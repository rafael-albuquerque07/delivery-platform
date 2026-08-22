# ADR-010 — Saga do pedido: pivô em PRONTO, pagamento fora da transação

**Status:** Aceita — 16/08/2026 · **reescrita em 21/08/2026** pela arquitetura v1.1
**Substitui:** `ADR-010-saga-com-estoque-opcional.md` (mesmo número, título anterior)
**Relacionada:** ADR-009 (valores e liquidação), ADR-018 (`stockControlledSnapshot`), ADR-022 (remuneração)
**Premissas do PRD que sustentam esta decisão:** P1, P2, P6

> **Reescrita v1.1 — o que mudou e por quê.** A versão original orquestrava
> quatro pernas com o **pagamento como transação-pivô**: nada acontecia antes de
> `PaymentApprovedV1`. A premissa P1 derrubou isso — na maioria dos pedidos a
> plataforma não custodia o dinheiro, e o pagamento acontece na porta do cliente,
> depois de a comida existir. Uma Saga que espera aprovação de pagamento para
> começar o preparo descreve um produto que não é este. O pivô muda de lugar, o
> pagamento sai da transação distribuída e o estoque adormece até o marco 10.

## Contexto

Uma Saga precisa de três coisas para ser desenhável: saber quais passos são
**compensáveis** (dá para desfazer barato), qual é a **transação-pivô** (a partir
dela não se desfaz mais, só se avança) e quais são **retriáveis** (podem falhar,
mas serão tentados até dar certo, porque desistir não é opção).

Na v1.0 o pivô era a cobrança: o cartão passou, agora vai. Na v1.1 não há
cobrança para passar. O cliente paga em dinheiro na porta, ou no cartão da
maquininha do entregador, ou por Pix confirmado por webhook — sempre **depois**
de a comida estar pronta e ter saído.

Isso reposiciona a pergunta. O ponto de não-retorno deste sistema não é
financeiro: é **produtivo**. Quando a pizza está no balcão, o custo já foi
incorrido, e nenhum estorno traz a farinha de volta. E é também o ponto em que o
pedido deixa de ser assunto de um serviço só.

## Decisão

### O pivô é `PRONTO`, e o critério é estrutural

Antes de `PRONTO`, cancelar um pedido é uma **transação local** no
`order-service`: muda-se um estado, registra-se a causa, acabou. Nenhum outro
serviço precisa desfazer nada, porque nenhum outro serviço foi acionado.

Em `PRONTO`, o pedido entra no fluxo distribuído — cria-se a entrega, atribui-se
o entregador, começa a custódia de valores. Daí em diante não existe
compensação barata: existe **avançar**, com retentativa, até um estado terminal.

```
          compensável (local)                    PIVÔ      retriável (remoto)
 ┌──────────────────────────────────────────┐     │    ┌──────────────────────┐

 RECEBIDO ──▶ CONFIRMADO ──▶ EM_PREPARO ────────▶ PRONTO ──▶ despacho ──▶ liquidação
     │            │  ▲            │                 │
     │            │  └── PAGO ────┘                 └── cancelar aqui é
     └── CANCELADO ┘     (marco 8)                      PERDA REGISTRADA,
         (sem efeito remoto)                            não compensação
```

O portão de compromisso — `CONFIRMADO` no presencial, `PAGO` no online — está
detalhado em `docs/dominio/pedido.md` §2. Ele **não é o pivô**: um pedido
confirmado e ainda não preparado se cancela sem efeito remoto nenhum.

Cancelar depois de `PRONTO` continua sendo possível — o cliente some, o endereço
não existe — mas é um **lançamento de perda** com autor e motivo, nunca um
desfazimento. O sistema não finge que o pedido não aconteceu.

### O pagamento sai da Saga

`payment` deixa de ser um passo orquestrado e vira **registro de liquidação**
(ADR-009). Não há `PENDING_PAYMENT` no caminho comum, não há janela de 10
minutos, não há timeout de pagamento cancelando pedido.

A única exceção é o Pix com confirmação por webhook, e mesmo ela não é perna de
Saga: é um evento assíncrono que muda o estado de uma `Liquidacao`, não o estado
do pedido. **Um pedido entregue com Pix ainda não confirmado é um pedido
entregue** — com uma liquidação pendente, que aparece no fechamento como tal.
Prender a entrega ao webhook seria travar o entregador na porta do cliente por
causa de latência de PSP.

### Duas pernas remotas no MVP

| # | Perna | Serviço | Classe | Se falhar |
|---|---|---|---|---|
| 1 | Criar entrega e atribuir entregador | `delivery` | retriável | Retentativa com backoff; após N tentativas, alerta no painel — **nunca** cancelamento automático |
| 2 | Lançar liquidação na jornada | `settlement` | retriável | Retentativa; a liquidação já está gravada no pedido, a jornada é que precisa alcançá-la |

Nenhuma das duas é compensável, e isso é intencional: depois do pivô, compensar
significaria "des-entregar", que não existe.

### A perna de estoque fica dormente

A premissa P6 diz que a maioria dos produtos opera em **disponibilidade
qualitativa** — "acabou a calabresa" — e não em saldo contado. O controle
quantitativo foi adiado para o marco 10, e com ele o `inventory-service` sai do
MVP.

O que **permanece** desde já:

- `stockControlledSnapshot` continua congelado no item (ADR-018). Custa um
  boolean e evita uma migration de dados quando o marco 10 chegar.
- O estado `AGUARDANDO_ESTOQUE` existe na máquina de estados, **inalcançável no
  MVP**, com o teste que prova que nenhum pedido entra nele enquanto todos os
  itens tiverem `stockControlledSnapshot == false`.

A disponibilidade qualitativa não é perna de Saga: é uma verificação **síncrona**
no fechamento do pedido, contra o catálogo. Item indisponível não gera pedido —
gera uma volta ao cliente antes de existir pedido algum.

### Quando o marco 10 chegar

A reserva entra como perna **compensável antes do pivô**, com as regras que a
versão anterior desta ADR já tinha decidido e que continuam válidas: reserva
tudo-ou-nada sobre o conjunto de itens controlados, TTL maior que a janela de
decisão, commit idempotente, e `AGUARDANDO_ESTOQUE` como entrada alternativa.
Nada disso precisa ser reprojetado — precisa ser ligado.

### `motivoCancelamento`

```
ESTABELECIMENTO_RECUSOU · CLIENTE_DESISTIU · FORA_DE_AREA
ITEM_INDISPONIVEL · CLIENTE_AUSENTE · ENDERECO_INEXISTENTE
SEM_ENTREGADOR · PAGAMENTO_NAO_REALIZADO · ERRO_OPERACIONAL
```

Sem isso, "pedidos cancelados" no relatório é um número sem diagnóstico. Com
isso, `CLIENTE_AUSENTE` alto vira conversa sobre confirmação no canal, e
`SEM_ENTREGADOR` alto vira decisão de contratação.

Note que `PAGAMENTO_NAO_REALIZADO` é motivo de cancelamento **depois** da
entrega tentada — não antes. É o caso do cliente que não tinha o dinheiro na
porta.

## Consequências

**Positivas**

- O fluxo comum — a esmagadora maioria dos pedidos — deixa de atravessar dois
  serviços e uma espera de webhook que não existem no negócio real.
- O comerciante começa a preparar quando decide preparar, que é o que ele já
  faz. O sistema para de exigir uma aprovação que ninguém pediu.
- A Saga fica pequena o bastante para caber na cabeça de quem a mantém: duas
  pernas remotas, ambas retriáveis, um pivô claro.
- Cancelamento depois do pivô vira dado contábil em vez de estado inconsistente.

**Negativas**

- **Não há garantia de recebimento.** O pedido é produzido antes de qualquer
  pagamento, e o cliente pode não pagar. Isso não é defeito do desenho — é a
  realidade do negócio, e o produto responde a ela **medindo**: `NAO_LIQUIDADO`
  é registro de primeira classe e aparece no fechamento. Nenhuma arquitetura
  resolve calote; a nossa se recusa a escondê-lo.
- Duas pernas retriáveis sem compensação exigem **fila morta e procedimento
  operacional**. "Tentar para sempre" sem um lugar onde a mensagem morre e
  alguém olha é como se perde pedido em silêncio.
- O estado `AGUARDANDO_ESTOQUE` inalcançável é código morto durante todo o MVP.
  Aceito, com teste que documenta a intenção — a alternativa é migration de
  dados no marco 10.
- Pix pendente na entrega cria um terceiro estado no fechamento ("aguardando
  confirmação") que não é nem recebido nem calote. Precisa de tratamento
  explícito no extrato, senão vira divergência falsa.

## Alternativas consideradas

- **Manter o pagamento como pivô, com "pagamento na entrega" modelado como
  aprovação automática imediata.** Rejeitada: é mentira estrutural. O sistema
  registraria "pago" no momento do pedido para um dinheiro que talvez nunca
  chegue, e o fechamento de caixa — a funcionalidade que vende o produto —
  passaria a somar valores fictícios.
- **Pivô no início do preparo (`EM_PREPARO`) em vez de `PRONTO`.** Defensável, e
  foi considerada seriamente: o custo é incorrido ali, não em `PRONTO`.
  Rejeitada porque o critério escolhido é **estrutural, não econômico** — o pivô
  marca onde a transação deixa de ser local. Cancelar durante o preparo continua
  sendo decisão do comerciante com perda parcial, e o sistema registra a perda
  sem precisar de um pivô mais cedo. Um critério econômico exigiria decidir qual
  percentual de custo já incorrido "conta", que não é pergunta de arquitetura.
- **Exigir confirmação do Pix antes de liberar a saída do entregador.**
  Rejeitada: prende a operação a uma latência de terceiro, e o modo de falha —
  entregador parado na loja porque o webhook atrasou — é pior que o problema.
- **Manter o `inventory-service` no MVP "já que está no esqueleto".**
  Rejeitada: é o argumento do custo afundado. O serviço existe no repositório
  porque foi criado sob a v1.0, o que não é razão para mantê-lo.
