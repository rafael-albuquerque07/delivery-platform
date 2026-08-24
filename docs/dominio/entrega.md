# Domínio — Entrega, despacho e retorno

**Serviço:** `delivery-service` · **Status:** vigente (v1.1, 21/08/2026)
**Fontes:** PRD §5 (P2, P5), PRD §6 E6, Resposta v1.1 §5.3, ADR-020, ADR-022
**Invariantes do `CLAUDE.md` que este documento detalha:** 1, 8, 9
**Entra no marco 5** — entregadores, vínculo, remuneração e despacho

O entregador é **da casa** (P2). Isso apaga metade do que um serviço de entrega
costuma ter: não há leilão, não há pool competitivo, não há oferta que o
entregador aceita ou recusa, não há remuneração por corrida. Sobra o que a
pizzaria de fato faz — dizer quem leva o quê, e saber quem já voltou.

---

## 1. O que este serviço decide, e o que ele apenas registra

Distinção que evita o pior defeito possível aqui — dois serviços escrevendo o
mesmo fato e discordando.

| Fato | Quem decide |
|---|---|
| Quem leva esta entrega | **Aqui** |
| Quem é o próximo do rodízio | **Aqui** |
| O entregador voltou à loja | **Aqui** |
| Está disponível para nova entrega | **Aqui** |
| O pedido saiu para entrega | `order-service` (T16) |
| O pedido foi entregue | `order-service` (T19) — exige liquidação |
| O pedido foi cancelado | `order-service` |
| Quanto o entregador ganha | `merchant-service` (vínculo) + `settlement` (apuração) |

> **O `delivery-service` não declara uma entrega concluída. Ele fica sabendo.**

A razão é a invariante 1 do `CLAUDE.md`: nenhuma entrega conclui sem liquidação
registrada, e a liquidação vive dentro do agregado `Pedido`
([`pedido.md`](pedido.md) §1). Se este serviço pudesse marcar `ENTREGUE` por
conta própria, existiria um caminho para concluir sem passar pelo portão de
liquidação — e o portão deixaria de ser garantia.

Na prática o entregador toca um botão só. Esse toque vira a transição T19 no
`order-service`, com a liquidação junto; aqui a entrega muda de estado ao
consumir `PedidoEntregueV1`.

---

## 2. Agregados

```
Entrega  (raiz)
├── pedidoId, estabelecimentoId
├── entregadorId            null enquanto PENDENTE
├── estado
├── atribuicao              modo, momento: Instant, autor, sugeridoPeloRodizio
├── Tentativa         [n]   somente-inserção — momento: Instant, resultado, motivo
└── custodia                valorAReceber, trocoALevar   (derivados do pedido)

PosicaoDoEntregador  (raiz)   — um por entregador × estabelecimento
├── situacao               NO_ESTABELECIMENTO | EM_ROTA | RETORNANDO | INDISPONIVEL
├── desde                  Instant
├── ultimoRetornoEm        Instant    ← é isto que ordena o rodízio
└── entregasEmMaos         contagem
```

`desde` e `ultimoRetornoEm` são `Instant` (UTC), nunca hora local — a mesma
regra de todo instante persistido (ADR-025).

**`custodia` é derivada, nunca digitada.** `valorAReceber` é o `totalEfetivo` do
pedido; `trocoALevar` é o `trocoDevido` calculado pelo pedido (ADR-009). Um
campo editável aqui seria a porta dos fundos da invariante 2 do `CLAUDE.md` — o
valor cobrado tem que vir do pedido, e não adianta blindar o `order-service` se
a tela do entregador aceita outro número.

---

## 3. Duas máquinas de estado que o PRD lista como uma

H6.2 enumera: *"atribuída, no estabelecimento, retirada, em trânsito, entregue,
retornando, disponível na loja"*.

São **duas coisas diferentes** numa lista só. "Retornando" e "disponível na loja"
não descrevem uma entrega — descrevem o **entregador**. Uma entrega concluída
está concluída; quem volta para a loja e fica disponível é a pessoa.

Isso não é purismo. O rodízio ordena "por ordem de retorno à loja" (H6.2), e
ordem de retorno é atributo do entregador. Enquanto as duas máquinas estiverem
fundidas, o rodízio não tem por onde ordenar — e a primeira implementação vai
inventar uma consulta que varre entregas para descobrir quem voltou primeiro.

### Estados da `Entrega`

| Estado | Significado | Terminal |
|---|---|---|
| `PENDENTE` | Criada, aguardando entregador. Fica no balcão | não |
| `ATRIBUIDA` | Tem entregador designado. Ainda não saiu | não |
| `EM_ROTA` | Saiu do estabelecimento com o pedido | não |
| `ENTREGUE` | Concluída | **sim** |
| `DEVOLVIDA` | Voltou à loja sem entregar | **sim** |
| `CANCELADA` | Pedido cancelado antes da conclusão | **sim** |

`RETIRADA` e `EM_TRANSITO` do PRD **viram um estado só**, `EM_ROTA`. A distinção
— o pedido está na mão do entregador, mas ele ainda não saiu — não governa
nenhuma regra, e cada estado a mais é um toque que o entregador não vai dar no
meio do sábado. Se um dia surgir regra que dependa disso, o desdobramento é
barato; o contrário não é.

`NAO_ENTREGUE` **não é estado da entrega** — é `Tentativa` com resultado
frustrado. Uma entrega pode ter três tentativas e continuar `EM_ROTA`. Modelá-lo
como estado obrigaria a voltar de `NAO_ENTREGUE` para `EM_ROTA` a cada nova
tentativa, perdendo o histórico de quantas houve.

### Situações do entregador

| Situação | Significado |
|---|---|
| `NO_ESTABELECIMENTO` | Na loja, disponível. **Elegível ao rodízio** |
| `EM_ROTA` | Com pelo menos uma entrega em mãos |
| `RETORNANDO` | Última entrega concluída, ainda não chegou |
| `INDISPONIVEL` | Pausa, almoço, fim de turno |

`RETORNANDO` existe para o painel: o operador precisa saber que o Jorge está a
cinco minutos antes de decidir se segura o pedido ou chama o Rafa.

---

## 4. Tabela de transições da `Entrega`

| # | De | Para | Gatilho | Guarda | Origem |
|---|---|---|---|---|---|
| D01 | — | `PENDENTE` | `PedidoProntoV1` com `modalidade = ENTREGA` | — | Evento |
| D02 | `PENDENTE` | `ATRIBUIDA` | Operador designa, direto ou por rodízio | Entregador com **vínculo ativo** e **jornada aberta** · abaixo do teto de simultâneas | Local |
| D03 | `ATRIBUIDA` | `ATRIBUIDA` | Reatribuição | Mesmas guardas de D02 · motivo obrigatório | Local |
| D04 | `ATRIBUIDA` | `PENDENTE` | Operador desfaz a atribuição | Motivo obrigatório | Local |
| D05 | `ATRIBUIDA` | `EM_ROTA` | `PedidoSaiuParaEntregaV1` | — | Evento |
| D06 | `EM_ROTA` | `EM_ROTA` | Tentativa frustrada registrada | Motivo obrigatório | Local |
| D07 | `EM_ROTA` | `ENTREGUE` | `PedidoEntregueV1` | — | Evento |
| D08 | `EM_ROTA` | `DEVOLVIDA` | Entregador volta com o pedido | `PedidoCanceladoV1` recebido, ou cancelamento em curso | Evento + local |
| D09 | `PENDENTE` · `ATRIBUIDA` · `EM_ROTA` | `CANCELADA` | `PedidoCanceladoV1` | — | Evento |

**Não existe** transição saindo de `ENTREGUE`, `DEVOLVIDA` ou `CANCELADA`. E não
existe caminho de `PENDENTE` direto para `EM_ROTA`: ninguém sai sem estar
designado, senão não há a quem cobrar o dinheiro no fim do turno.

D03 e D04 existem porque a realidade tem: o Jorge furou, o pedido volta para o
balcão, o Rafa leva. Ambos exigem motivo — sem isso, "por que este pedido
demorou" não tem resposta.

**Correspondência com o pedido.** D06 é o mesmo fato que a transição T20 em
[`pedido.md`](pedido.md) — cliente ausente. O pedido entra em `NAO_ENTREGUE`,
que é um limbo à espera de decisão; aqui a entrega continua `EM_ROTA` e ganha uma
`Tentativa`. Granularidades diferentes do mesmo acontecimento, e de propósito: o
pedido precisa de um estado onde alguém decide, a entrega precisa da contagem.

---

## 5. Atribuição: não existe aceite

> **A atribuição é uma ordem de serviço, não uma oferta.**

O entregador não aceita nem recusa. Ele é funcionário da casa (P2), e o
comerciante decide quem leva o quê. Isso elimina de uma vez: oferta, janela de
aceite, timeout de oferta, reoferta, fila de candidatos, penalidade por recusa e
todo o desenho de leilão que a v1.0 tinha.

Se o entregador não pode levar, ele fala com o operador, que reatribui (D03).
A conversa é humana; o sistema registra o resultado.

### Direta

O operador escolhe no painel. É o caminho principal, e é o que o comerciante já
faz gritando o nome do motoboy.

### Rodízio

O sistema **sugere** o próximo; o operador confirma com um toque, e pode
ignorar. A sugestão é ordenada assim:

```
candidatos = entregadores com vínculo ATIVO
           ∧ jornada ABERTA nesta loja
           ∧ situacao = NO_ESTABELECIMENTO
           ∧ entregasEmMaos < maxEntregasSimultaneas

próximo    = candidato com o MENOR ultimoRetornoEm
             (quem voltou há mais tempo é quem está esperando há mais tempo)

empate     = menor contagem de entregas na jornada; persistindo, ordem de
             abertura da jornada — determinístico, nunca aleatório
```

**Quando o operador ignora a sugestão, isso é gravado** (`sugeridoPeloRodizio`
guarda quem o sistema indicou). Não para vigiar ninguém: sem esse dado, "o
rodízio não está funcionando" é uma discussão sem evidência, e a resposta quase
sempre é que ele está funcionando e sendo contornado por um bom motivo que
ninguém registrou.

Lista de candidatos vazia **não é erro**. É informação para o painel: "ninguém
disponível, dois retornando". O pedido fica `PENDENTE`, que é exatamente o que
acontece no balcão.

---

## 6. A tela do entregador

H6.2: endereço, itens, valor a receber do cliente e troco a levar. Tudo derivado
do pedido, nada editável.

| Campo | Fonte | Regra |
|---|---|---|
| Endereço | `enderecoTextual` congelado no pedido | Texto, não coordenada (P5, ADR-020) |
| Itens | Snapshot do pedido (ADR-018) | Com opções e remoções — a cozinha e o cliente conferem pelo mesmo texto |
| Valor a receber | `totalEfetivo` do pedido | **Nunca digitado.** Muda se houver `Ajuste` |
| Método declarado | Pedido | Com o aviso de que pode mudar na porta |
| Troco a levar | `trocoDevido` do pedido | Zero é diferente de "não informado" |
| Situação do Pix | `Liquidacao.situacao` | **Sem texto ambíguo** (H5.3) |

A última linha merece o destaque que o PRD dá: a tela precisa dizer, sem margem,
se o Pix caiu. `AGUARDANDO_CONFIRMACAO` não pode aparecer como um símbolo verde
discreto que o entregador lê como "pago". É esse pixel que decide se o golpe do
comprovante falso funciona.

**Coordenada exata não é exibida nem registrada.** Sem geoprocessamento no MVP e
com a proibição de log do `CLAUDE.md`, o endereço textual é o que existe — e é o
que o entregador de bairro usa de qualquer forma.

---

## 7. Retorno e disponibilidade

> **Retorno à loja é estado registrado, não suposição** (H6.3).

Concluir a última entrega **não** torna o entregador disponível: ele fica
`RETORNANDO`. Só o registro do retorno — toque dele no aplicativo ou do operador
no painel — devolve `NO_ESTABELECIMENTO` e atualiza `ultimoRetornoEm`.

Inferir a disponibilidade a partir da conclusão quebraria o rodízio de um jeito
específico e injusto: quem entrega perto voltaria "disponível" antes de estar na
loja e receberia mais pedidos que quem entrega longe. O critério passaria a ser
distância, que é justamente o que a premissa P5 tirou do sistema.

`ultimoRetornoEm` é atualizado **só no retorno**, nunca na conclusão. É o único
campo que ordena o rodízio, e ele precisa significar uma coisa só.

---

## 8. Várias entregas ao mesmo tempo

O entregador sai com três pedidos. É rotina em pizzaria no sábado, e o modelo
precisa suportar sem inventar um agregado de viagem.

- Um entregador pode ter **N entregas** `EM_ROTA` simultâneas.
- `maxEntregasSimultaneas` é configuração do estabelecimento; acima dele o
  entregador sai do rodízio, mas o operador ainda pode atribuir diretamente.
- Disponibilidade **não é binária**. `EM_ROTA` com uma entrega e teto três não
  impede a segunda — mas o rodízio prefere quem está na loja.
- Ordem das paradas **não é modelada**. Sem cálculo de rota (P5), quem decide a
  sequência é o entregador, que conhece o bairro melhor que qualquer motor.

O que **não** existe: agrupar entregas numa "viagem" com identidade própria.
Seria necessário para ETA e otimização de rota, que estão fora de escopo. Se o
marco 11 trouxer rastreamento, é aí que a discussão volta.

---

## 9. Eventos

**Consome** — todos idempotentes, chave em `processed_messages`:

| Evento | De | Efeito |
|---|---|---|
| `PedidoProntoV1` | `order` | D01 — cria a entrega, se modalidade `ENTREGA` |
| `PedidoSaiuParaEntregaV1` | `order` | D05 |
| `PedidoEntregueV1` | `order` | D07 |
| `PedidoCanceladoV1` | `order` | D08, D09 |
| `VinculoEntregadorAlteradoV1` | `merchant` | Entra ou sai do rodízio |

**Publica:**

| Evento | Quando | Consumidores |
|---|---|---|
| `EntregaAtribuidaV1` | D02, D03 | `conversation` (avisar o cliente que saiu para entrega é T16, não isto) |
| `EntregadorRetornouV1` | §7 | Painel |
| `EntregaDevolvidaV1` | D08 | `settlement` — o item voltou, a liquidação foi `NAO_LIQUIDADO` |

Note que **este serviço não publica nada que o `settlement` use para dinheiro**.
A apuração se alimenta de `PedidoEntregueV1`, que carrega a liquidação. Um
segundo caminho para o mesmo fato criaria contagem dupla — exatamente o que J1 e
J3 em [`liquidacao.md`](liquidacao.md) proíbem.

---

## 10. Invariantes

| # | Invariante | O que quebra sem ela |
|---|---|---|
| N1 | Uma entrega pertence a **um** pedido; um pedido `ENTREGA` tem **uma** entrega não terminal | Dois entregadores com o mesmo pedido |
| N2 | Atribuição exige vínculo ativo **e** jornada aberta | Entrega sem ninguém a quem cobrar o dinheiro no fim do turno |
| N3 | `ENTREGUE` só por `PedidoEntregueV1` | Caminho para concluir sem liquidação — §1 |
| N4 | `custodia` é derivada do pedido, nunca digitada | Valor cobrado deixa de vir do pedido |
| N5 | `ultimoRetornoEm` muda só no retorno registrado | Rodízio passa a premiar quem entrega perto |
| N6 | Reatribuição e desatribuição exigem motivo | "Por que demorou" sem resposta |
| N7 | `Tentativa` é somente-inserção | Some o histórico de quantas vezes se tentou |
| N8 | Nenhum estado terminal transita | Entrega ressuscita |
| N9 | Não existe aceite do entregador | Reintroduz leilão, contra P2 |
| N10 | Coordenada exata não é exibida nem logada | LGPD e `CLAUDE.md` |
| N11 | `entregasEmMaos` == contagem de entregas `EM_ROTA` do entregador | Rodízio decide por número errado |

---

## 11. Uma observação honesta sobre este serviço

Depois da v1.1, o `delivery-service` é o mais fino dos oito. O vínculo e a
remuneração foram para o `merchant-service` (ADR-022); a apuração foi para o
`settlement`; a conclusão é decidida pelo `order`. Sobram atribuição, rodízio,
posição do entregador e retorno.

Isso é pouco, e vale dizer em voz alta em vez de descobrir na revisão de código.
Os argumentos para mantê-lo separado:

- **Rodízio e posição são estado operacional de alta rotatividade** que não tem
  nada a ver com o ciclo de vida do pedido. Dentro do `order-service`, virariam
  duas tabelas e um conjunto de regras que nenhum outro caso de uso do pedido
  encosta.
- **É onde o marco 11 aterrissa.** Rastreamento, telemetria e posição em mapa
  são deste domínio, e quando entrarem o serviço deixa de ser fino.
- **O painel de despacho tem um dono.** Sem serviço próprio, a tela mais usada
  pelo operador no pico não teria a quem pertencer.

O argumento contrário — que isto poderia ser um módulo do `order-service` até o
marco 11 — é legítimo. Se ele vencer, que seja por decisão registrada em ADR, e
não por alguém achar o serviço vazio demais e começar a enfiar coisa nele.

---

## 12. O que este documento deliberadamente não decide

- **`maxEntregasSimultaneas` inicial.** É configuração de estabelecimento e o
  número de partida é decisão de produto.
- **Aplicativo do entregador × tela web.** É apresentação. O domínio não muda.
- **Política de tentativas** — quantas, com que intervalo. Configuração.
- **O que acontece com a mercadoria devolvida.** `DEVOLVIDA` registra o retorno
  do item; se ele é descartado, revendido ou reentregue é decisão operacional
  sem regra no sistema. **Pendência** se aparecer exigência fiscal.
- **Entregador atendendo duas lojas ao mesmo tempo.** Ele tem duas jornadas e
  duas posições, e nada impede que esteja `NO_ESTABELECIMENTO` em uma e
  `EM_ROTA` em outra — o que é fisicamente impossível. Nenhuma das duas lojas
  enxerga a outra (P3), então não há como detectar. **Aceito como limitação
  conhecida**, e é o tipo de coisa que só aparece quando aparecer.
