# Marco 3 — receber e acompanhar pedidos

**A coluna do PRD §10:** *"Receber e acompanhar pedidos numa tela"* · **as histórias:** H4.1, H4.4 e
H4.5, e o critério da H1.3 que entra no marco 3.

**Como esta tabela se lê:** a skill `metodo-delivery-platform`, em `fechar-marco.md`. A coluna
**fatias** diz que fatias da ADR-086 servem o critério; uma fatia que é **peça** dá ao critério uma
parte, e não o completa. Fatia 1 medida na M3-1, sobre `d4fb2ec` (ADR-087). Fatia 2 medida na M3-2, sobre `b275220` (ADR-088).

**Estado:** 14 critérios; 1 anotado; 13 contam; nenhum completo; a fatia 1 é peça em 5 deles, e a fatia 2 em 2.

| # | Critério | Fatias | Rota | Tela | Teste | Exercida |
|---|---|---|---|---|---|---|
| 4.1a | Fila por status, com o mais urgente primeiro | | — | — | — | — |
| 4.1b | Alerta sonoro e visual para pedido novo | | — | — | — | — |
| 4.1c | Subestados coerentes com o tipo de operação: na fila, em produção, finalizando (cozinha); separando, conferido, embalado (balcão) | 1 | — | — | peça (fatia 1): `OperacaoAtualControllerIT`, caso 1 — o tipo de operação | — |
| 4.1d | Impressão ou exibição do pedido para a produção | | — | — | — | — |
| 4.4a | Todo cancelamento registra a causa | | — | — | — | — |
| 4.4b | Pedido pago que é cancelado dispara devolução do valor | *Entra a partir do marco 8* (ADR-030) — fora da conta | | | | |
| 4.4c | Pedido não pode ficar sem saída possível em nenhum estado | | — | — | — | — |
| 4.5a | O pedido lançado nasce pela mesma transição que o do canal — a T01 do `pedido.md` —, com as mesmas guardas: itens vendáveis, área atendida na entrega, invariantes de valor e regra do troco | 1 · 2 | — | — | peça (fatia 1): `CondicoesDoPedidoControllerIT`, casos 6 a 9 — a área atendida — e 5 — a regra do troco · peça (fatia 2): `CotacaoControllerIT`, casos 9 a 14 — os itens vendáveis | — |
| 4.5b | O pedido mínimo da modalidade vale também para o pedido lançado, sem aceite manual por cima dele (ADR-028 §4) | 1 | — | — | peça (fatia 1): `CondicoesDoPedidoControllerIT`, caso 5 — o mínimo da modalidade | — |
| 4.5c | Quem confirma é o atendente, por ato explícito no painel, no lugar do botão do cliente | | — | — | — | — |
| 4.5d | O total aparece discriminado antes de lançar — itens, taxa de entrega e desconto | 1 · 2 | — | — | peça (fatia 1): `CondicoesDoPedidoControllerIT`, casos 5, 6 e 10 — a taxa e o desconto · peça (fatia 2): `CotacaoControllerIT`, casos 1 a 3 — os itens, com as opções e os preços | — |
| 4.5e | O pedido entra na mesma fila do painel, com a origem `BALCAO` — a do que chegou por telefone e a do que chegou no balcão | | — | — | — | — |
| 4.5f | Lançar exige uma permissão própria, que nenhuma das permissões de H2.1 cobre | | — | — | — | — |
| 1.3b | Endereço fora da área é recusado no momento do pedido, com mensagem clara | 1 | — | — | peça (fatia 1): `CondicoesDoPedidoControllerIT`, caso 8 — a não atendida, com a lista dos bairros | — |
