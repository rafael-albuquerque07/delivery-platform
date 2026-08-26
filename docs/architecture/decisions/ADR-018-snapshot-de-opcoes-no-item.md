# ADR-018 — Snapshot de opções no item do pedido e cotação pelo catálogo

**Status:** Aceita — 16/08/2026
**Relacionada:** ADR-006 (carrinho no Redis), ADR-009 (valores), ADR-017 (MongoDB)

> Esta ADR **refina** a especificação que a originou. Lá, o `order-service`
> replicava as regras de `minSelect`/`maxSelect` para validar a seleção. Aqui a
> validação volta para o catálogo, que é o dono da regra. O motivo está em
> "Alternativas consideradas".

## Contexto

Em delivery de comida, o preço do produto quase nunca é o preço do produto — é o
preço-base mais as escolhas. Pizza grande com borda recheada custa mais que a
mesma pizza pequena, e a diferença está nas opções, não no produto.

A definição de congelamento do item previa `productId`, `productNameSnapshot`,
`unitPriceSnapshot` e `quantity` — **sem as opções**. Com isso, o total do
pedido estaria errado por construção, exatamente no caso mais comum.

Some-se o carrinho no Redis (ADR-006), que guarda apenas identificadores: o
preço só é resolvido no checkout. E a regra de que preço e valor são sempre
recalculados no backend.

## Decisão

### Modelo no catálogo

```
Product
├── basePrice: Money
├── stockControlled: boolean
└── optionGroups: [
      { id, name: "Tamanho",     minSelect: 1, maxSelect: 1, options: [...] },
      { id, name: "Adicionais",  minSelect: 0, maxSelect: 5, options: [...] }
    ]

Option: { id, name, priceDelta: Money }
```

`minSelect`/`maxSelect` por grupo expressam "escolha obrigatória de tamanho" e
"até 5 adicionais" sem código especial.

### O catálogo cotiza; o pedido congela

O `catalog-service` expõe uma operação **interna** de cotação:

```
POST /internal/catalog/quote
  { merchantId, items: [ { productId, quantity, optionIds[] } ] }
→ 200 { lines: [ { productId, productName, unitBasePrice,
                   options: [ { optionId, groupName, optionName, priceDelta } ],
                   unitTotal, subtotal, stockControlled } ],
        itemsSubtotal, pricedAt }
→ 400 se a seleção viola minSelect/maxSelect ou a opção não pertence ao produto
→ 409 se algum produto não está ACTIVE ou é de outro estabelecimento
```

O catálogo é o dono das regras de produto — validação e precificação ficam com
ele. O `order-service` faz **uma** chamada (a mesma que já faria só para buscar
preços), recebe linhas validadas e precificadas, e congela o resultado.

### Snapshot no pedido

```
OrderItem
├── productId, productNameSnapshot, unitBasePriceSnapshot
├── stockControlledSnapshot: boolean      → consumido pela ADR-010
├── quantity
├── selectedOptions: [OrderItemOption]
├── unitTotal   = unitBasePriceSnapshot + Σ priceDeltaSnapshot
└── subtotal    = unitTotal × quantity

OrderItemOption
├── optionId
├── groupNameSnapshot     "Tamanho"
├── optionNameSnapshot    "Grande"
└── priceDeltaSnapshot    15,00
```

Tabela nova: `order_item_options`.

Congelar o **nome** do grupo e da opção — não só o preço — é o que permite
reimprimir um pedido de seis meses atrás com o texto que o cliente viu, mesmo
que o comerciante tenha renomeado tudo desde então.

### O que o `order-service` continua validando

A cotação não o exime de tudo. Ele valida o que é **dele**: um único
estabelecimento por carrinho, quantidade ≥ 1, e que o carrinho pertence ao
cliente autenticado. E ignora qualquer preço ou nome que venha no request.

### Divergência entre carrinho e checkout

- A resposta do carrinho carrega `pricedAt` e os preços exibidos.
- No checkout o cliente reenvia o `expectedTotal` que viu.
- Divergiu → **409 `PRICE_CHANGED`** com a nova decomposição completa; o pedido
  não é criado e o cliente reconfirma.
- Produto saiu do ar → **409 `ITEM_UNAVAILABLE`** com a lista do que caiu.

Nunca se cobra a diferença em silêncio.

## Consequências

**Positivas**

- O total fica correto por construção, incluindo o caso comum.
- Pedido antigo é reimprimível com o texto original.
- Impossível o cliente escolher o próprio preço ou pular uma escolha
  obrigatória.
- A regra de produto vive num lugar só; o `order-service` não duplica
  `minSelect`/`maxSelect` nem envelhece uma cópia dela.
- `stockControlledSnapshot` chega de graça na mesma resposta, e é o que permite
  a ADR-010 decidir o caminho da Saga sem outra chamada.

**Negativas**

- Uma tabela a mais (`order_item_options`) e um relacionamento a mais no
  agregado do pedido.
- O `catalog-service` ganha um endpoint desenhado a partir da necessidade do
  `order-service` — acoplamento de contrato, ainda que não de código.
- O checkout depende da disponibilidade do catálogo. Já dependia para buscar
  preços; a cotação não acrescenta uma chamada, mas concentra mais coisa nela.
  Mitigação: timeout e circuit breaker no `CatalogPort`, mais cache curto no
  Redis para o catálogo público.

## Alternativas consideradas

- **`order-service` valida `minSelect`/`maxSelect` por conta própria.** Era a
  proposta original. Rejeitada: duplica a regra de produto em dois serviços, e
  a cópia envelhece — o catálogo evolui o modelo de opções e o pedido continua
  validando pela versão antiga. Como o `order-service` já precisa chamar o
  catálogo para obter preços, mover a validação para a mesma chamada **não custa
  round-trip nenhum**. Foi o que motivou o refinamento desta ADR.
- **Carrinho guarda o preço.** Rejeitada: o cliente passa a controlar o valor
  cobrado.
- **Congelar apenas o preço das opções, sem os nomes.** Rejeitada: o pedido
  deixa de ser reimprimível, e suporte ao cliente vira arqueologia.

## Emenda de 26/08/2026 — o carrinho citado aqui não existe

O texto acima cita a ADR-006 e a descreve como "carrinho no Redis, que guarda
apenas identificadores". Quando isto foi escrito, a ADR-006 **não existia** — a
citação apoiava-se numa decisão nunca tomada.

Ela existe agora, e decidiu o contrário do que a citação presume: **não há
carrinho.** O que há é `rascunhoDePedido`, campo do agregado `Conversa`, em
MongoDB.

**O que esta decisão exige continua verdadeiro**, e é mais fraco do que a
citação sugere: o rascunho **não guarda preço**. Guarda identificadores; o preço
vem da cotação, no fechamento. Onde ele mora nunca importou aqui — importa que
o cliente não controle o valor, que é a alternativa rejeitada no fim deste
documento.

Leia "o carrinho no Redis (ADR-006)" como "o rascunho da conversa (ADR-006)".

### E o fluxo de checkout descrito acima também é da v1.0

A seção "O que o `order-service` continua validando" e a seguinte descrevem um
fluxo de requisição e resposta com **cliente autenticado** dono de um carrinho,
que reenvia no checkout o `expectedTotal` que viu. Esse cliente não existe:
P3 põe o comerciante como cliente do produto, e o consumidor é um número de
telefone numa conversa — sem login e sem sessão.

**A decisão desta ADR não depende disso** e continua inteira: opções congeladas
no item, cotação pelo catálogo, preço nunca vindo do carrinho. O que muda é
*quem* pergunta e *como* o cliente diz o que viu.

| | Vigente |
|---|---|
| Onde o pedido é montado | `Conversa.rascunhoDePedido` — ADR-006 |
| O que o cliente vê | o resumo antes da confirmação — `conversa.md` §5 |
| Como ele diz o que viu | o botão carrega identificador do rascunho **e uma marca da cotação** |
| Cotação envelhecida | reconfirma com os números novos — `PRICE_CHANGED` |

O `expectedTotal` reenviado pelo cliente **não é mais o mecanismo**, e a
substituição é ganho: a marca da cotação é referência, não valor. Nada de
dinheiro volta do cliente, e a invariante 2 do `CLAUDE.md` deixa de precisar ser
lembrada nesse ponto — não há valor chegando para ser ignorado.

As três validações que a seção lista continuam corretas, com uma ficando de
graça:

- **um único estabelecimento** — agora **estrutural**: a conversa é com uma
  loja (ADR-006 §2);
- **quantidade ≥ 1** — continua, no `order`, em T01;
- **posse pelo cliente autenticado** — **sem objeto**; o equivalente é a
  conversa pertencer ao contato, que o `conversation-service` já garante.

E `pricedAt` continua existindo: é a marca da cotação, com outro nome.
