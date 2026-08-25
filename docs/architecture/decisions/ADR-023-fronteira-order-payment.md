# ADR-023 — Fronteira entre `order` e `payment`: o pedido é dono do registro, o pagamento é a fronteira com o PSP

**Status:** Aceita — 23/08/2026
**Fecha o "ponto em aberto" da:** ADR-021
**Relacionada:** ADR-009 (valores e liquidação), ADR-010 (Saga do pedido)
**Detalha:** `docs/dominio/pedido.md` §7, `docs/dominio/liquidacao.md` §2
**Invariantes do `CLAUDE.md`:** 1 (nenhuma conclusão sem liquidação), 6 (imagem não confirma Pix), 8 (banco por serviço)
**Precisa existir antes do marco 4** — é quando o `payment-service` ganha código

## Contexto

Duas afirmações do próprio repositório se contradizem.

O §5.3 da resposta v1.1 descreve o `payment-service` como **"registro de
liquidações"** — ou seja, ele seria o livro do que foi recebido.

O `docs/dominio/pedido.md` §1 coloca `Liquidacao` **dentro do agregado
`Pedido`**, no `order-service`. O motivo está lá: a invariante 1 do `CLAUDE.md`
diz que nenhuma entrega ou retirada conclui sem liquidação registrada, e essa
invariante só é verificável se quem controla a transição de estado controla
também o registro.

As duas não podem estar certas. Enquanto ficarem, quem for implementar o marco 4
vai escolher uma por acaso — e provavelmente a errada, porque o §5.3 é o
documento mais recente e mais autoritativo em aparência.

## Decisão

### O `order-service` é dono do registro. O `payment-service` é dono da conversa com o PSP.

```
                 order-service                    payment-service
        ┌──────────────────────────┐      ┌──────────────────────────────┐
        │  Pedido (agregado)       │      │  Cobrança                    │
        │  └── Liquidacao [n]      │      │  ├── txid, QR dinâmico       │
        │      metodoLiquidado     │      │  ├── pedidoId (correlação)   │
        │      valorEfetivo        │      │  ├── estado no PSP           │
        │      situacao            │◀─────│  └── payload bruto do webhook│
        │                          │      │                              │
        │  Invariante 1 verificada │      │  Assinatura, idempotência,   │
        │  DENTRO da transação     │      │  retentativa, segredo do PSP │
        └──────────────────────────┘      └──────────────────────────────┘
                          LiquidacaoConfirmadaV1
```

**O `payment` não é o livro-caixa.** Ele é a camada anticorrupção do provedor de
pagamento: gera a cobrança, guarda o `txid`, recebe o webhook, valida a
assinatura, e traduz tudo isso num evento de domínio. O que foi recebido, de
quem, por qual método e sob custódia de quem é assunto do pedido.

### Divisão de responsabilidades

| | `order` | `payment` |
|---|---|---|
| Gravar `Liquidacao` | **sim** | não |
| Verificar a invariante 1 na transição | **sim** | não |
| Gerar cobrança Pix com `txid` e QR | não | **sim** |
| Expor o endpoint de webhook | não | **sim** |
| Validar assinatura do PSP | não | **sim** |
| Guardar o payload bruto recebido | não | **sim** |
| Correlacionar `txid` ↔ `pedidoId` | não | **sim** |
| Marcar a liquidação como `CONFIRMADA` | **sim**, ao consumir o evento | não |
| Cartão e Pix online (marco 8) | não | **sim** |
| Guardar segredo do PSP | não | **sim** |

### O fluxo do Pix na entrega, ponta a ponta

```
1. order    → pedido em PRONTO, cliente declarou PIX
2. order    → solicita cobrança ao payment (síncrono, com timeout)
3. payment  → cria a cobrança no PSP, guarda txid ↔ pedidoId, devolve o QR
4. order    → grava Liquidacao situacao = AGUARDANDO_CONFIRMACAO
5. cliente  → paga. O PSP chama o webhook do payment
6. payment  → valida assinatura, confere idempotência, publica
              LiquidacaoConfirmadaV1 { pedidoId, liquidacaoId, txid, valor }
7. order    → consome, muda situacao para CONFIRMADA
8. settlement → consome PedidoEntregueV1 (não este evento) — ver abaixo
```

**O passo 4 acontece antes do 5, e é isso que permite T19.** O pedido é entregue
com a liquidação `AGUARDANDO_CONFIRMACAO`; a entrega não espera o webhook
(`pedido.md` §7). O entregador não fica parado na porta por causa de latência de
PSP.

**Comprovante em imagem não entra em lugar nenhum deste fluxo.** Invariante 6.
Só o passo 6 muda `situacao`.

### O `settlement` não escuta o `payment`

Isso é regra, não detalhe. A apuração se alimenta de `PedidoEntregueV1` e
`PedidoRetiradoV1`, que **carregam a liquidação no payload**, mais
`LiquidacaoConfirmadaV1` para o Pix que confirma depois. Nunca de um evento do
`payment` sobre o mesmo fato.

Dois caminhos para o mesmo dinheiro é contagem dupla, e J1 e J3 de
`liquidacao.md` existem justamente para impedir isso.

### Idempotência em dois lugares, por motivos diferentes

| Onde | Chave | Contra o quê |
|---|---|---|
| `payment`, no webhook | id da notificação do PSP | O PSP reenvia. O endereço é público |
| `order`, no consumidor | `liquidacaoId` | O broker reentrega |

## Consequências

**Positivas**

- A invariante 1 continua verificável dentro de uma transação local. Nenhuma
  checagem de conclusão vira chamada de rede.
- O `payment` ganha um recorte com sentido próprio — segredo, assinatura,
  retentativa, formato do provedor — em vez de ser um espelho do pedido.
- Trocar de PSP passa a ser trocar um adaptador. O modelo de liquidação não sabe
  qual provedor existe.
- O marco 8 (cartão, Pix online) cresce dentro do `payment` sem tocar no pedido.

**Negativas**

- **Dado de pagamento fica em dois lugares.** `txid` e valor existem no `payment`
  e no `order`. Divergência entre os dois — "o PSP confirmou e o pedido continua
  pendente" — é possível, e precisa de **procedimento de reconciliação**, não de
  um `catch`. Escrito em `docs/operacao/reconciliacao-de-pagamento.md`, com os
  quatro formatos da divergência em `pagamento.md` §8.
- **Uma chamada síncrona a mais no caminho do pedido**, no passo 2. Com timeout e
  disjuntor; se o `payment` estiver fora, o pedido é fechado sem cobrança Pix e o
  cliente escolhe outro método. Não trava a venda.
- **O `payment` é fino até o marco 4** e ocioso antes disso. Mesma discussão do
  `delivery-service` em `entrega.md` §11 — e mesma resposta: recorte claro vale
  mais que volume de código.

## Alternativas consideradas

- **`payment` como livro de liquidações, leitura literal do §5.3.** Rejeitada:
  transforma a invariante 1 numa consulta entre serviços no meio de uma transição
  de estado. É exatamente o ponto onde a inconsistência nasce, e a invariante que
  vende o produto deixaria de ser garantida por construção.
- **Sem `payment-service`; adaptador do PSP dentro do `order`.** A mais tentadora,
  e defensável para o MVP: o `order` já é dono do registro, e o adaptador seria
  mais uma implementação de porta. Rejeitada por três coisas que não são do
  pedido — o segredo do PSP, um endpoint público de webhook com política de
  retentativa própria, e o crescimento do marco 8. Se um dia se decidir fundir, a
  porta já existe e a fusão é barata; o caminho contrário não é.
- **Duplicar a `Liquidacao` nos dois, sincronizada por evento.** Rejeitada: dois
  donos do mesmo fato é a pior das três opções. Alguém acabaria somando a cópia
  errada num relatório.

## Emenda que esta decisão provoca

O §5.3 da resposta v1.1 diz "payment — Reescrito como **registro de
liquidações**". Sob esta ADR, a descrição correta é "**fronteira com o PSP**:
cobrança Pix com `txid`, webhook e confirmação". O PDF é documento publicado e
não se reescreve; esta ADR é a emenda, e a ADR-021 passa a apontar para cá em vez
de listar o assunto como aberto.
