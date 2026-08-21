# ADR-009 — Modelo de valores do pedido

**Status:** Aceita — 16/08/2026
**Relacionada:** ADR-019 (cotação da entrega), ADR-018 (snapshot dos itens)

## Contexto

O domínio do pedido previa `Order`, `OrderItem` e `Money`, sem nenhuma
decomposição do valor cobrado. Ao mesmo tempo, o sistema precisa cobrar taxa de
entrega, e o blueprint determina que "o valor da cobrança vem do pedido e não de
um valor arbitrário enviado pelo cliente".

Sem decomposição explícita, três coisas quebram: o relatório de vendas produz um
número que não bate com o que foi cobrado; não há como auditar uma cobrança
passada; e qualquer mudança futura de política de frete reescreve o significado
de pedidos antigos.

Há ainda uma confusão comum a evitar: **o que o cliente paga pela entrega e o
que o entregador recebe não são o mesmo número.** A diferença é margem ou
subsídio da plataforma. Modelá-los como um campo só impede frete grátis, entrega
subsidiada e qualquer política de preço — e é irreversível depois que existirem
pedidos gravados.

## Decisão

### Decomposição

```
itemsSubtotal     Σ dos subtotais dos itens
+ deliveryFee     taxa de entrega cobrada do cliente
+ serviceFee      taxa da plataforma
+ tip             gorjeta ao entregador
− discount        cupom
──────────────
= total           único valor que o payment-service aceita cobrar
```

`serviceFee`, `tip` e `discount` existem como campo desde já, com `Money.zero()`,
sem regra implementada. Acrescentar coluna depois é barato; mudar o **significado
de `total`** depois que existem pedidos e relatórios não é.

`courierPayout` **não** pertence ao pedido. Vive no `delivery-service`, é
calculado quando a entrega é criada e aparece na oferta ao entregador. No MVP a
política é `courierPayout = deliveryFee`, com o campo separado desde o início.

### Tipo monetário

`Money` é value object imutável com `BigDecimal` de escala 2 e código de moeda,
persistido como `@Column(precision = 19, scale = 2)`. Toda operação usa
`RoundingMode.HALF_UP`. `double` e `float` são proibidos em toda a cadeia.

### Invariantes — verificadas na construção, não em teste

```
1.  total == itemsSubtotal + deliveryFee + serviceFee + tip − discount
2.  itemsSubtotal == Σ item.subtotal
3.  todos os componentes ≥ 0, e discount ≤ base descontável
```

### Regra de arredondamento

Arredonda-se ao formar **cada componente**; `total` é a soma de componentes já
arredondados, nunca um recálculo independente. É o que evita a soma da tela não
bater com o valor cobrado.

### Ponto geográfico congelado

`Order` guarda `pickupPoint` e `deliveryPoint` como `GeoSnapshot` — endereço
textual mais latitude e longitude em **valores primitivos imutáveis**, sem tipo
espacial e sem consultar o `geolocation-service` depois. Se o cliente editar o
endereço cadastrado, a rota do pedido antigo não muda.

## Consequências

**Positivas**

- A cobrança é auditável: dá para reconstruir qualquer pedido antigo.
- Frete grátis, subsídio e promoção passam a ser mudança de política, não
  migração de dados.
- O `payment-service` não pode ser enganado — cobra `order.totals.total`, e a
  invariante 1 torna essa exigência verificável.
- O relatório de vendas (R4) bate com o extrato.

**Negativas**

- Mais campos desde o início, a maioria zerada durante todo o MVP.
- Exige disciplina de arredondamento em todo cálculo; a invariante é o que a
  garante, mas ela precisa ser lembrada em cada novo componente de valor.

## Alternativas consideradas

- **Um campo `total` apenas, calculando o resto na leitura.** Rejeitada:
  impossível auditar cobrança passada, e mudança de política de frete reescreve
  o histórico.
- **`deliveryFee` e `courierPayout` como o mesmo campo.** Rejeitada: elimina
  qualquer política de preço de entrega e é irreversível.
- **`Money` como `long` em centavos.** Alternativa legítima e comum, que remove
  a discussão de escala. Rejeitada por legibilidade e por mapear pior em JPA e
  em JSON; o risco de precisão fica controlado pelo value object, que força
  escala 2 e arredondamento explícito. É escolha, não descuido.
