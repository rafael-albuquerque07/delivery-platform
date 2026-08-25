# ADR-033 — O estado da jornada é perguntado, com cache curto e falha fechada

**Status:** Aceita — 25/08/2026
**Corrige:** duas guardas inexequíveis — `pedido.md` T16 e `entrega.md` §5
**Relacionada:** ADR-011 (autorização contextual — este é o mesmo padrão), ADR-022, ADR-032 (a irmã assimétrica)
**Emenda:** `docs/dominio/liquidacao.md` §8, que dizia "Publica: nada"
**Precisa existir antes do marco 5** — é quando o despacho ganha código

## Contexto

A varredura que a matriz de eventos tornou possível achou mais duas guardas da
classe da ADR-032. Elas apontam na direção oposta — não é o `settlement`
perguntando sobre `Pedido`, são o `order` e o `delivery` perguntando sobre
`Jornada` — e uma delas é mais grave que a original.

### As duas guardas

```
pedido.md  T16   PRONTO → SAIU_PARA_ENTREGA
                 guarda: modalidade = ENTREGA
                       · entrega atribuída
                       · entregador com JORNADA ABERTA        ← daqui

entrega.md §5    rodízio
                 candidatos = vínculo ATIVO
                            ∧ JORNADA ABERTA nesta loja       ← e daqui
                            ∧ situacao = NO_ESTABELECIMENTO
                            ∧ entregasEmMaos < max
                 desempate por ORDEM DE ABERTURA DA JORNADA   ← e daqui
```

`Jornada` é agregado do `settlement-service`. O `liquidacao.md` §8, escrito na
rodada da ADR-032, diz **"Publica: nada"** — o serviço é consumidor terminal.
Nenhum evento anuncia abertura ou fechamento de jornada, e não há porta síncrona
documentada.

Nenhum dos dois serviços tem como avaliar a própria guarda.

### Por que esta é pior que a da ADR-032

O `liquidacao.md` §3 diz, sobre a guarda de T16:

> É assim que a invariante 1 do `CLAUDE.md` — nenhuma entrega sem liquidação
> registrada — se torna **estruturalmente garantida** em vez de esperada.

A invariante 1 é a que vende o produto. A cadeia inteira é:

```
invariante 1  ──apoia-se em──▶  guarda de T16  ──precisa de──▶  estado da Jornada
                                                                       ✗
```

Sem o terceiro elo, "estruturalmente garantida" é afirmação, não fato. E o modo
de falha é concreto: um entregador sem jornada aberta sai com pedido, entrega,
e o dinheiro que ele recebeu não tem jornada onde ser lançado.

### O terceiro requisito, que quase passou

O rodízio não precisa de um booleano. O desempate usa **ordem de abertura da
jornada**, e o critério primário usa contagem de entregas na jornada. O
`delivery` conhece as entregas que ele mesmo atribuiu; o que ele não tem é
`abertaEm`.

Quem responder isto devolve **lista com timestamp**, não sim ou não.

## Decisão

### 1. Consulta síncrona ao `settlement`, com cache curto em processo

```
order/delivery precisa saber sobre jornada
        ↓
cache em processo    ── acertou ──▶  responde
        │
        └── errou ──▶  pergunta ao settlement ──▶ guarda e responde
```

**Isto é literalmente o padrão da ADR-011.** A autorização contextual já resolve
permissão por requisição contra o `merchant-service`, com Caffeine em processo,
TTL curto e invalidação por `VinculoAlteradoV1`. Jornada tem a mesma forma:
consultada muitas vezes, muda poucas vezes, e errar para o lado permissivo é
grave.

Reusar o padrão não é economia de decisão — é evitar dois mecanismos diferentes
para o mesmo problema no mesmo repositório.

### 2. O `settlement` passa a publicar dois eventos

```
JornadaAbertaV1     { jornadaId, entregadorId, estabelecimentoId, abertaEm }
JornadaFechadaV1    { jornadaId, entregadorId, estabelecimentoId, fechadaEm }
```

Consumidores: `order` e `delivery`, **para invalidar cache** — não para manter
projeção. A distinção é o núcleo da ADR-032 e vale igual aqui: o evento é o
caminho rápido, o TTL é a rede de segurança, e a verdade continua morando no
`settlement`.

Isto emenda o `liquidacao.md` §8. Ele dizia "Publica: nada", e dizia certo com a
informação que tínhamos — a necessidade só apareceu quando a matriz permitiu
cruzar guarda com origem do dado.

`EM_CONFERENCIA` **não** publica: para efeito de despacho, a jornada em
conferência já não aceita pedido novo, e é `JornadaFechadaV1` que fecha a
janela. Duas transições, dois eventos, não quatro.

### 3. Falha fechada, e a direção não é simétrica

**Consulta que não responde recusa a operação.** Sem jornada confirmada, não há
despacho e não há sugestão de rodízio.

A assimetria dos dois erros é o que decide:

| Erro | Consequência | Como se descobre |
|---|---|---|
| Recusar quem tem jornada aberta | despacho travado | **na hora** — o operador reclama |
| Permitir quem não tem | entrega feita, dinheiro sem jornada onde lançar | **no fechamento**, ou nunca |

O primeiro é barulhento e reversível em segundos. O segundo é a invariante 1
quebrada em silêncio. `Fail-closed`, igual à autorização (ADR-011).

### 4. A armadilha da ADR-011 vale aqui, inteira

Cache em processo invalidado por evento exige **fila exclusiva por instância,
ligada a um exchange fanout**. Com fila compartilhada, só uma instância recebe a
invalidação e as outras seguem com dado velho até o TTL — silenciosamente.

É a mesma linha que já está na tabela de armadilhas do `CLAUDE.md` para
`VinculoAlteradoV1`, e passa a valer para estes dois.

### 5. A forma da porta

`JornadaPort`, no `settlement`, com duas operações:

```
jornadaAbertaDe(entregadorId, estabelecimentoId)  →  { jornadaId, abertaEm } | vazio
jornadasAbertasEm(estabelecimentoId)              →  [ { entregadorId, jornadaId, abertaEm } ]
```

A segunda existe porque o rodízio precisa da lista com `abertaEm` para o
desempate — pedir uma por entregador seria N chamadas para montar uma sugestão.

## Por que não é a mesma resposta da ADR-032

As duas decisões parecem contraditórias e não são. O que muda é a frequência:

| | ADR-032 | Esta |
|---|---|---|
| Guarda | iniciar o acerto | despachar · sugerir rodízio |
| Frequência | **uma vez por jornada** | muitas vezes por noite |
| Solução | consulta pura | consulta **+ cache + invalidação** |
| Falha | recusa | recusa |

Cache para uma leitura por turno seria construir mecanismo para nada. Consulta
sem cache no caminho do despacho seria pagar rede a cada toque de botão no pico.
O critério é o mesmo — **a verdade mora num serviço só** —, e o que varia é
quanto vale otimizar a leitura.

## Consequências

**Positivas**

- A invariante 1 volta a ser estruturalmente garantida de verdade. Era a
  afirmação mais forte do repositório e estava sem lastro.
- O rodízio passa a ser implementável como está escrito, desempate incluído.
- Nenhum mecanismo novo: é a ADR-011 aplicada a outro dado.
- `Jornada` deixa de ser invisível sem virar dado replicado. Ninguém guarda
  cópia; guarda-se resposta com prazo.

**Negativas**

- **Despacho passa a depender do `settlement` de pé.** No pico. É o custo mais
  caro desta decisão, e a mitigação é o cache: uma indisponibilidade curta é
  absorvida enquanto o TTL durar, e só morde quando coincide com entrada de
  entregador novo.
- **O `settlement` deixa de ser consumidor terminal**, o que era uma
  propriedade agradável do desenho e durou uma rodada.
- **Janela de TTL após o fechamento da jornada.** Se `JornadaFechadaV1` se
  perder, um despacho pode ser autorizado por até um TTL depois do fechamento. É
  a mesma janela que a autorização aceita para permissão revogada, e a razão de
  o TTL ser curto.
- **Dois eventos novos** para a matriz e para o esquema.

## Alternativas consideradas

- **Projeção de jornadas abertas dentro do `order` e do `delivery`.** Rejeitada
  pelo argumento da ADR-032 §2: evento perdido deixa a projeção errada
  indefinidamente, sem TTL que a conserte. Cache com prazo erra por segundos;
  projeção erra até alguém notar.
- **Consulta pura, sem cache**, como na ADR-032. Coerente e mais simples.
  Rejeitada pela frequência: uma chamada de rede por despacho e por sugestão de
  rodízio, no horário de pico, para um dado que muda duas vezes por turno.
- **`Jornada` migrar para o `merchant-service`**, junto do vínculo, ficando ao
  lado de quem já responde autorização. Tentadora — resolveria com a porta que
  já existe. Rejeitada: jornada é a raiz da apuração (`liquidacao.md` §2), e
  movê-la levaria lançamentos, fechamento e ajustes junto. Seria mover o
  `settlement` inteiro para dentro do `merchant`.
- **Falhar aberto quando o `settlement` não responde**, para não travar a
  operação no pico. Rejeitada no §3: é exatamente a troca que transforma a
  invariante 1 em intenção.
- **T16 confiar no `delivery`**, já que só há entrega atribuída se o rodízio
  aprovou. Rejeitada por dois motivos: a atribuição direta não passa pelo
  rodízio (`entrega.md` §5), e encadear guarda em guarda faz a invariante 1
  depender de dois serviços em vez de um.

## Pendência que esta decisão não fecha

**O valor do TTL.** A ADR-011 também o deixou como "curto" sem número. Os dois
deveriam usar o mesmo, e o número sai de medição, não de escolha — fica para
quando houver tráfego. Registrado nos dois lugares para não virar dois valores
diferentes por acidente.
