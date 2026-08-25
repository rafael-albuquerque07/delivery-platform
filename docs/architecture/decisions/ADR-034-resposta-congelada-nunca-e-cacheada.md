# ADR-034 — Resposta que vira snapshot nunca é cacheada

**Status:** Aceita — 25/08/2026
**Corrige:** duas guardas de `pedido.md` sem origem documentada, e uma porta sem nome
**Relacionada:** ADR-011 (autorização), ADR-019 e ADR-020 (`DeliveryQuotePort`), ADR-032, ADR-033
**Corrige também:** a pendência final da ADR-033, que descrevia um problema inexistente
**Precisa existir antes do marco 3** — é quando o `order-service` ganha código

## Contexto

A ADR-032 e a ADR-033 fecharam três guardas que dependiam de estado de outro
serviço. A varredura seguinte, contra `estabelecimento.md` inteiro, achou mais
três casos — e eles **não são a mesma coisa**, o que só ficou visível ao olhar
os três juntos.

| Guarda | O que existe hoje |
|---|---|
| T01 · área atendida | A porta existe: `DeliveryQuotePort`, ADR-020. O `pedido.md` não a referencia |
| T02 · loja aberta | Nada. `ExpedienteAlteradoV1` vai para `catalog` e `conversation`; ao `order`, não |
| I8 · subestado válido para o tipo de operação | `estabelecimento.md` §4 afirma que o `order` consulta, e não diz por onde |

E um quarto achado, estrutural: **`estabelecimento.md` §3 se chama "Como os
outros serviços perguntam" e documenta uma pergunta só** — a de autorização. É o
lugar certo com um terço do conteúdo.

### O erro que as duas ADRs anteriores tornaram provável

ADR-011, ADR-032 e ADR-033 formam um padrão: *consulte o dono do dado, guarde a
resposta com prazo curto, invalide por evento, negue quando não souber.* Ele está
certo nos três casos e é o que qualquer um aplicaria ao quarto por analogia.

Aplicá-lo à `DeliveryQuotePort` **seria um defeito de dinheiro**.

```
T01  →  DeliveryQuotePort.quote(...)  →  taxa
                                          ↓
                            CONGELADA no pedido como taxaSnapshot
                                          ↓
                                é o que o cliente paga
```

Uma taxa de sessenta segundos atrás, copiada para dentro de um pedido, é um preço
errado que o cliente já confirmou — e o congelamento (`pedido.md` §5) existe
justamente para que o valor seja reconstruível meses depois. Cache aqui não
produz dado velho por um minuto; produz **dado velho para sempre**, gravado.

## Decisão

### 1. O critério não é frequência. É se a resposta vira snapshot.

> **Resposta que é congelada no agregado nunca é cacheada.**

Não importa quantas vezes por minuto a pergunta é feita. O que importa é que o
erro de uma leitura desatualizada **sobrevive à leitura** — vira campo gravado,
vira valor cobrado, vira histórico.

Onde a resposta é usada e descartada, cache é otimização. Onde ela é copiada,
cache é corrupção com atraso.

### 2. A árvore de decisão, para a próxima guarda

```
A guarda depende de estado de outro serviço?
│
├── a resposta é CONGELADA no agregado?
│      → consulta síncrona, SEM CACHE                        ADR-034
│
├── é avaliada uma vez por ciclo de vida?
│      → consulta síncrona pura                              ADR-032
│
└── é avaliada muitas vezes?
       → consulta + cache curto + invalidação por evento     ADR-011, ADR-033

Em todos os três: falha fechada. Sem resposta, a operação é recusada.
```

Os três ramos concordam no que importa — **a verdade mora num serviço só** — e
divergem apenas em quanto vale otimizar a leitura.

### 3. As três guardas, resolvidas

**T01 · área atendida** — `DeliveryQuotePort` (ADR-019, ADR-020), **sem cache**,
pelo §1. A porta já existe e já é a que produz `taxaSnapshot`: a guarda "área
atendida" é respondida pela mesma chamada que congela a taxa. Não há mecanismo a
criar, há referência a escrever.

**T02 · loja aberta** — porta nova, `OperacaoDoEstabelecimentoPort`, **com
cache**, invalidada por `ExpedienteAlteradoV1`, que passa a ter o `order` entre
os consumidores. A resposta não é congelada: ela decide se a transição acontece
e é descartada em seguida.

O aceite manual fora do horário (`estabelecimento.md` §4) continua valendo e não
depende da porta — é ato explícito de quem tem `ALTERAR_STATUS`, e a guarda é
"loja aberta **ou** aceite manual".

**I8 · subestado × tipo de operação** — a mesma
`OperacaoDoEstabelecimentoPort`, mesma cache. O `tipoDeOperacao` muda quando o
comerciante reconfigura a loja, o que é raro, e não é congelado no pedido: o
`estabelecimento.md` §4 já diz que alterá-lo não afeta pedidos em andamento.

### 4. `estabelecimento.md` §3 passa a listar todas as portas

A seção se chama "Como os outros serviços perguntam" e documenta uma pergunta.
Passa a documentar as três, com a coluna que importa:

| Porta | Cache | Por quê |
|---|---|---|
| `AutorizacaoComercialPort` | 60 s positivo · 10 s negativo | ADR-011 |
| `OperacaoDoEstabelecimentoPort` | idem | Resposta usada e descartada |
| `DeliveryQuotePort` | **nenhum** | A resposta é congelada — ADR-034 §1 |

Uma porta sem essa coluna preenchida não está documentada.

### 5. Os TTLs são os mesmos, e já tinham número

A ADR-033 fechou com uma pendência dizendo que o TTL da ADR-011 não tinha valor
definido. **Está errado**: `estabelecimento.md` §3 registra 60 s para resposta
positiva e 10 s para negativa, atribuindo à ADR-011.

A consulta de jornada da ADR-033 usa **os mesmos números**, e a assimetria
funciona na direção certa pelo mesmo motivo: negar por engano custa dez segundos
de espera; permitir por engano custa uma entrega sem jornada onde lançar.

A pendência da ADR-033 fica **cancelada**, não resolvida — ela descrevia um
problema que não existia.

## Consequências

**Positivas**

- As três guardas passam a ser exequíveis, e a classe inteira que a ADR-032 abriu
  fecha aqui, se a varredura confirmar.
- O critério de cache deixa de ser analogia. Havia três precedentes dizendo
  "cacheie" e nenhum dizendo onde parar.
- `estabelecimento.md` §3 vira o catálogo real do que o `merchant` responde, com
  a política de cache ao lado de cada porta.
- Nenhuma porta nova além de uma, e nenhum evento novo — só um consumidor a mais
  no `ExpedienteAlteradoV1`.

**Negativas**

- **T01 paga uma chamada de rede sem cache, no caminho do pedido.** É o custo
  direto da decisão. Mitigação é a mesma da ADR-023 para a cobrança Pix — timeout
  e disjuntor —, mas aqui **não há caminho alternativo**: sem taxa não há total, e
  sem total não há pedido.
- **O `order` passa a depender do `merchant` em mais um ponto.** O `merchant` já
  era ponto único de falha por causa da autorização (`estabelecimento.md` §3 diz
  isso com todas as letras); esta decisão não muda a natureza do risco, aumenta a
  superfície.
- **A regra do §1 exige saber o que é congelado.** Quem não conhecer o §5 do
  `pedido.md` vai aplicar o padrão errado por analogia — que é exatamente o que
  esta ADR existe para impedir, e por isso a árvore do §2 vai para o `CLAUDE.md`.

## Alternativas consideradas

- **Cachear a cotação com TTL bem curto**, dois ou três segundos. Reduz o erro
  sem eliminá-lo. Rejeitada: o defeito não é proporcional ao tempo. Uma taxa
  errada gravada é errada para sempre, e "quase sempre certo" num campo de
  dinheiro é a pior categoria de defeito — raro, silencioso e caro.
- **Congelar a taxa em T02 em vez de T01.** Adiaria a chamada para depois do
  aceite. Rejeitada: o cliente confirma o total em T01 (`pedido.md` §5, H4.2), e
  congelar depois significa confirmar um número que ainda pode mudar.
- **O `order` manter projeção das áreas por evento.** `AreasDeEntregaAlteradasV1`
  já existe e vai para `conversation`. Rejeitada pelo argumento da ADR-032:
  projeção com evento perdido erra até alguém notar, e aqui o erro vira dinheiro
  gravado.
- **Uma porta só para tudo do `merchant`.** Simplifica a contagem e destrói a
  distinção do §1 — políticas de cache diferentes na mesma porta viram uma
  decisão por método, que ninguém lê.
- **Deixar `estabelecimento.md` §3 como está**, documentando só autorização.
  Rejeitada: foi essa lacuna que fez três guardas parecerem sem mecanismo quando
  uma delas tinha.

## Pendência que esta decisão não fecha

**Se o `merchant-service` sendo ponto único de falha continua aceitável** quando
o `order` depender dele para autorizar, cotar, saber se a loja está aberta e
validar subestado. Cada decisão isolada é defensável; a soma nunca foi avaliada
como soma. Não é urgente e não bloqueia marco nenhum — mas é a pergunta que este
documento não faz e alguém vai fazer.
