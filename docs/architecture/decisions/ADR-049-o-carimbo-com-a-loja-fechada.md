# ADR-049 — O carimbo com a loja fechada

- **Estado:** aceita
- **Data:** 30/09/2026
- **Emenda:** ADR-046 (a porta que "já existe" e não existia)
- **Relacionadas:** ADR-025 (dia operacional), ADR-045 (a credencial entre serviços), ADR-012 (o gateway não roteia `/internal/`)
- **Invariantes:** C11 do `catalogo.md`, 9 do `CLAUDE.md`

## Contexto

A Marli abre às 18h. Às 10h, preparando a massa, ela vê que não tem calabresa e
marca "acabou hoje".

`ESGOTADO_HOJE` não existe sem carimbo: o construtor da `Disponibilidade` do
`catalog` recusa `marcadoEm` sem `expedienteDeReferencia`, com a mensagem
*"ESGOTADO_HOJE sem expedienteDeReferencia nunca reativa"*. E o catálogo **não
pode calcular** o dia operacional — a ADR-046 §6 diz que o cálculo
*"continua sendo o único lugar que a calcula"*, no `merchant`, e a classe do
catálogo não tem `FusoHorario` justamente para que não haja como derivá-lo por
engano.

Então o `catalog` pergunta. E a pergunta não tinha resposta, porque às 10h a
loja está fechada e `Disponibilidade.inicioDaFaixaEm(10h)` devolve **vazio**.

**Nenhum documento decidia este caso.** A ADR-046 emendada escreveu a regra para
a loja aberta — *"o expediente é o dia operacional do início da faixa que contém
o instante"* — e a varredura, que é o único chamador existente, nunca pergunta
com a loja fechada: ela só publica quando há faixa.

### E uma correção, antes da decisão

A emenda de 26/09 desta mesma ADR-046 diz que quem precisar da resposta *"pergunta
pela `OperacaoDoEstabelecimentoPort`, que já existe e já calcula na leitura"*.

**Ela não existe.** Não há `OperacaoDoEstabelecimentoPort` em código, nem
`expedienteCorrente`, nem `diaOperacionalCorrente`. O nome aparecia, antes desta
rodada, em dez documentos e em dois javadocs — da `Disponibilidade` e do
`Estabelecimento` do `merchant` —, e em nenhuma declaração. O reconhecimento de
29/09 mediu a ausência; a contagem foi refeita na G-C1.

A frase que eu venho repetindo desde a rodada F — *"a rota tem de responder pelo
`inicioDaFaixaEm`, e não pelo instante"* — também **não está escrita em documento
nenhum**. É dedução correta da emenda da ADR-046, e nunca foi registrada. Esta
ADR é o registro.

## Decisão

### 1. Fechada, o carimbo é o da **próxima abertura**

```
inicio = Disponibilidade.inicioDaFaixaEm(agora, fuso)
         ├─ presente → expediente = diaOperacional(inicio, fuso)      // em curso
         └─ vazio    → proximaAberturaApos(agora, fuso)
                        ├─ presente → expediente = diaOperacional(proxima, fuso)
                        └─ vazio    → não há expediente → 409
```

"Acabou" significa **não ofereça até a próxima virada de expediente depois
desta**. Com o carimbo da próxima abertura, a abertura das 18h traz o mesmo dia
que está no carimbo, `carimbo.isBefore(abertura)` é falso, e o item continua
esgotado a noite inteira — que é o que ela quis dizer.

### 2. Por que não o dia operacional do instante

É a alternativa barata: o cálculo já existe, e ela acerta às 10h.

Ela erra **na madrugada**, que é onde este projeto já errou duas vezes. A
pizzaria fecha às 02:00; às 03:00 alguém marca "acabou a calabresa" enquanto
limpa o balcão. `diaOperacional(03:00)` é o **dia anterior**, porque 03:00 é
antes da hora de corte. A abertura das 18h do mesmo dia civil traz o dia de
hoje, hoje é depois de ontem, e a calabresa **volta ao cardápio** na abertura
seguinte à marcação.

Com a próxima abertura: às 03:00 a próxima é hoje às 18:00, o carimbo é hoje, e
o item fica esgotado esta noite.

> É o defeito do job à meia-noite que o `catalogo.md` §3 rejeita, reaparecendo
> pela hora de corte — exatamente como a emenda de 26/09 da ADR-046 o viu
> reaparecer do lado do produtor.

### 3. Por que não o da última abertura

Porque reativa na abertura seguinte, que é o pior resultado possível: o item
some do cardápio por algumas horas e volta **na hora do pico**, oferecendo o que
não existe. É o caso que o PRD nomeia.

### 4. Por que não recusar a marcação com a loja fechada

Marcar de manhã o que faltou para a noite é a operação normal de padaria e
pizzaria. Um 409 aí é o sistema dizendo "volte às 18h para me contar o que você
já sabe às 10h" — e o PRD é explícito sobre o custo disso: *"Se o comerciante
precisar lembrar de reativar doze itens toda manhã, ele para de usar na segunda
semana."*

### 5. Loja que não abre por horário: 409, e não uma data

Horário vazio é válido — o `estabelecimento.md` §4 diz que significa "nunca abre
por horário" — e a ADR-046 já registrava a consequência: *"Loja sem horário
nunca abre expediente, e portanto nunca reativa nada"*.

Qualquer data devolvida aqui seria comparada, amanhã, com a de um evento de
abertura que nunca chega, e o produto ficaria esgotado para sempre sem erro em
lugar nenhum. **409 é o código certo pelo critério que o `catalogo.md` §5 já
usa**: estado do mundo, não erro do chamador.

**O que a G-C2 herda, e que esta ADR não decide:** o que a marcação faz quando
recebe esse 409. A resposta provável é que uma loja sem horário só aceite
`ESGOTADO_INDETERMINADO`, que não depende de expediente — mas isso se decide com
a tela na frente.

### 6. Onde cada peça mora

| Peça | Onde | Por quê |
|---|---|---|
| `proximaAberturaApos(Instant, FusoHorario)` | `Disponibilidade` do `merchant` | É uma pergunta sobre o horário de funcionamento, e é a irmã do `inicioDaFaixaEm`. Devolve **instante**, e não sabe de hora de corte |
| `expedienteParaCarimbo(Instant)` | `Estabelecimento` | É quem tem o `fusoHorario`. A composição das duas perguntas mora com o dado de que ela depende |
| a rota | `GET /internal/merchants/{id}/expediente-corrente` | `/internal/` porque é serviço perguntando a serviço, com o token de quem pediu (ADR-045) |

O caso de uso **não** compõe a conta: ele confere o vínculo, carrega a loja e
pergunta. Um caso de uso que somasse faixa, fuso e corte seria o segundo lugar a
errar às 04:00, e a F.1 existiu porque havia um primeiro.

### 7. Vínculo ativo, e nenhuma permissão específica

A invariante 9 manda confrontar o identificador da URL com o usuário
autenticado. O confronto aqui é **ter vínculo ativo com a loja**.

Permissão específica seria duplicação: quem chama já confere a permissão do ato
que vai praticar — `ALTERAR_PRODUTO`, na G-C2 —, e uma segunda lista aqui teria
de concordar com aquela para sempre.

**Sem vínculo é 403, inclusive quando a loja não existe.** Um 404 para loja
inexistente e 403 para loja sem vínculo diria, a qualquer portador de token,
quais identificadores existem neste sistema.

## Consequências

**Positivas**

- O `catalogo.md` §3 ganha a peça que faltava do lado de quem **marca**; a F deu
  a do lado de quem **abre**.
- `proximaAberturaApos` é o que o painel vai querer para dizer "abre às 18h", e
  nasce com teste antes de ter tela.
- A frase que eu repetia sem documento passa a ter documento.

**Negativas**

- **Uma busca que olha para a frente.** `proximaAberturaApos` varre até oito dias
  de horário — o bastante para a semana fechar. É memória e laço, não banco, e o
  horário tem no máximo sete chaves. **Gatilho escrito:** se algum dia o horário
  deixar de ser semanal (feriado, exceção por data), esta busca muda junto.
- **Duas faixas encostadas continuam sendo dois turnos.** A ADR-046 já assumiu
  isso; aqui a consequência é que marcar entre elas carimba a segunda. Quem opera
  continuamente cadastra uma faixa só.
- **A conta se prova no teste unitário, e só um caso dela no de integração.** A
  rota lê o `Clock` do serviço (`RelogioConfig`, o mesmo do `GerenciarEquipeService`
  e do relay), e o IT o fixa em terça às 10h para refazer o caso da Marli de
  ponta a ponta. A madrugada e a semana inteira ficam no `CarimboDoExpedienteTest`,
  com instantes literais — um IT por instante seria um contexto Spring por caso.
- **Mais uma dependência do `catalog` no `merchant`**, e é a falha fechada da
  ADR-011 se propagando pela terceira vez. Consequência já assumida lá.

## Alternativas consideradas

- **`diaOperacional` do instante.** Rejeitada na §2: erra entre a meia-noite e as
  04:00, que é exatamente a janela que este projeto já consertou duas vezes.
- **O dia operacional da última abertura.** Rejeitada na §3: reativa na abertura
  seguinte, no pico.
- **Carimbo nulo significando "reativa na próxima abertura, seja qual for".**
  Elegante, e errada pelo mesmo motivo da anterior: a abertura das 18h reativaria
  o que foi marcado às 10h. Exigiria ainda inverter a regra que a
  `Disponibilidade` do catálogo cobra hoje.
- **Recusar a marcação com a loja fechada.** Rejeitada na §4.
- **O catálogo calcular o dia operacional com um fuso replicado.** Rejeitada pela
  ADR-046 §6, e a estrutura já a impede: não há `FusoHorario` na classe do
  catálogo.

## Emenda de 06/10/2026 — o que a marcação faz com o 409

A §5 deixou em aberto o que o `catalog` faz quando a loja não abre por horário.
A G-C2 decidiu, e a decisão tem dois ramos, porque o domínio já os separa: o
par `marcadoEm` + `expedienteDeReferencia` nasce inteiro ou não nasce, e
**apenas `ESGOTADO_HOJE` o exige**.

- **`ESGOTADO_HOJE` é recusado**, com 409 e um detalhe que diz o caminho: "Use
  ESGOTADO_INDETERMINADO." Sem expediente ele nunca reativaria, e o produto
  sumiria do cardápio para sempre sem erro em lugar nenhum — que é o mesmo
  motivo pelo qual esta ADR recusou devolver uma data inventada;
- **os outros três estados são aceitos sem carimbo.** É legal pelo construtor
  da `Disponibilidade`, e é honesto: o carimbo existe para a comparação da
  reativação, e numa loja que nunca abre expediente não há comparação a fazer.
  Gravar só o instante seria inventar a metade que o domínio proíbe.

**A consequência que fica escrita:** numa loja sem horário, `ACABANDO` e
`DISPONIVEL` não registram *quando* alguém os disse. Se um dia isso for preciso
— auditoria, "marcado há quanto tempo" na tela —, o lugar de mudar é o par de
campos da `Disponibilidade`, e não esta regra.
