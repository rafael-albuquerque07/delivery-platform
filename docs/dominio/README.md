# Domínio — regras de negócio vigentes

Um documento por agregado. Cada um responde **qual é a regra hoje**: fronteira
do agregado, invariantes, tabela de transições, fórmulas de apuração.

| Documento | Serviço | Cobre |
|---|---|---|
| [`pedido.md`](pedido.md) | `order-service` | Agregado Pedido, máquina de estados, congelamento, ajustes, liquidação registrada |
| [`liquidacao.md`](liquidacao.md) | `settlement-service` | Jornada do entregador, conferência de caixa, extrato, fechamento imutável |
| [`estabelecimento.md`](estabelecimento.md) | `merchant-service` | Agregados, autorização contextual, horário e pausa, áreas e taxas, política de troco, vínculos |
| [`catalogo.md`](catalogo.md) | `catalog-service` | Produto e opções, publicação, disponibilidade qualitativa, reativação por expediente, cotação |
| [`entrega.md`](entrega.md) | `delivery-service` | Atribuição direta e rodízio, posição do entregador, custódia, retorno registrado |
| [`conversa.md`](conversa.md) | `conversation-service` | Roteamento por número, modos, limites da interpretação, escalonamento, custo, LGPD |
| [`pagamento.md`](pagamento.md) | `payment-service` | Cobrança Pix, webhook e assinatura, confirmação, devolução e divergência com o provedor |

## Como ler junto com as ADRs

| | ADR | `docs/dominio/` |
|---|---|---|
| Responde | *por que* decidimos assim | *qual é a regra* agora |
| Muda | raramente; emenda com data | sempre que a regra muda |
| Guarda | alternativas rejeitadas e custos aceitos | invariantes, tabelas, fórmulas |
| Se divergir do código | o código está errado, ou a ADR precisa de emenda | é defeito — corrija na mesma alteração |

Uma ADR narra uma escolha feita num dia. Este diretório narra o sistema como ele
deve estar funcionando neste momento.

## Regra de manutenção

**Tabela aqui, teste lá.** Toda tabela de transição e toda fórmula deste
diretório existe para virar teste parametrizado. Se uma regra aqui não tem teste
correspondente, ela é intenção — e intenção não sobrevive a três meses de
manutenção.

Ao alterar uma regra: altere o documento, altere o teste, altere o código. Na
mesma alteração. Documento que descreve o sistema de dois meses atrás é pior que
documento nenhum, porque tem autoridade.
