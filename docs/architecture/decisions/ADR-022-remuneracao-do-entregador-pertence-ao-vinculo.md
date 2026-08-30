# ADR-022 — A remuneração do entregador pertence ao vínculo, não ao pedido

**Status:** Aceita — 21/08/2026
**Relacionada:** ADR-009 (valores do pedido), ADR-020 (taxa por área)
**Premissas do PRD que sustentam esta decisão:** P1, P2
**Detalhada em:** `docs/dominio/liquidacao.md`

## Contexto

A ADR-009, na versão v1.0, dizia:

> `courierPayout` não pertence ao pedido. Vive no `delivery-service`, é
> calculado quando a entrega é criada e aparece na oferta ao entregador. No MVP
> a política é `courierPayout = deliveryFee`.

Aquilo fazia sentido num marketplace: o entregador é autônomo, aceita ou recusa
uma oferta, e o que ele ganha naquela corrida é uma função do que o cliente
pagou de frete. A plataforma fica com a diferença.

A premissa P2 derrubou o desenho: **o entregador pertence ao estabelecimento**.
Jorge é da casa. Recebe diária, ou comissão, ou os dois, combinados com a Marli
quando foi contratado — e o mesmo Jorge pode atender a pizzaria da Marli às
terças e o mercadinho do Sérgio aos sábados, com regras diferentes em cada um
(H6.1). Nada disso tem relação com a taxa que o cliente pagou pelo bairro.

Uma entrega de R$ 6 no Centro e uma de R$ 12 em Boa Viagem rendem ao Jorge
exatamente a mesma comissão, porque foi isso que ele combinou. `courierPayout =
deliveryFee` não é uma simplificação do MVP — é uma afirmação falsa sobre o
negócio.

## Decisão

### A remuneração vive no vínculo

O `merchant-service` mantém o vínculo entre entregador e estabelecimento, e é
nele que a remuneração é definida:

```
VinculoEntregador
├── entregadorId, estabelecimentoId
├── modeloDeRemuneracao   DIARIA | COMISSAO | DIARIA_MAIS_COMISSAO | TAXA_FIXA
├── valorDiaria           Money
├── comissaoPorEntrega    Money
├── taxaFixaPorEntrega    Money
└── ativo                 boolean
```

O mesmo entregador tem **um vínculo por estabelecimento**, independentes.

### O pedido não sabe quanto o entregador ganha

`courierPayout` **sai do modelo do pedido**, e não é substituído por nada
equivalente. O pedido conhece `deliveryFee` — o que o cliente paga — e mais
nada sobre remuneração.

Isto não é economia de campo: é acoplamento que se recusa a criar. Enquanto o
pedido não souber quanto o entregador ganha, é impossível escrever a regra
errada de que um depende do outro.

### A apuração é por jornada, não por entrega

Diária não cabe num pedido. Comissão até caberia, mas apurá-la pedido a pedido
significa somar N linhas para responder a pergunta que sempre é feita por turno.
A unidade de apuração é a **jornada** (`docs/dominio/liquidacao.md`), e o
`vinculoSnapshot` é congelado na abertura dela.

### Consequência para o `delivery-service`

A tela do entregador mostra **endereço, itens, valor a receber do cliente e
troco a levar** (H6.2). Não mostra "você ganha R$ X nesta corrida", porque na
maioria dos modelos essa frase não tem significado — a diária não se divide por
entrega.

## Consequências

**Positivas**

- O modelo concorda com o negócio: quem define quanto o Jorge ganha é a Marli,
  no vínculo, e não uma fórmula sobre a taxa de bairro.
- Frete grátis deixa de ser um problema. Com `courierPayout = deliveryFee`, taxa
  zero significaria entregador trabalhando de graça; com a remuneração no
  vínculo, a promoção do comerciante não toca o pagamento de ninguém.
- Dois vínculos com regras diferentes para o mesmo entregador ficam triviais.
- Fecha a porta para comissão sobre venda: não há campo no pedido onde ela
  caberia.

**Negativas**

- A remuneração só é conhecível **por jornada**, o que torna impossível
  responder "quanto custou esta entrega" sem um rateio arbitrário da diária.
  Aceito: a pergunta certa é "quanto custou o turno", e essa tem resposta exata.
- Exige o vínculo cadastrado antes da primeira entrega. Mitigação: um vínculo
  com todos os valores zerados é válido e leva dez segundos — o extrato sai com
  crédito zero, e a conferência de caixa, que é o que importa no primeiro dia,
  funciona igual.
- `TAXA_FIXA` e `COMISSAO` são numericamente idênticos por entrega. Mantidos
  separados porque significam coisas diferentes no acordo e o comerciante usa os
  dois nomes; unificá-los economizaria um campo e custaria a conversa.

## Alternativas consideradas

- **Manter `courierPayout` no pedido, calculado a partir de `deliveryFee`.**
  Rejeitada: afirma que a remuneração depende da taxa de bairro, o que é falso
  sob P2, e quebra em taxa zero.
- **`courierPayout` no pedido, copiado do vínculo no momento do despacho.**
  Mais defensável — congelaria a comissão junto com o pedido. Rejeitada porque
  não resolve diária (que não é por entrega) e duplica no pedido um dado cuja
  fonte de verdade é a jornada, criando dois lugares para consertar quando
  divergirem. O congelamento que importa já existe em `vinculoSnapshot`.
- **Deixar a remuneração fora do sistema, calculada pelo comerciante.**
  Rejeitada: é exatamente o papel que o produto promete substituir. O acerto com
  o entregador é metade do fechamento de expediente.

## Nota de 26/08/2026 — `deliveryFee` chama-se `taxaSnapshot`

A política rejeitada aqui — `courierPayout = deliveryFee` — continua rejeitada
pelas mesmas razões. O campo passou a se chamar `taxaSnapshot` e é o único do
modelo (**ADR-009** emendada): não há mais um valor cobrado separado do valor
congelado.
