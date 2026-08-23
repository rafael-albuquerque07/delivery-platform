# ADR-004 — Um pedido pertence a exatamente um estabelecimento

**Status:** Aceita — 16/08/2026 · **formalizada em 23/08/2026**
**Relacionada:** ADR-011 (autorização), ADR-012 (roteamento), ADR-018 (cotação), ADR-020 (taxa por área), ADR-022 (remuneração)
**Premissa do PRD:** P3 — o comerciante é o cliente; o consumidor é usuário
**Detalhada em:** `docs/dominio/pedido.md` I7
**Em vigor desde o primeiro commit** — esta ADR registra o porquê, que faltava

## Contexto

Num marketplace, a pergunta "posso pedir de duas lojas no mesmo carrinho" é de
produto, e a resposta costuma ser um desenho de subpedidos com entregas
separadas.

Aqui a pergunta é diferente, porque a premissa P3 é diferente: **não há
marketplace.** O consumidor não navega entre lojas, não busca, não compara. Ele
conversa com **uma** pizzaria, no número **dela**, sobre o cardápio **dela**.

Um pedido multiloja não é um recurso difícil neste sistema — é um objeto que não
tem significado nele.

## Decisão

**Todo pedido tem um `estabelecimentoId`, e todos os seus itens pertencem a esse
estabelecimento.** Carrinho com itens de duas lojas é estado inválido, recusado
no fechamento e, antes disso, impedido no próprio carrinho.

### Por que isto sustenta quase tudo

Não é uma restrição isolada. É a premissa de que cinco outras decisões dependem —
e sem ela, cada uma vira um problema de decomposição:

| Sem esta decisão, quem responde? | Onde a resposta mora hoje |
|---|---|
| De qual tabela de áreas sai a taxa de entrega? | ADR-020 — a do estabelecimento |
| Qual entregador leva, se cada um pertence a uma loja? | ADR-022 — o do estabelecimento |
| Em qual jornada a liquidação entra? | `liquidacao.md` — entregador × estabelecimento |
| Contra qual vínculo a permissão é verificada? | ADR-011 — usuário × estabelecimento |
| Qual `merchantId` vai no caminho da URL? | ADR-012 — um, sempre na mesma posição |

Cada linha dessa tabela seria uma decisão nova, com decomposição própria, se um
pedido pudesse atravessar duas lojas. É o tipo de premissa que se paga uma vez e
rende em todo lugar.

### Onde a regra é imposta

Em dois lugares, e os dois são necessários:

- **No carrinho**, ao acrescentar item — recusa cedo, com mensagem clara, antes
  de o cliente montar um pedido inteiro que vai ser rejeitado.
- **No fechamento**, como invariante do agregado (`pedido.md` I7) — porque o
  carrinho é estado externo e não se confia em validação que já passou.

### O que o cliente faz quando quer duas lojas

Faz **dois pedidos**, e paga **duas taxas de entrega**. É o que já acontece hoje
com dois telefonemas, e o sistema não finge o contrário.

## Consequências

**Positivas**

- Cinco decisões downstream ficam simples em vez de decompostas.
- `estabelecimentoId` é chave de partição natural: toda consulta filtra por ele,
  todo índice começa por ele, e o isolamento entre lojas fica estrutural.
- A autorização contextual (ADR-011) tem um alvo único por requisição.
- Relatório por loja é a leitura natural, não uma agregação com rateio.

**Negativas**

- **"Pizza de um lugar e sobremesa de outro" é impossível** no mesmo carrinho.
  Aceito: é recurso de marketplace, e este produto declaradamente não é um.
- **Duas taxas de entrega** quando o cliente quer duas lojas. Honesto, e é o
  custo real de duas entregas — mas é pior que a experiência de um marketplace, e
  vale saber disso em vez de descobrir por reclamação.
- **A regra precisa de guarda no carrinho, não só no pedido.** É fácil implementar
  só no fechamento e produzir uma experiência ruim: o cliente monta tudo e leva
  recusa no fim.
- **Fecha a porta para virar marketplace.** Se a premissa P3 cair, esta ADR cai
  junto — e junto com ela as cinco da tabela acima. É a decisão mais estrutural
  do sistema, e por isso a mais cara de reverter.

## Alternativas consideradas

- **Pedido multiloja com subpedidos**, um por estabelecimento. O desenho que
  marketplaces usam. Rejeitada: reintroduz o marketplace pela porta dos fundos, e
  transforma cada uma das cinco perguntas da tabela numa decisão de decomposição —
  qual subpedido tem a taxa, qual entregador leva qual parte, como fecha a
  jornada de dois entregadores de lojas diferentes.
- **Aceitar o carrinho misto e dividir automaticamente no fechamento.** A opção
  com melhor experiência aparente, e a mais perigosa. Rejeitada: esconde do
  cliente que existem duas entregas e duas taxas até o total aparecer. Surpresa
  no valor é a pior coisa que um sistema de pedido pode fazer, e a invariante 2
  do `CLAUDE.md` existe para o mesmo tipo de problema.
- **Permitir itens de outra loja e cobrar uma taxa só.** Rejeitada: alguém paga a
  segunda entrega, e nesse desenho é o comerciante, sem ter sido consultado.
