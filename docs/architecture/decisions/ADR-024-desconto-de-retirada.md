# ADR-024 — Desconto de retirada, não preço por modalidade

**Status:** Aceita — 23/08/2026
**Relacionada:** ADR-009 (modelo de valores), ADR-018 (cotação), ADR-020 (taxa por área), ADR-022 (remuneração do entregador)
**Premissa do PRD:** "Modalidade — entrega ou retirada no balcão"
**Detalhada em:** `docs/dominio/pedido.md` I12 · `docs/dominio/estabelecimento.md` §2
**Resolve:** a pendência registrada em `docs/dominio/catalogo.md` §10

## Contexto

`catalogo.md` registrou a pergunta desde a v1.1: *muita loja cobra mais na
entrega que no balcão, e o modelo tem um `precoBase` só.* Ficou marcada com prazo
— antes do marco 2 — e sem dono.

São **duas** modalidades, não três. O PRD é explícito: entrega ou retirada no
balcão. Não há consumo no local, não há comanda de mesa, não há taxa de serviço.
Isso encolhe a pergunta: ela é sobre **um** par.

### O que já diferencia as duas modalidades hoje

Antes de acrescentar qualquer coisa, vale ver o que existe:

| Mecanismo | Onde | Efeito na retirada |
|---|---|---|
| Taxa de entrega por área | ADR-020 | Não é cobrada |
| Invariante I10 | `pedido.md` | `deliveryFee = zero ∧ nomeAreaSnapshot = null` |
| Componente `discount` | ADR-009 | Existe, com `Money.zero()`, sem regra |

**Retirada já sai mais barata.** A pergunta real não é "como diferenciar", é "o
comerciante precisa de mais alguma coisa além da taxa".

### O motivo econômico do cardápio duplo não existe aqui

Preço diferente por modalidade é universal no iFood porque o marketplace tira
12–27% **do item**. O comerciante embute a comissão no preço de entrega para não
absorvê-la.

Neste sistema não há comissão sobre o item. O custo do comerciante com a entrega
é a remuneração do entregador (ADR-022 — diária, comissão por entrega, taxa fixa
por entrega), e quem paga a entrega é o cliente, na taxa por área (ADR-020). **A
força que torna o cardápio duplo obrigatório lá não atua aqui.** O que atua é o
hábito de quem vem de lá — e hábito é motivo para explicar, não para modelar.

O que sobra de legítimo é o outro lado: **incentivar a retirada** para poupar o
custo do entregador. Isso é desconto, não preço.

## Decisão

**Preço não varia por modalidade. O estabelecimento configura um desconto de
retirada em valor fixo, aplicado no fechamento.**

```
Estabelecimento
└── descontoDeRetirada   Money — zero é válido e é o padrão
```

Quatro consequências diretas:

1. **`cotar` continua sem receber modalidade.** A assinatura da ADR-018 fica como
   está: `cotar(estabelecimentoId, [ { produtoId, quantidade, opcoesEscolhidas } ])`.
2. **O desconto entra em `discount`** (ADR-009), no congelamento em T01, junto
   com a taxa e a área. Nenhum campo novo no pedido.
3. **O cliente vê a linha.** "Desconto de retirada — R$ 5,00" aparece no total,
   nomeada. Não é preço menor: é abatimento visível.
4. **Um número para a loja inteira.** Não por produto, não por categoria.

O produto **não ganha dimensão de modalidade** — nem de preço, nem de
disponibilidade. Todo item vendável é vendável nas duas.

### Composição, com o exemplo

Pedido de R$ 48,00 em itens, bairro com taxa de R$ 7,00, loja com desconto de
retirada de R$ 5,00:

| | Entrega | Retirada |
|---|---|---|
| `itemsSubtotal` | 48,00 | 48,00 |
| `deliveryFee` | 7,00 | 0,00 — I10 |
| `discount` | 0,00 | 5,00 |
| **`total`** | **55,00** | **43,00** |

R$ 12,00 de diferença, e o cliente consegue apontar de onde vem cada real. Num
modelo de preço por modalidade ele veria "R$ 55" e "R$ 43" sem decomposição — e a
invariante 2 do `CLAUDE.md` existe exatamente contra isso.

### Por que valor fixo e não percentual

Porque o custo que ele compensa é fixo. A remuneração por entrega da ADR-022 é
comissão mais taxa fixa **por entrega**, não uma fração do pedido: o entregador
custa o mesmo levando R$ 30 ou R$ 300.

E porque é o que o comerciante sabe responder. Perguntado quanto quer dar de
desconto para quem vem buscar, ele diz "cinco reais". Ninguém diz "quatro vírgula
sete por cento".

### Por que não preço por produto — o argumento que decide

Se o preço depende da modalidade, **a modalidade tem que ser conhecida antes do
primeiro número dito.**

No WhatsApp isso inverte a ordem natural da conversa. O cliente diz "quero uma
calabresa grande"; o sistema não pode responder "R$ 48" sem antes perguntar se
ele vem buscar. A alternativa é cotar e recotar quando a modalidade aparecer — que
é a surpresa no valor que o produto existe para não cometer.

"Entrega ou retirada?" é pergunta de fechamento. Preço por modalidade a
transformaria em pergunta de abertura, e o custo disso é medido em turnos de
conversa (H8.3), que é a métrica que este produto tenta baixar.

## Consequências

**Positivas**

- **Nada muda no catálogo.** O agregado `Produto`, a fórmula
  `precoUnitario = precoBase + Σ acrescimo` e a vendabilidade derivada ficam
  intactos.
- **`cotar` fica intacto**, e com ele o contrato que o `order-service` e o
  `conversation-service` consomem.
- **A conversa fica intacta.** A modalidade continua podendo aparecer no fim.
- **O desconto é auditável.** É uma linha nomeada do total, congelada em T01, e
  aparece no relatório do comerciante como "quanto dei para incentivar retirada" —
  que é a pergunta que ele realmente vai fazer.
- **Um campo, uma tela, nenhuma manutenção recorrente.** Reajuste de cardápio não
  vira reajuste em dobro.

**Negativas**

- **Não atende o comerciante que quer o cardápio com dois preços**, e ele vai
  pedir — sobretudo se veio do iFood, onde o cardápio dele já tem. A resposta é
  "não, e a taxa mais o desconto cobrem o mesmo efeito". É uma resposta que pode
  custar venda, e é o custo mais alto desta decisão.
- **Um número só para a loja inteira.** Não dá para descontar a pizza e não
  descontar o refrigerante. Se isso doer de verdade, a saída é granularidade por
  categoria — que exige categoria como entidade de primeira classe no catálogo,
  o que ela hoje não é.
- **Não existe pedido mínimo no modelo.** A invariante I3 permite
  `discount ≤ itemsSubtotal + deliveryFee`, então um desconto de R$ 5 num pedido
  de R$ 10 de retirada é legal e sai por R$ 5. `estabelecimento.md` não tem
  `pedidoMinimo`, e esta ADR não o cria. **Pendência nomeada, sem prazo** — vale
  quando houver comerciante real reclamando, não antes.
- **`discount` passa a ter uma origem, e vai ter duas.** A ADR-009 documenta o
  campo como "cupom". Enquanto a origem for única, somar num campo só é seguro.
  No dia em que existir cupom, ou o campo se decompõe ou o comerciante deixa de
  conseguir separar "quanto dei de desconto de retirada" de "quanto queimei em
  promoção". **Quem for escrever cupom revisita esta ADR primeiro.**
- **Fecha, por ora, a porta do preço por modalidade** — e reabri-la custa o
  contrato do `cotar` e a sequência da conversa, não a migração do banco. Ver a
  seção seguinte, porque este ponto foi mal descrito antes.

### Sobre o prazo, que estava mal justificado

`catalogo.md` dizia que a resposta era necessária antes do marco 2 porque
"depois vira migration". **A parte da migração é a barata**: acrescentar um preço
por modalidade a um catálogo publicado é um Mongock que popula o campo novo com o
`precoBase` existente, e os pedidos congelados não perdem sentido porque cada um
sabe a própria modalidade.

O caro é outro: uma vez que o `cotar` tenha contrato publicado e a conversa esteja
construída para cotar antes de saber a modalidade, mudar isso toca a interpretação,
o rascunho, o botão de confirmação e a ordem das perguntas. **É custo de desenho,
não de dado.** O prazo continua valendo — decida antes de escrever o `cotar`, e o
`cotar` é marco 2 —, mas pelo motivo certo.

## Alternativas consideradas

- **Nenhuma diferença além da taxa (opção A).** A mais honesta das rejeitadas: a
  taxa por área já faz a retirada sair mais barata, e I10 já a zera. Rejeitada
  por tirar do comerciante a única alavanca para *incentivar* a retirada — a taxa
  é do cliente e varia por bairro; o desconto é decisão dele e vale para todos.
  A diferença entre "sai mais barato porque não tem taxa" e "eu te dou R$ 5 se
  você vier buscar" é a diferença entre um fato e uma oferta.
- **Preço por modalidade em cada produto (opção C).** O desenho que o comerciante
  vindo do iFood espera, e o mais flexível. Rejeitada por três motivos, em ordem
  de peso: obriga a modalidade a ser conhecida antes de qualquer preço, invertendo
  a ordem da conversa; dobra o trabalho de cadastro e de reajuste, que numa loja de
  oitenta itens ninguém faz direito depois do primeiro mês; e é o mecanismo pelo
  qual a taxa de entrega some dentro do preço do item — "entrega grátis" com o
  frete embutido —, que é o oposto do que o modelo de valores da ADR-009 foi
  desenhado para garantir.
- **Preço base com override opcional por modalidade (opção D).** Aparentemente
  mais barata que C porque a maioria dos produtos não precisaria do segundo
  número. Rejeitada porque **o custo estrutural é idêntico**: o `cotar` muda igual,
  a conversa muda igual, e a única economia é de digitação. Some-se a isso uma
  regra de fallback que precisa estar explícita em todo lugar que lê preço, e o
  saldo fica pior que o de C.
- **Percentual em vez de valor fixo.** Se autolimita — nunca excede o subtotal — e
  dispensaria a discussão do pedido mínimo. Rejeitada porque erra nas duas pontas:
  5% de um pedido de R$ 40 são R$ 2 e não cobrem o entregador; os mesmos 5% de um
  pedido de R$ 300 são R$ 15 e dão de graça o que não custou nada a mais. O custo
  compensado é fixo, e o desconto que o compensa deve ser fixo.
- **Desconto por categoria do cardápio.** O meio-termo entre "a loja toda" e "cada
  produto". Rejeitada por ora: exige que categoria seja entidade de primeira
  classe no catálogo, com dono, invariante e evento próprios. É onde a
  complexidade se esconde, e não há caso real pedindo.
- **`modalidadesDisponiveis` no produto.** Sopa que não viaja bem, promoção "retire
  e leve dois". Rejeitada por falta de caso: sem comerciante real, o exemplo é
  inventado, e a saída existe hoje — despublicar o item, ou tratar na conversa.
  Barata de acrescentar depois, pela mesma conta de migração desta ADR.

## Emenda que esta decisão provoca

`docs/referencia/PRD-Plataforma-Delivery.pdf` lista o que se configura num
estabelecimento — tipo de operação, modalidades aceitas, métodos de pagamento por
modalidade. **`descontoDeRetirada` não está nessa lista**, e passa a fazer parte
dela.

O PDF não se reescreve. A lista vigente é a de `docs/dominio/estabelecimento.md`.
