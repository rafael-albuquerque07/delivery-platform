# ADR-086 — O marco 3 em fatias

- **Status:** aceita
- **Data:** 17/11/2026
- **Rodada:** P-5
- **Relacionada:** ADR-083 (a primeira porta do pedido é o painel), ADR-018 (a cotação), ADR-045 e
  ADR-054 (a credencial entre serviços), ADR-046 (o fechamento de expediente), ADR-081 §3 (evento
  sem consumidor), ADR-085 (as ideias de plataforma), ADR-011, emenda de 19/10/2026 (a permissão
  nova e o membro que já existia), o `pedido.md` e o `estabelecimento.md` §3

## Contexto

**O marco 3 é o primeiro com código no `order-service`.** O PRD §10 diz que ele entrega a H4.1, a
H4.4 e a H4.5, e a coluna é *"Receber e acompanhar pedidos numa tela"*. A P-4 mediu, em
17/11/2026, o que ele encontra:

| Fato | Reverifica com |
|---|---|
| O `order` tem um arquivo Java, a `*Application`, e nenhuma migration | `find backend/services/order-service/src/main -name "*.java"` e `find backend/services/order-service/src/main/resources/db/migration -name "*.sql"` |
| Não há contrato OpenAPI do `order` | `ls contracts/openapi/` |
| O `catalog` não tem rota interna; a cotação da ADR-018 não existe | `grep -rn 'Mapping("/internal' backend/services/catalog-service/src/main --include=*.java` — vazio; no `merchant`, o mesmo comando acha dois controladores |
| A `OperacaoDoEstabelecimentoPort` está declarada no `estabelecimento.md` e não tem código | `grep -rn "interface OperacaoDoEstabelecimentoPort" backend --include=*.java` — vazio; com `AutorizacaoComercialPort`, o mesmo comando acha uma |
| Dos sete eventos que o `order` consome no `contracts/eventos.md`, um é produzido: o `ExpedienteAlteradoV1` | `find backend/services -path "*/src/main/*" -name "*AlteradoV1.java"` — dois, o `ExpedienteAlteradoV1` e o `VinculoAlteradoV1` |
| `ENTREGUE` e `RETIRADO` exigem liquidação (I6), que é do marco 4 | `grep -n "^. I6 " docs/dominio/pedido.md` |

**E o Rafael tomou quatro decisões em 17/11/2026**, que esta ADR escreve: vizinhos primeiro;
evento só com consumidor; o alerta de pedido novo por consulta periódica; e o destino de três
gatilhos da ADR-085.

## Decisão

> **O marco 3 se constrói em sete fatias, nesta ordem. Os vizinhos nascem antes do `Pedido`, para
> que cada fatia tenha o que chamar quando nascer.**

1. **A operação da loja, no `merchant`.** Uma rota interna que responde o que a
   `OperacaoDoEstabelecimentoPort` do `estabelecimento.md` declara — se a loja está aberta, a
   operação, os métodos por modalidade, a política de troco, o pedido mínimo, o desconto de
   retirada — e a taxa da área para um endereço (a `DeliveryQuotePort` do `pedido.md`). Responde
   sobre o portador do token (ADR-045).
2. **A cotação, no `catalog`.** `POST /internal/catalog/quote`, como a ADR-018 a desenha: valida a
   seleção e precifica, e o pedido congela. **No marco 3, quem a chama tem portador** — o atendente
   —, então a contradição da ADR-054 não aparece aqui: ela continua aberta para o 7a. A permissão
   que a cotação exige, a rodada da cotação decide.
3. **A permissão de lançar pedido, no `merchant`.** Um valor novo do `Permissao`, que o
   `ADMINISTRADOR` recebe por `EnumSet.allOf`; o convite do front oferece; o `estabelecimento.md`
   a marca como acréscimo ao PRD, como a `CONFIGURAR_LOJA` e a `GERENCIAR_JORNADA`. A
   `CONFIGURAR_LOJA` não alcançou quem era membro antes dela (ADR-011, emenda de 19/10/2026); a
   rodada da permissão decide como a nova alcança.
4. **O `Pedido`, no `order`.** O agregado, os estados e as transições que não dependem de `PAGO`,
   de estoque, de entrega nem de liquidação — T01, T02, T07, T08, T09, T11, T13, T14, T15, T17,
   T18 e T25, pela leitura da P-4 —, as invariantes, o congelamento (§5 do `pedido.md`), a
   migration, **a rota de lançar** (H4.5), o contrato OpenAPI, e o `order` subindo na pilha. **A
   mutação no CI entra aqui** (ADR-085).
5. **A fila e as transições, no `order`.** A fila por status com o mais urgente primeiro, os
   subestados pelo tipo de operação, cancelar com causa, e a exibição para a produção (H4.1 e H4.4).
6. **As telas.** Lançar pedido com o total discriminado antes (H4.5), a fila com consulta periódica
   e alerta sonoro e visual (H4.1), e as ações de cada estado.
7. **A passagem humana do marco 3**, critério por critério, como a skill
   `metodo-delivery-platform` manda em `fechar-marco.md`.

### 2. Evento só com consumidor

O marco 3 não publica eventos do pedido. Cada um nasce na rodada em que o primeiro consumidor
ganha código, e o outbox do `order` com ele. A regra é a armadilha *"Vai escrever peça de
infraestrutura para um marco distante"* do `CLAUDE.md`, aplicada a evento pela ADR-081 §3.

### 3. O alerta é por consulta periódica

O painel pergunta à fila a cada poucos segundos; não há conexão aberta. O intervalo, a fatia 6
decide.

### 4. No marco 3, nenhum pedido termina com sucesso

`ENTREGUE` e `RETIRADO` exigem liquidação (I6), que é do marco 4. O pedido de entrega para em
`PRONTO`, e o de retirada em `AGUARDANDO_CLIENTE`. A H4.4 — nenhum estado sem saída — vale assim
mesmo: os dois têm o cancelamento (T18, T25). **A coluna do marco 3 é "receber e acompanhar", e é
isso que ele entrega.**

### 5. O destino das linhas do razão que disparam com o marco 3

| Linha | Destino |
|---|---|
| 5 | não dispara no marco 3 — o `order` não consome evento: a guarda da T02 é consulta |
| 22 | **lida na fatia 4**, com a ADR-046 na mão: é ela que decide entre as opções da ADR-046 |
| 33 | **paga na fatia 4** — o `order` sobe na pilha |
| 97 | **lida na fatia 4** — dispara só se o `order` ganhar consumidor no marco 3 |
| 119 | **rearmada**: o primeiro lojista pagante |
| 120 | **paga na fatia 4** |
| 121 | **rearmada** para o gatilho que o limite de taxa do gateway já tem: o primeiro ambiente exposto à internet, nas linhas 16 e 17 do razão (ADR-012 e ADR-044) |
| 122 | não dispara no marco 3 — sem consumidor no `order` |

## Consequências

**Cada fatia é uma rodada, ou mais.** O `order` nasce sem eventos e sem outbox, e o 7a os encontra
por fazer. A cotação nasce com portador, e o 7a reabre a ADR-054.

## Alternativas consideradas

**O pedido primeiro, com os vizinhos depois.** **Recusada pelo Rafael:** a T01 nasceria sem ter o
que chamar.

**Publicar os eventos desde já.** **Recusada:** evento sem consumidor.

**Aviso empurrado ao painel.** **Recusado:** exigiria decidir a conexão aberta atrás do gateway
antes de haver um pedido.

## Gatilho escrito

**O primeiro lojista pagante, para o histórico de alteração.** Aí nasce o histórico — quem, quando,
antes e depois —, que a ADR-085 esperava da T01.
