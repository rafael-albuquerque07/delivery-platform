# Domínio — Pedido

**Serviço:** `order-service` · **Status:** vigente (v1.1, 21/08/2026)
**Fontes:** PRD §5 (P1–P6), PRD §6 E4 e E5, **Resposta v1.1 §5.4**, ADR-009, ADR-010, ADR-018, ADR-020
**Invariantes do `CLAUDE.md` que este documento detalha:** 1, 2, 3, 4, 5, 6

> **Correção de 21/08/2026.** A primeira versão deste documento omitia os estados
> `CONFIRMADO` e `PAGO` e usava `EM_ROTA` e `AGUARDANDO_RETIRADA` no lugar de
> `SAIU_PARA_ENTREGA` e `AGUARDANDO_CLIENTE`. A figura 2 do documento v1.1 §5.4 é
> a autoridade, e ela tem o portão de compromisso que eu havia dissolvido dentro
> da transição de aceite. Corrigido abaixo. Nomes de transição mudaram de número.

Este documento existe porque o `CLAUDE.md` exige que a máquina de estados seja
"tabela de transições válidas, testada" — e a tabela não existia. Diagrama não é
tabela: não se percorre num teste parametrizado.

---

## 1. Agregado

`Pedido` é raiz de agregado. Nada abaixo dele é criado, alterado ou lido por
fora dele.

```
Pedido  (raiz)
├── identificacao        id, numeroSequencialNaLoja, estabelecimentoId, origem
├── cliente              nome, telefone, referenciaExterna do canal
├── entrega              modalidade, enderecoTextual, nomeAreaSnapshot, taxaSnapshot
├── pagamento            momentoDeclarado, metodoDeclarado, trocoPara
├── valores              itemsSubtotal, deliveryFee, tip, discount, total
├── estado               estado, subestado, motivoCancelamento
├── ItemDoPedido    [n]  congelado — nome, precoBase, opções, stockControlledSnapshot
├── Ajuste          [n]  somente-inserção
└── Liquidacao      [n]  somente-inserção
```

`origem` — `WHATSAPP | WEB | BALCAO` — é **atributo, não estado** (v1.1 §5.4). O
mesmo vale para o subestado de preparo. Modelá-los como estado multiplicaria a
máquina por seis sem acrescentar uma regra.

**Por que `Liquidacao` fica dentro do agregado.** A invariante I6 — nenhum estado
terminal de conclusão sem liquidação registrada — só é verificável se quem
controla a transição também controla a liquidação. Fora do agregado, a checagem
vira consulta a outro serviço no meio de uma transição de estado, que é
exatamente onde a inconsistência nasce.

O `settlement-service` mantém a **sua própria** cópia, alimentada por evento.
Não lê esta tabela (invariante 8 do `CLAUDE.md`).

**Fronteira transacional:** uma transição de estado, os ajustes e as liquidações
que ela produz gravam na **mesma transação local** do `order-service`, junto com
a linha do outbox. Nunca em duas.

---

## 2. Estados

| Estado | Significado | Terminal |
|---|---|---|
| `RECEBIDO` | Cliente confirmou. Ainda **não há compromisso** do estabelecimento | não |
| `CONFIRMADO` | Compromisso presencial — **ato humano** do estabelecimento | não |
| `PAGO` | Compromisso online — webhook do PSP. **Marco 8** | não |
| `AGUARDANDO_ESTOQUE` | Reserva pendente. **Inalcançável no MVP** — marco 10 | não |
| `EM_PREPARO` | Em produção ou separação. Tem subestado | não |
| `PRONTO` | **Transação-pivô** (ADR-010). Existe fisicamente | não |
| `SAIU_PARA_ENTREGA` | Com o entregador. Só `modalidade = ENTREGA` | não |
| `AGUARDANDO_CLIENTE` | No balcão. Só `modalidade = RETIRADA` | não |
| `NAO_ENTREGUE` | Tentativa frustrada. Ainda decidível | não |
| `ENTREGUE` | Concluído com entrega | **sim** |
| `RETIRADO` | Concluído com retirada | **sim** |
| `CANCELADO` | Encerrado sem conclusão, com motivo obrigatório | **sim** |

### O portão de compromisso

`RECEBIDO` não obriga ninguém. É o cliente dizendo o que quer. O compromisso do
**estabelecimento** é um estado à parte, com duas entradas que convergem para o
mesmo fluxo de preparo:

```
                      RECEBIDO
                         │
        ┌────────────────┴────────────────┐
   CONFIRMADO                           PAGO
   presencial · ato humano       online · webhook do PSP
        └────────────────┬────────────────┘
                    EM_PREPARO
```

**`CONFIRMADO` é ato humano, não fato verificável.** A plataforma não retém
valor, não estorna e não bloqueia (P1). Todo controle contra abuso — limite por
histórico, teto para cliente sem cadastro, bloqueio por reincidência — é
**comercial**, e precisa ser registrado como tal. Nenhum deles é garantia
técnica, e chamá-los assim cria uma falsa sensação de proteção onde não há.

`NAO_ENTREGUE` é **acréscimo meu** à figura do v1.1, que simplifica. Uma tabela
de transições precisa dele: sem ele, cliente ausente deixa o pedido sem saída, e
I11 quebra.

### Subestados de `EM_PREPARO`

Dependem do `tipoDeOperacao` do estabelecimento (H1.2). O subestado é **livre**
dentro do seu conjunto — o operador vai e volta à vontade; nenhuma regra de
negócio depende dele. Ele existe para a tela, não para o domínio.

| `tipoDeOperacao` | Subestados válidos |
|---|---|
| `PRODUCAO` | `NA_FILA` · `EM_PRODUCAO` · `FINALIZANDO` |
| `SEPARACAO` | `SEPARANDO` · `CONFERIDO` · `EMBALADO` |
| `MISTA` | qualquer um dos seis |

Subestado fora do conjunto do tipo de operação é erro de domínio → HTTP 409.
Em qualquer estado que não seja `EM_PREPARO`, `subestado` é nulo.

---

## 3. Tabela de transições

Esta é a fonte de verdade. **Toda combinação `(de, para)` ausente desta tabela é
inválida** e lança `TransicaoInvalidaException` → HTTP 409.

| # | De | Para | Gatilho | Guarda | Efeito |
|---|---|---|---|---|---|
| T01 | — | `RECEBIDO` | Cliente confirma por botão | Itens vendáveis · área atendida · invariantes de valor · regra do troco | Congela itens, taxa e área. `PedidoRecebidoV1` |
| T02 | `RECEBIDO` | `CONFIRMADO` | Estabelecimento aceita | Loja aberta **ou** aceite manual explícito · `ALTERAR_STATUS` | Compromisso. `PedidoConfirmadoV1` |
| T03 | `RECEBIDO` | `PAGO` | Webhook do PSP | Assinatura válida · `txid` correlacionado. **Marco 8** | Compromisso. `PedidoPagoV1` |
| T04 | `RECEBIDO` | `AGUARDANDO_ESTOQUE` | Aceite com item controlado | `∃ item.stockControlledSnapshot` — **falso no MVP** | Solicita reserva |
| T05 | `AGUARDANDO_ESTOQUE` | `CONFIRMADO` | Reserva confirmada | — | Compromisso |
| T06 | `AGUARDANDO_ESTOQUE` | `CANCELADO` | Reserva rejeitada | — | `ITEM_INDISPONIVEL` |
| T07 | `RECEBIDO` | `CANCELADO` | Estabelecimento recusa | Motivo obrigatório | `ESTABELECIMENTO_RECUSOU` |
| T08 | `RECEBIDO` | `CANCELADO` | Cliente desiste | — | `CLIENTE_DESISTIU` |
| T09 | `CONFIRMADO` | `EM_PREPARO` | Produção iniciada | — | Define subestado inicial |
| T10 | `PAGO` | `EM_PREPARO` | Produção iniciada | — | Define subestado inicial |
| T11 | `CONFIRMADO` | `CANCELADO` | Cancelado antes do preparo | Motivo e autor | Sem perda de insumo |
| T12 | `PAGO` | `CANCELADO` | Cancelado antes do preparo | Motivo e autor | **Dispara devolução** (H4.4) |
| T13 | `EM_PREPARO` | `EM_PREPARO` | Operador muda subestado | Subestado ∈ conjunto do `tipoDeOperacao` | Nenhum evento |
| T14 | `EM_PREPARO` | `PRONTO` | Produção concluída | — | **Pivô.** `PedidoProntoV1` |
| T15 | `EM_PREPARO` | `CANCELADO` | Cancelado durante o preparo | Motivo e autor | Perda parcial registrada |
| T16 | `PRONTO` | `SAIU_PARA_ENTREGA` | Entregador retirou na loja | `modalidade = ENTREGA` · entrega atribuída · entregador com **jornada aberta** | `PedidoSaiuParaEntregaV1` |
| T17 | `PRONTO` | `AGUARDANDO_CLIENTE` | Disponibilizado no balcão | `modalidade = RETIRADA` | — |
| T18 | `PRONTO` | `CANCELADO` | Cancelado após o pivô | Motivo e autor | **Perda total.** Não é compensação |
| T19 | `SAIU_PARA_ENTREGA` | `ENTREGUE` | Entrega confirmada | **`∃ Liquidacao`** (I6) | `PedidoEntregueV1` com a liquidação |
| T20 | `SAIU_PARA_ENTREGA` | `NAO_ENTREGUE` | Tentativa frustrada | Motivo obrigatório | — |
| T21 | `NAO_ENTREGUE` | `SAIU_PARA_ENTREGA` | Nova tentativa | — | — |
| T22 | `NAO_ENTREGUE` | `ENTREGUE` | Resolvido na hora | `∃ Liquidacao` | `PedidoEntregueV1` |
| T23 | `NAO_ENTREGUE` | `CANCELADO` | Desistência após tentativas | Motivo e autor | Perda total. Item retorna à loja |
| T24 | `AGUARDANDO_CLIENTE` | `RETIRADO` | Cliente retirou | **`∃ Liquidacao`** (I6) | `PedidoRetiradoV1` |
| T25 | `AGUARDANDO_CLIENTE` | `CANCELADO` | Cliente não apareceu | Motivo obrigatório | Perda total |

**Não existem** — e a ausência é deliberada:

- nenhuma transição **saindo** de `ENTREGUE`, `RETIRADO` ou `CANCELADO`;
- nenhuma de `SAIU_PARA_ENTREGA` de volta para `PRONTO`;
- nenhum atalho de `RECEBIDO` para `EM_PREPARO` — o compromisso é registrado
  mesmo quando o dono aceita e começa no mesmo segundo, porque é ele que separa
  "cliente pediu" de "a loja assumiu";
- nenhum atalho de `CONFIRMADO` para `PRONTO` — sem preparo registrado não há
  tempo de preparo mensurável;
- nenhuma transição para `ENTREGUE` ou `RETIRADO` sem liquidação. **É a regra
  mais importante desta tabela.** É o que separa este sistema do caderno.

### Como isto vira teste

A tabela é dado, não código. O teste é parametrizado sobre ela:

```java
// 1. toda transição da tabela é aceita quando a guarda é satisfeita
// 2. toda transição da tabela é REJEITADA quando a guarda é violada
// 3. todo par (de, para) FORA da tabela lança TransicaoInvalidaException
// 4. nenhum estado não-terminal fica sem saída  → alcançabilidade
// 5. todo estado é alcançável a partir de T01
// 6. nenhum pedido entra em AGUARDANDO_ESTOQUE nem em PAGO enquanto o MVP durar
```

O item 3 é o que costuma faltar. Testar só o caminho feliz deixa a máquina de
estados aberta: descobre-se em produção que `CANCELADO → SAIU_PARA_ENTREGA`
passava.

---

## 4. Invariantes do agregado

Verificadas na construção e em toda transição — não em teste de integração.

| # | Invariante | Onde quebra se não existir |
|---|---|---|
| I1 | `total == itemsSubtotal + deliveryFee + tip − discount` | Cobrança diverge do extrato |
| I2 | `itemsSubtotal == Σ item.subtotal` | Relatório de vendas não bate |
| I3 | Todo componente ≥ 0 · `discount ≤ itemsSubtotal + deliveryFee` | Total negativo |
| I4 | `metodoDeclarado = DINHEIRO ∧ trocoPara ≠ null → trocoPara ≥ total` | Entregador na porta sem troco possível (H5.2) |
| I5 | `total` imutável após `RECEBIDO`; correção só por `Ajuste` | Perde-se o valor que o cliente confirmou |
| I6 | `ENTREGUE` ou `RETIRADO` → `∃ Liquidacao` | O produto inteiro perde a razão de existir |
| I7 | Todos os itens do mesmo `estabelecimentoId` | Pedido multiloja, fora de escopo (ADR-004) |
| I8 | `estado = EM_PREPARO ↔ subestado ≠ null` | Subestado órfão na tela |
| I9 | `estado = CANCELADO → motivoCancelamento ≠ null` | "Cancelados" vira número sem diagnóstico |
| I10 | `modalidade = RETIRADA → deliveryFee = zero ∧ nomeAreaSnapshot = null` | Cobra-se entrega de quem foi buscar |
| I11 | Nenhum estado não-terminal sem transição de saída | Pedido preso para sempre (H4.4) |

**I7 é imposta em dois lugares, não só aqui.** O carrinho já recusa item de outro
estabelecimento ao ser acrescentado — mensagem clara, antes de o cliente montar
um pedido inteiro. A invariante do agregado é a segunda guarda, porque o
carrinho é estado externo e não se confia em validação que já passou (ADR-004).

---

## 5. Congelamento

No instante de `RECEBIDO`, o pedido copia — não referencia:

| Copiado | De | Se não fosse congelado |
|---|---|---|
| `nome`, `precoBase` do item | catálogo | Reajuste de preço reescreveria pedidos antigos |
| opções escolhidas: nome e acréscimo | catálogo | "Sem cebola" sumiria se a opção fosse removida |
| `stockControlledSnapshot` | catálogo | Marco 10 exigiria migration |
| `nomeAreaSnapshot`, `taxaSnapshot` | `merchant-service` | Reajuste da taxa do bairro mudaria o histórico |
| `enderecoTextual` | cliente | Cliente edita o endereço, o pedido antigo muda de destino |

Depois disso o pedido **não consulta mais** catálogo nem estabelecimento para
nada que afete valor. É o que torna a cobrança reconstruível meses depois.

O congelamento protege contra alteração no catálogo e no cadastro do cliente;
anonimização por pedido de exclusão é a única exceção, e é destrutiva no lugar
(ADR-013 §5).

---

## 6. Ajustes e valor efetivo

```
totalEfetivo = total + Σ ajustes.delta
```

`total` nunca é sobrescrito. Substituição de item, remoção, arredondamento por
falta de moeda e correção posterior são todos `Ajuste` — com tipo, delta, motivo
obrigatório, autor e momento.

**Nenhum relatório soma `total` para dizer quanto entrou.** Soma `totalEfetivo`,
ou melhor, soma `Liquidacao.valorEfetivo`. Isto vai ser esquecido; escreva o
teste que pega.

Substituição de item (H4.3, **marco 10** — a anotação "marco 5" no PRD está
velha, ver `catalogo.md` §6) é composta: um `Ajuste` de tipo `SUBSTITUICAO` com
o delta, e o item original **permanece na lista**, marcado. Não se apaga o que o
cliente pediu.

---

## 7. Liquidação vista pelo pedido

O pedido registra; o `settlement-service` apura. Do lado do pedido:

```
Liquidacao
├── metodoLiquidado      DINHEIRO | CARTAO | PIX | NAO_LIQUIDADO
├── valorEfetivo         Money  — zero quando NAO_LIQUIDADO
├── situacao             CONFIRMADA | AGUARDANDO_CONFIRMACAO
├── momento              timestamp
├── responsavelCustodia  ENTREGADOR | ESTABELECIMENTO | PLATAFORMA
├── registradoPor        usuário
├── referenciaExterna    txid do Pix, NSU do cartão | null
└── motivo               obrigatório quando NAO_LIQUIDADO
```

Três regras que não são óbvias:

1. **Pix só é `CONFIRMADA` por webhook do PSP.** Comprovante em imagem
   apresentado ao entregador não muda situação nenhuma (H5.3, invariante 6 do
   `CLAUDE.md`). Enquanto o webhook não chega, a liquidação é
   `AGUARDANDO_CONFIRMACAO` — e **isso não impede** T19. O pedido é entregue; a
   liquidação é que fica pendente.
2. **Pagamento parcial gera duas liquidações**, não uma com valor menor. Metade
   em dinheiro e metade no cartão é o caso real, e `Σ valorEfetivo` das
   liquidações confirmadas é o que se compara com `totalEfetivo`.
3. **`NAO_LIQUIDADO` não bloqueia a conclusão.** O pedido foi entregue e não foi
   pago — os dois fatos são verdadeiros e ambos precisam ficar registrados. A
   cobrança vira assunto do estabelecimento com o cliente, não um estado preso.

---

## 8. Eventos publicados

Todos com `correlationId` no envelope, todos via outbox na mesma transação da
mudança de estado (invariante 7 do `CLAUDE.md`).

| Evento | Transição | Consumidores previstos |
|---|---|---|
| `PedidoRecebidoV1` | T01 | `conversation` |
| `PedidoConfirmadoV1` | T02, T05 | `conversation` |
| `PedidoPagoV1` | T03 | `conversation`, `settlement` |
| `PedidoProntoV1` | T14 | `delivery`, `conversation` |
| `PedidoSaiuParaEntregaV1` | T16 | `conversation` |
| `PedidoEntregueV1` | T19, T22 | `settlement`, `conversation` |
| `PedidoRetiradoV1` | T24 | `settlement`, `conversation` |
| `PedidoCanceladoV1` | T06, T07, T08, T11, T12, T15, T18, T23, T25 | `delivery`, `settlement`, `conversation` |
| `LiquidacaoConfirmadaV1` | Webhook de Pix confirma | `settlement` |

`PedidoEntregueV1` e `PedidoRetiradoV1` **carregam a liquidação no payload**. O
`settlement-service` não consulta o `order-service` para apurar (invariante 8).

---

## 9. O que este documento deliberadamente não decide

- **Ordem de exibição da fila do painel.** É produto, não domínio.
- **Política de quantas tentativas de entrega.** É configuração por
  estabelecimento, não regra do agregado.
- **Numeração sequencial por loja** — reinicia diariamente? por ano? Precisa ser
  decidido antes do `order-service`, e não foi. **Pendência.**
- **Prazo para o Pix `AGUARDANDO_CONFIRMACAO` virar `NAO_LIQUIDADO`.** Depende
  do PSP escolhido; hoje fica pendente indefinidamente e aparece no fechamento.
- **Os controles comerciais contra abuso** citados em §2 — teto para cliente sem
  cadastro, bloqueio por reincidência. São regra de estabelecimento e ainda não
  têm dono. **Pendência antes do marco 3.**
