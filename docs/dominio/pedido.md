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
├── pagamento            momentoDeclarado: enum (ONLINE | NA_ENTREGA | NA_RETIRADA — ADR-009, não é carimbo de tempo), metodoDeclarado, trocoPara
├── valores              itemsSubtotal, deliveryFee, tip, discount, total
├── estado               estado, subestado, motivoCancelamento
├── ItemDoPedido    [n]  congelado — nome, precoBase, opções, stockControlledSnapshot
├── Ajuste          [n]  somente-inserção
├── Liquidacao      [n]  somente-inserção
└── Devolucao       [n]  somente-inserção — ADR-030
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
| T01 | — | `RECEBIDO` | Cliente confirma por botão | Itens vendáveis · área atendida · invariantes de valor · regra do troco · **pedido mínimo da modalidade** | Congela itens, taxa, área e desconto. `PedidoRecebidoV1` |
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

**A guarda "entregador com jornada aberta" de T16 é resolvida por consulta ao
`settlement`**, com cache curto invalidado por `JornadaAbertaV1` e
`JornadaFechadaV1`, e **falha fechada**: sem confirmação, não há despacho
(ADR-033). É o mesmo mecanismo da autorização contextual (ADR-011), pelo mesmo
motivo — dado consultado muitas vezes, que muda poucas, em que errar para o lado
permissivo quebra a invariante 1.

**T20 e T21 não publicam evento, e isso é decisão.** Ninguém precisa saber que
uma tentativa falhou: o `delivery` modela tentativa dentro da entrega, o
`conversation` avisa o cliente por outro caminho, e o `settlement` **pergunta**
em vez de escutar (ADR-032). Registrado em `contracts/eventos.md` §3.

**A guarda "área atendida" de T01 é respondida pela `DeliveryQuotePort`** — a
mesma chamada que produz `taxaSnapshot` (ADR-019, ADR-020). Ela é síncrona e
**não é cacheada**: a resposta é congelada no pedido, e taxa velha gravada é
preço errado que o cliente já confirmou (ADR-034).

**A guarda "loja aberta" de T02 vem da `OperacaoDoEstabelecimentoPort`**, esta
com cache curto invalidado por `ExpedienteAlteradoV1`, porque a resposta é usada
e descartada. Mesma porta responde o `tipoDeOperacao` que I8 valida. Falha
fechada nas duas — salvo o aceite manual explícito, que é o outro ramo da guarda
de T02 e não pergunta nada.

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
| I12 | `modalidade = RETIRADA → discount == descontoDeRetirada congelado` · `modalidade = ENTREGA → discount == zero` | Desconto prometido some no fechamento, ou entrega sai descontada sem motivo |
| I13 | `Σ Devolucao.valor ≤ Σ liquidações CONFIRMADAS` | Devolver mais do que entrou |
| I14 | `formaDeDevolucao = ESTORNO_PSP → referenciaExterna ≠ null` quando `situacao = EXECUTADA` | "Estornei" sem prova de que estornou |

A `Liquidacao` de I6 é o registro, dentro do `Pedido`, de que o dinheiro foi
recebido. Não confundir com o `Lancamento` de tipo `LIQUIDACAO_DE_ENTREGA` do
`settlement-service` (`liquidacao.md`), que é o reflexo dela na jornada do
entregador — e que a retirada não produz.

> `I12` vale enquanto `discount` tiver **origem única**. No dia em que existir
> cupom, ela é a primeira a ser revisitada — junto com a decisão de decompor o
> campo (ADR-024, consequências negativas).

> **Pedido mínimo é guarda de `T01`, não invariante do agregado.** A diferença
> importa: invariante vale para sempre, e um pedido criado sob um mínimo de
> R$ 20 continua válido depois de o comerciante subir o mínimo para R$ 30.
> Modelá-lo como invariante tornaria inválido, retroativamente, um pedido que
> nasceu certo. ADR-028

**I7 é imposta em dois lugares, não só aqui.** O rascunho da conversa recusa
item de outro estabelecimento por construção — a conversa é com **uma** loja, e
não há onde a segunda caberia (ADR-006 §2). A invariante do agregado é a segunda
guarda, porque o rascunho é estado de **outro serviço** e não se confia em
validação que já passou (ADR-004).

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
| `discount` (parcela de retirada) | `merchant-service` | Reajuste do desconto da loja mudaria pedidos passados |

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
obrigatório, autor e momento (`Instant`).

**Nenhum relatório soma `total` para dizer quanto entrou.** Soma `totalEfetivo`,
ou melhor, soma `Liquidacao.valorEfetivo`. Isto vai ser esquecido; escreva o
teste que pega.

Substituição de item (H4.3, **marco 10**) é composta: um `Ajuste` de tipo `SUBSTITUICAO` com
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
├── momento              Instant
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

### Devolução vista pelo pedido

Uma quarta situação, que só aparece depois: **entrou mais dinheiro do que o
pedido veio a valer.**

```
Σ liquidações CONFIRMADAS  >  totalEfetivo   →  há devolução devida
```

Gorjeta fica **fora** dessa conta. Ela é campo próprio (H5.2) e nunca foi
devida — foi dada. Somá-la produz devolução onde não há.

```
Devolucao
├── valor              Money — sempre positivo
├── origem             AJUSTE_POSTERIOR | CANCELAMENTO_DE_PEDIDO_PAGO
│                      | LIQUIDACAO_DUPLICADA | CONFIRMACAO_APOS_CANCELAMENTO
├── formaDeDevolucao   ESTORNO_PSP | FORA_DO_SISTEMA
├── situacao           DEVIDA | EXECUTADA | CANCELADA
├── referenciaExterna  id do estorno no PSP | null
├── motivo             obrigatório, sempre
└── registradaPor      usuário
```

**Devolução não é liquidação com sinal trocado**, e não é `Ajuste`. Ajuste muda o
que se **deve**; devolução registra o que precisa **voltar** — e quando um causa
o outro, os dois existem ao mesmo tempo. A ADR-030 tem o raciocínio inteiro.

No marco 4 **nenhuma** devolução é executada pelo sistema: a plataforma não
custodiou o valor (P1), então `formaDeDevolucao = FORA_DO_SISTEMA` é o caso
normal e `EXECUTADA` significa que alguém marcou. `ESTORNO_PSP` entra no marco 8.

Detalhe dos quatro caminhos em [`pagamento.md`](pagamento.md) §6.

---

## 8. Eventos

Todos com `correlationId` no envelope, todos via outbox na mesma transação da
mudança de estado (invariante 7 do `CLAUDE.md`).

| Evento | Transição | Consumidores previstos |
|---|---|---|
| `PedidoRecebidoV1` | T01 | `conversation` |
| `PedidoConfirmadoV1` | T02, T05 | `conversation` |
| `PedidoPagoV1` | T03 | `conversation` |
| `PedidoProntoV1` | T14 | `delivery`, `conversation` |
| `PedidoSaiuParaEntregaV1` | T16 | `conversation` |
| `PedidoEntregueV1` | T19, T22 | `settlement`, `conversation` |
| `PedidoRetiradoV1` | T24 | `conversation` |
| `PedidoCanceladoV1` | T06, T07, T08, T11, T12, T15, T18, T23, T25 | `delivery`, `payment`, `conversation` |
| `DevolucaoDevidaV1` | Nasce uma `Devolucao` | `payment` (só quando `ESTORNO_PSP`), `settlement` |

`PedidoEntregueV1` e `PedidoRetiradoV1` **carregam a liquidação no payload**. O
`settlement-service` não consulta o `order-service` para apurar (invariante 8).

### Consumidos

| Evento | Origem | O que o pedido faz |
|---|---|---|
| `LiquidacaoConfirmadaV1` | `payment` | Muda a `Liquidacao` para `CONFIRMADA`. Se o pedido estiver `CANCELADO`, confirma assim mesmo e gera `Devolucao` — ADR-030 §6 |
| `CobrancaExpiradaV1` | `payment` | Registra que a cobrança venceu. **Não** muda estado do pedido |
| `EstornoExecutadoV1` | `payment` | Muda a `Devolucao` para `EXECUTADA` — marco 8 |
| `ConfiguracaoOperacionalAlteradaV1` | `merchant` | Modalidades, métodos e regra de troco aplicáveis ao pedido |
| `JornadaAbertaV1` | `settlement` | **Invalida cache** de jornada aberta — não projeta |
| `JornadaFechadaV1` | `settlement` | Idem |
| `ExpedienteAlteradoV1` | `merchant` | **Invalida cache** de operação da loja — não projeta |

`AreasDeEntregaAlteradasV1` **não** é consumido aqui, e é decisão: sem cache de
cotação, não há entrada a invalidar. ADR-034 §1.

Idempotência por `liquidacaoId`, `cobrancaId` ou `devolucaoId`, conforme o
evento — invariante 7 do `CLAUDE.md`.

**Estes são os únicos.** T04 e T05 sugerem um evento de reserva vindo do
`catalog`, e ele não existe: `AGUARDANDO_ESTOQUE` é inalcançável no MVP
(`pedido.md` §2) e reserva só existe no modo `QUANTITATIVO`, no marco 10
(`catalogo.md` §6). A linha entra na tabela quando o marco 10 entrar.

---

## 9. O que este documento deliberadamente não decide

- **Ordem de exibição da fila do painel.** É produto, não domínio.
- **Política de quantas tentativas de entrega.** É configuração por
  estabelecimento, não regra do agregado.
- **Numeração sequencial por loja** — reinicia diariamente? por ano? Precisa ser
  decidido antes do `order-service`, e não foi. **Pendência.**
  A ADR-025 fixa **qual** dia — o `diaOperacional` do estabelecimento —, mas não
  decide se reinicia.
- **Prazo para o Pix `AGUARDANDO_CONFIRMACAO` virar `NAO_LIQUIDADO`.** Depende
  do PSP escolhido; hoje fica pendente indefinidamente e aparece no fechamento.
- **Os controles comerciais contra abuso** citados em §2 — teto para cliente sem
  cadastro, bloqueio por reincidência. São regra de estabelecimento e ainda não
  têm dono. **Pendência antes do marco 3.**
