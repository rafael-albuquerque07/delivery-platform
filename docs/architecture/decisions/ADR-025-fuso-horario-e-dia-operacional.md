# ADR-025 — Fuso horário do estabelecimento e o dia operacional

**Status:** Aceita — 24/08/2026
**Relacionada:** ADR-011 (autorização), ADR-013 (retenção), ADR-021 (catálogo de serviços)
**Detalhada em:** `docs/dominio/estabelecimento.md` §4 · `docs/dominio/liquidacao.md` §2 e §6 · `docs/dominio/catalogo.md` §3
**Fecha duas pendências:** fuso horário (`estabelecimento.md` §9) e jornada que cruza a meia-noite (`liquidacao.md` §9)
**Precisa existir antes do marco 1** — o `merchant-service` é o primeiro a gravar horário de funcionamento

## Contexto

Todo o horário de funcionamento presume um fuso que não está modelado. Loja
única em Recife funciona por acidente; a primeira loja em outro fuso quebra — e
quebra em três lugares ao mesmo tempo, todos em silêncio.

Só que o fuso é metade do problema. A outra metade estava registrada como
pendência separada, em outro documento, com outro prazo:

> **Jornada que cruza a meia-noite.** O turno da pizzaria termina às 2h. A
> jornada é do turno, não do dia civil — mas a agregação "total do dia" precisa
> de um critério, e ele não foi decidido.
> — `liquidacao.md` §9

**São a mesma pergunta.** "Que dia é hoje para esta loja" tem duas componentes:
qual é o relógio dela, e onde o dia dela começa. Responder só a primeira deixa o
fechamento de sábado partido entre sábado e domingo — e é justamente o número que
o comerciante confere no fim do turno.

### Onde a resposta é usada

Quatro lugares, e só quatro. Vale enumerá-los porque o resto do sistema **não**
precisa de fuso nenhum:

| Onde | Pergunta |
|---|---|
| `merchant` — horário de funcionamento | "a loja está aberta agora?" |
| `catalog` — reativação de `ESGOTADO_HOJE` | "este é o mesmo expediente de quando marquei?" |
| `settlement` — fechamento e `totalDoDiaEmJornadasPorMetodo` | "que dia esta jornada fecha?" |
| `order` — numeração sequencial por loja, se ela reiniciar | "que dia é este pedido?" |

Todo o restante do sistema registra **quando algo aconteceu**, e isso é instante,
não data civil. Confundir os dois é a origem de praticamente todo defeito de
fuso.

## Decisão

### 1. Instante e tempo civil são coisas diferentes, e o código diz qual é qual

```
Instante   quando algo aconteceu
           → Instant no domínio, timestamptz no banco, SEMPRE em UTC
           → occurredAt, momento, marcadoEm, abertura.momento,
             expiraEm, pausadoAte, ultimoRetornoEm, registradoEm

Tempo civil  a que dia ou hora do calendário da loja isto pertence
           → só existe convertendo um instante COM a zona do estabelecimento
           → horário de funcionamento, expediente, dia operacional
```

Três proibições que decorrem disso, e que valem em todo serviço:

- **Nunca `LocalDateTime` sem zona** num campo persistido. Ele parece resolver e
  guarda uma hora sem lugar — o mesmo número significa dois instantes
  diferentes em duas lojas.
- **Nunca `LocalDate.now()` ou `LocalDateTime.now()`.** Eles leem o fuso do
  servidor, que é do datacenter e não da pizzaria. Toda conversão recebe a zona
  explicitamente.
- **Nunca aritmética sobre hora local.** Somar 24 horas a uma hora local não é
  o mesmo que somar um dia; com horário de verão, difere. Usa-se a zona.

### 2. O fuso pertence ao estabelecimento, como identificador IANA

```
Estabelecimento
└── identificacao   id, nome, documento, telefone, endereco, fusoHorario
```

`fusoHorario` é um identificador IANA — `America/Sao_Paulo`, `America/Manaus` —
validado contra o conjunto de zonas brasileiras. Campo **visível no cadastro**,
já preenchido com `America/Sao_Paulo`, que o comerciante pode trocar num toque.

Fica em `identificacao`, ao lado do endereço, porque é um fato sobre **onde a
loja está** — não uma preferência de operação.

**Por que identificador IANA e não deslocamento fixo.** Hoje o Brasil não
observa horário de verão, e `-03:00` funcionaria. Mas `America/Recife` e
`America/Sao_Paulo` têm o mesmo deslocamento agora e regras históricas
diferentes: se o horário de verão voltar, São Paulo observa e Recife não. Uma
loja de Recife gravada como deslocamento fixo passaria a errar uma hora, sem
nenhum sinal, no dia da virada. O identificador IANA custa o mesmo e é correto
por construção.

**Por que não deduzir do endereço ou do CEP.** Exigiria base de CEP para zona,
erraria em divisas, e criaria uma dependência nova para resolver algo que o
comerciante responde num toque.

### 3. O dia operacional começa às 04:00, e isso é constante

```
HORA_DE_CORTE = 04:00        constante de domínio, igual para todas as lojas

diaOperacional(instante, fuso):
    local = instante convertido para fuso
    se local.hora < 04:00  →  local.data − 1 dia
    senão                  →  local.data
```

Uma venda à 01:30 de domingo pertence ao **dia operacional de sábado**, que é
como o comerciante fala e como ele confere.

**Por que constante e não campo configurável.** 04:00 funciona para pizzaria
18h–2h, padaria 6h–20h, açaí, marmitaria e minimercado — qualquer operação cujo
movimento não atravessa as quatro da manhã. Um campo a mais é uma decisão a mais
para o comerciante entender, uma tela a mais, e um valor que quando alguém mexer
muda o significado de "hoje" para trás.

Se um dia aparecer operação 24 horas reclamando, virar campo é barato — e é
barato **porque** o valor já fica congelado em cada `Fechamento` (§4 abaixo).
Mudar a constante não reescreve histórico.

### 4. O dia operacional da jornada é congelado na abertura

`Jornada` ganha `diaOperacional`, calculado no momento da abertura e
**congelado**, exatamente como o `vinculoSnapshot` já é.

É do **início** do turno, não do fim. Uma jornada aberta às 18h de sábado e
fechada às 2h30 de domingo é a jornada de sábado — que é como as duas pessoas
que a conferem a chamam. O caso raro que torna a escolha necessária é a padaria
que abre às 3h: pela abertura o turno é da sexta, pelo fechamento seria do
sábado, e sem regra as duas leituras convivem.

**E todo `Lancamento` herda o `diaOperacional` da sua jornada**, em vez de
recalculá-lo pelo próprio momento. Sem isso, uma liquidação registrada às 05:00
dentro de um turno aberto às 03:00 cairia num dia e o fechamento em outro, e a
invariante J9 quebraria por um caso que ninguém reproduziria em teste.

### 5. O expediente de referência é o dia operacional

`catalogo.md` §3 usa `expedienteDeReferencia` para tornar idempotente a
reativação de `ESGOTADO_HOJE`. Ele passa a ser, literalmente, o
`diaOperacional` da loja.

Isso resolve de graça uma pergunta que aquele documento não respondia: **o que
acontece quando a loja abre duas vezes no mesmo dia.** A padaria opera 6h–14h e
18h–22h; são duas transições fechado → aberto, e o catálogo recebe dois eventos
de abertura.

```
06:00  abre           expediente = D
11:00  acabou o pão   expedienteDeReferencia = D
14:00  fecha
18:00  abre de novo   expediente = D  →  D == D  →  NÃO reativa   ✓
```

O pão que acabou no almoço continua acabado no jantar, que é o comportamento
certo — o estoque físico também não se repôs. E no dia seguinte, `D+1 ≠ D`, e
reativa.

A regra anterior — "só a transição fechado → aberto **por horário** conta" —
continua valendo e agora tem um critério verificável por trás.

### 6. Horário de verão, se voltar

A regra de faixa que cruza a meia-noite (`estabelecimento.md` §4) não muda.
O que muda é como ela é avaliada: sempre por conversão com a zona, nunca por
aritmética sobre a hora local.

| Transição | O que acontece | Efeito aqui |
|---|---|---|
| Adianta-se o relógio | uma hora local não existe | uma faixa pode encurtar uma hora naquele dia; nenhuma quebra |
| Atrasa-se o relógio | uma hora local acontece duas vezes | um turno ganha uma hora; ambas com hora < 04:00, logo **mesmo dia operacional** |

A hora de corte às 04:00 é segura nos dois casos porque as viradas brasileiras
sempre ocorreram à meia-noite.

## Consequências

**Positivas**

- Duas pendências fecham com uma decisão, e uma terceira — abertura dupla no
  mesmo dia — se resolve sem ninguém ter perguntado.
- "Que dia é hoje para esta loja" passa a ter **uma** resposta, computada de um
  jeito só, usada por quatro serviços que não se falam.
- A separação instante × tempo civil torna o defeito de fuso **impossível de
  cometer sem contrariar uma regra escrita**, em vez de depender de disciplina.
- O fechamento de sábado deixa de vazar para domingo, que é o número que o
  comerciante confere no fim do turno.
- A porta para operar fora de um fuso fica aberta sem custo, e o produto
  continua declarando múltiplas regiões como não-objetivo.

**Negativas**

- **Mais um campo no cadastro**, contra H1.1, que pede cadastro em uma tela.
  Aceito: vem preenchido, e a alternativa — deduzir — é pior. Mas é um campo que
  o comerciante não entende e vai ignorar, e um erro nele é silencioso.
- **04:00 é arbitrário.** Não há nada de especial nesse número além de ser
  depois do fim de qualquer operação do público-alvo e antes do começo da
  próxima. Uma padaria que abra às 3h30 tem o dia operacional deslocado, e a
  correção é discutir a constante — não contornar caso a caso.
- **Congelar o dia na jornada duplica informação** que poderia ser derivada do
  momento de abertura. É deliberado, pelo mesmo motivo do `vinculoSnapshot`: um
  documento assinado não muda porque uma constante mudou.
- **`Lancamento` herdar o dia da jornada** significa que dois lançamentos com
  momentos em dias operacionais diferentes podem carregar o mesmo dia. Está
  correto — o dia é do turno —, mas é contraintuitivo lendo a tabela, e precisa
  da linha que explica.
- **O conjunto de zonas aceitas é uma validação a manter.** Quando o produto
  sair do Brasil, é uma linha a remover — mas até lá é uma lista que alguém
  precisa lembrar que existe.

## Alternativas consideradas

- **Não modelar; assumir um fuso único no código.** É o que existe hoje, por
  omissão. Rejeitada: a primeira loja fora do fuso quebra em três lugares ao
  mesmo tempo — horário de funcionamento, reativação do catálogo e fechamento —
  e nenhum deles dá erro. Todos apenas mostram o número errado.
- **Deslocamento fixo (`-03:00`) em vez de zona IANA.** Funciona hoje, custa o
  mesmo. Rejeitada porque o modo de falha é o pior possível: correto até o dia
  em que uma regra de horário de verão mudar, e errado em silêncio a partir
  dali.
- **Derivar a zona do endereço ou da faixa de CEP.** Rejeitada: base de CEP para
  zona a manter, erro em divisas, e uma dependência nova para uma pergunta que
  se responde com um toque.
- **Dia civil, sem hora de corte.** A opção mais simples de explicar, e a que a
  contabilidade usa. Rejeitada porque parte o sábado da pizzaria em dois: o que
  ela vendeu à 1h da manhã aparece no domingo, e o fechamento que o comerciante
  confere às 2h30 não bate com o dia que ele acabou de trabalhar. É exatamente a
  desconfiança que o produto existe para acabar.
- **Hora de corte configurável por loja, com padrão 04:00.** Cobriria desde já a
  loja 24 horas e a padaria que abre às 3h. Rejeitada por ora: é um campo que
  quase ninguém mexe e que, quando alguém mexe, muda o significado de "hoje"
  retroativamente. Vira campo no dia em que houver um comerciante real pedindo,
  e o congelamento na jornada torna essa migração barata.
- **Dia operacional = expediente, do abrir ao fechar.** Mais preciso em teoria.
  Rejeitada: a loja que abre duas vezes no dia teria dois expedientes e nenhum
  "dia"; a que não abriu não teria dia nenhum; e um pedido de balcão fora do
  horário não teria onde cair.

## Pendência que esta decisão não fecha

**Se a numeração sequencial de pedido por loja reinicia, e com que
periodicidade** (`pedido.md` §9). Esta ADR remove a ambiguidade de *qual dia* —
é o `diaOperacional` —, mas não decide se reinicia diariamente, anualmente ou
nunca. Continua sendo decisão de produto, com prazo no marco 3.
