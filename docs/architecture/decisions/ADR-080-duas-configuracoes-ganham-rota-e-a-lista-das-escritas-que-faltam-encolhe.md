# ADR-080 — Duas configurações ganham rota, e a lista das escritas que faltam encolhe

- **Status:** aceita
- **Data:** 10/11/2026
- **Rodada:** E-9
- **Relacionada:** ADR-028 (o pedido mínimo), ADR-024 (o desconto de retirada), ADR-073 (o
  horário ganha rota — o molde), ADR-011, emenda de 17/10/2026 (a `CONFIGURAR_LOJA`), ADR-059,
  emenda de 17/10/2026 (a lista do que a fixture grava e a API não escreve), ADR-053 (o
  contrato declara as recusas) e o `docs/dominio/estabelecimento.md` §4 e §6

## Contexto

**A H1.2 pede as duas coisas com estas palavras:** *"Desconto para quem retira no balcão,
quando o comerciante quiser oferecê-lo"* e *"Pedido mínimo por modalidade, sobre o valor dos
itens — sem a taxa de entrega —, com zero valendo como 'sem mínimo'"*.

**O modelo já estava decidido, e nenhuma das duas ADRs decide rota.** A ADR-028 decide o
mínimo por modalidade, sobre `subtotalDosItens`, com zero valendo como "sem mínimo" e sem
aceite manual por cima; a ADR-024 decide um desconto de retirada fixo, para a loja inteira,
zero como padrão. As duas tratam do que o `order` fará com o valor em T01, e não de como o
comerciante o grava.

**Os dois campos já existiam no agregado e no banco.** O `Operacao` carrega
`descontoDeRetirada` e `pedidoMinimoPorModalidade`, e o construtor dele cobra a M15 e a M17; o
banco tem a coluna e a tabela `estabelecimento_pedido_minimo` desde a `V1`. Uma loja criada
por rota nasce com os dois em zero (`Operacao.nova`), e **não havia como sair do zero**: só a
fixture gravava outro valor.

**O molde estava pronto.** O `ConfiguracaoDaLojaController` tem o horário como sub-recurso de
`/settings`, com `GET` e `PUT`, sob `CONFIGURAR_LOJA`, e o gateway já roteia `/settings/**`
para o `merchant`.

## Decisão

### 1. Dois sub-recursos irmãos do horário

`GET` e `PUT` em `/api/v1/merchants/{estabelecimentoId}/settings/pedido-minimo` e em
`.../settings/desconto-retirada`, sob `CONFIGURAR_LOJA`, para a leitura e para a escrita.
**Campo, não transição:** `PUT` que substitui o valor, como o horário. Nenhum dos dois
publica evento — ninguém os consome; quem os lerá é o `order`, em T01.

### 2. O mínimo é substituído inteiro, e o zero é entrada

O corpo do pedido mínimo é o mapa inteiro, por modalidade. **O caso de uso não confere a
M17**: o `Estabelecimento.comPedidoMinimo` passa pelo construtor do `Operacao`, que exige uma
entrada para toda modalidade aceita e para nenhuma outra, e nenhuma negativa. **O zero entra
como entrada e significa "sem mínimo"; a modalidade omitida é recusada**, e não lida como
zero — é a frase da H1.2 presa ao código, e tem teste que a prende.

### 3. As recusas são 400, por um ponto de tradução

O `Operacao` recusa com `IllegalArgumentException`, e cada caso de uso a troca por
`OperacaoRecusada`, com motivo — `PEDIDO_MINIMO` ou `DESCONTO_NEGATIVO` —, no molde do
`HorarioRecusado`. O tratador escreve a frase. **400 e não 409**: o corpo contradiz uma
configuração que o próprio chamador lê pelo `GET` e que nenhuma rota muda — as modalidades da
loja —, e nada mudou entre a leitura e a escrita. É erro de quem pediu (a régua da E-8).

As três recusas da M17 — modalidade que falta, modalidade que a loja não aceita, valor
negativo — saem do mesmo construtor e **dividem uma frase só**, que diz o que é aceito.

### 4. O que esta ADR não faz

**Não trata das áreas de entrega**, a terceira escrita do `merchant`: têm agregado, faixas de
CEP e a ADR-020. **Não cria tela.** **Não cria invariante**: a M15 e a M17 bastam.

## Consequências

**A lista do que a fixture grava e a API não escreve encolhe para um item** (ADR-059, emenda de
17/10/2026; linha 34 do razão): no `merchant`, as áreas de entrega; no `catalog`, nada.

**A `CONFIGURAR_LOJA` passa a guardar três sub-recursos** — o horário, o mínimo e o desconto —,
com uma tela só, a do horário.

**Uma loja que não aceita retirada pode gravar desconto de retirada.** A M15 só exige o valor
não negativo, e nenhuma invariante liga o desconto à modalidade. É configuração morta — o
argumento com que a M17 recusa o mínimo de modalidade não aceita —, e esta rodada não a
recusa, porque recusar seria invariante nova. O segundo gatilho abaixo é ela.

**A suíte do `merchant` ganhou um teto de contextos de teste.** A classe de integração nova
fez o Postgres do Testcontainers recusar conexões (*"too many clients already"*): cada classe
com o próprio `IdentityDeMentira` tem um contexto só dela, guardado em cache com o pool aberto
até a JVM acabar. `spring.test.context.cache.maxSize=16`, em
`src/test/resources/spring.properties` do `merchant`, limita as conexões abertas a 80, qualquer
que seja o número de classes.

## Alternativas consideradas

**Um recurso de configuração só**, com os dois campos (e o horário) num corpo. **Recusada:** o
horário já é sub-recurso, e um quarto desenho ao lado de três não ganha nada.

**`PATCH` em `/settings`.** **Recusada:** são campos substituídos inteiros, e o horário usa
`PUT`.

**409 para o mínimo de modalidade que a loja não aceita**, como estado do mundo. **Recusada
pelo §3:** as modalidades não têm rota de escrita, e o corpo errado é o do chamador.

**Preencher com zero a modalidade omitida.** Corpo menor. **Recusada:** é ler ausência como
zero, que a M17 existe para impedir — e foi a mutação que esta rodada fez para provar o teste.

## Gatilho escrito

**A primeira tela que monte o pedido mínimo ou o desconto de retirada.** Aí as rotas desta ADR
ganham chamador, e a `CONFIGURAR_LOJA` passa a ter a segunda tela — que é o gatilho da linha
103 do razão.

**A primeira loja que, sem aceitar retirada, gravar desconto de retirada.** Aí o
`estabelecimento.md` decide se o desconto de uma loja sem retirada é recusado, como a M17
recusa o mínimo de modalidade não aceita.
