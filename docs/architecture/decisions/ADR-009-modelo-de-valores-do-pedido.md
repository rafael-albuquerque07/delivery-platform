# ADR-009 — Modelo de valores do pedido

**Status:** Aceita — 16/08/2026 · **emendada em 21/08/2026** pela arquitetura v1.1
**Relacionada:** ADR-018 (snapshot dos itens), ADR-020 (taxa por área), ADR-022 (remuneração do entregador)
**Substitui internamente:** a referência à ADR-019, revogada
**Premissas do PRD que sustentam esta decisão:** P1, P2, P5

> **Emenda v1.1 — o que mudou e por quê.** A versão original desta ADR foi
> escrita sob a premissa de que a plataforma custodia o dinheiro e cobra online
> antes do preparo. O adendo de escopo derrubou essa premissa (P1). Quatro
> consequências entram aqui: troco passa a ser modelo de domínio; método
> **declarado** e método **liquidado** viram campos distintos; correção de valor
> vira lançamento de ajuste em lista somente-inserção; e `serviceFee`,
> `courierPayout` e o ponto geográfico saem do pedido. O texto abaixo é o
> vigente — a versão anterior está no histórico do Git.

## Contexto

O sistema precisa cobrar o valor certo, provar depois qual foi, e sobreviver ao
fato de que **na maioria dos pedidos o dinheiro nunca passa pela plataforma**: o
cliente paga ao entregador, em dinheiro, cartão ou Pix, na porta de casa.

Isso muda o problema. Não basta decompor o valor cobrado — é preciso separar
três coisas que o desenho ingênuo funde num campo só:

| O que é | Quando existe | Quem sabe |
|---|---|---|
| O que o pedido **vale** | No fechamento do pedido | O sistema, a partir do catálogo |
| O que o cliente **disse** que pagaria | No fechamento do pedido | O cliente |
| O que **de fato** foi pago, como e a quem | Na entrega, ou nunca | O entregador |

Fundir os dois últimos é o erro caro. O cliente pede em dinheiro, o entregador
não tem troco, e o pagamento sai no cartão da maquininha. Se houver um campo só,
a informação de que era preciso levar troco desaparece — e com ela some a
medição de acerto do preparo de caixa, que é justamente o que este produto
promete resolver.

Há ainda uma confusão residual da v1.0 a desfazer: **o que o cliente paga pela
entrega não tem relação com o que o entregador recebe.** Na v1.0 isso era margem
da plataforma. Na v1.1 é outra coisa: o entregador é da casa (P2) e sua
remuneração vem do **vínculo** com o estabelecimento — diária, comissão, taxa
fixa — apurada na jornada, não no pedido. Ver ADR-022.

## Decisão

### Decomposição do valor

```
itemsSubtotal     Σ dos subtotais dos itens, cada um já arredondado
+ deliveryFee     taxa da área nomeada, congelada (ADR-020)
+ tip             gorjeta, campo próprio e explícito
− discount        cupom ou desconto concedido
──────────────
= total           valor devido pelo pedido, imutável após o fechamento
```

**`serviceFee` sai do modelo.** Ele existia para comissão sobre a venda, que a
premissa P3 e o modelo de negócio por assinatura eliminaram. Um campo zerado
para uma cobrança que o produto se comprometeu a não fazer não é preparação, é
convite: um dia alguém o preenche. Comissão sobre venda está na lista de
não-objetivos do `CLAUDE.md`, e o modelo de dados deve concordar com ela.

**`courierPayout` sai do pedido.** Não é `deliveryFee` menos coisa alguma. Ver
ADR-022.

### Tipo monetário

`Money` é value object imutável com `BigDecimal` de escala 2 e código de moeda,
persistido como `@Column(precision = 19, scale = 2)`. Toda operação usa
`RoundingMode.HALF_UP`. `double` e `float` são proibidos em toda a cadeia.

### Arredondamento

Arredonda-se ao formar **cada componente**; `total` é a soma de componentes já
arredondados, nunca um recálculo independente. É o que evita a soma exibida na
tela não bater com o valor cobrado na porta.

### Método declarado e método liquidado

São campos distintos, em objetos distintos, preenchidos em momentos distintos.

```
Pedido
├── modalidade          ENTREGA | RETIRADA
├── momentoDeclarado    ONLINE | NA_ENTREGA | NA_RETIRADA
├── metodoDeclarado     DINHEIRO | CARTAO | PIX
└── trocoPara           Money | null      — só faz sentido com DINHEIRO

Liquidacao                                — 0..n por pedido, insert-only
├── metodoLiquidado     DINHEIRO | CARTAO | PIX | NAO_LIQUIDADO
├── valorEfetivo        Money             — o que a loja de fato recebeu
├── momento             timestamp
├── responsavelCustodia entregador | estabelecimento | plataforma
├── registradoPor       usuário
└── referenciaExterna   txid do Pix, NSU do cartão | null
```

`metodoDeclarado ≠ metodoLiquidado` é o **caso normal**, não a exceção. Nenhuma
validação pode exigir que coincidam.

`NAO_LIQUIDADO` é registro válido e explícito, com valor zero e motivo. Ausência
de registro **não é**: nenhuma entrega ou retirada conclui sem uma `Liquidacao`
gravada. Esta é a invariante 1 do `CLAUDE.md`, e é aqui que ela se materializa.

### Troco

```
trocoPara      informado pelo cliente     — "tenho uma nota de 50"
trocoDevido    = trocoPara − total        — derivado, nunca gravado por cima
```

Três regras:

1. `trocoPara < total` **impede o fechamento do pedido**. Não se aceita um
   pedido cujo pagamento declarado já se sabe insuficiente; volta-se ao cliente
   com a pergunta.
2. `trocoPara` nulo com `metodoDeclarado = DINHEIRO` significa "não precisa de
   troco" — valor exato. É diferente de "não informou".
3. Sobra por falta de moeda — o entregador não tem os R$ 0,30 — é registrada
   como **ajuste de arredondamento**, com esse nome. Nunca como gorjeta. Gorjeta
   é decisão do cliente e tem campo próprio; tratar as duas como a mesma coisa
   corrompe tanto o extrato do entregador quanto a medição de troco.

### Ajustes: correção é lançamento, não edição

Quando um item é substituído (H4.3), removido, ou quando se corrige um valor
depois do fechamento, **nada é sobrescrito**:

```
Ajuste                                    — lista somente-inserção
├── tipo       SUBSTITUICAO | REMOCAO | ARREDONDAMENTO | CORRECAO
├── delta      Money                      — pode ser negativo
├── motivo     texto obrigatório
├── autor      usuário que autorizou
└── momento    timestamp

totalEfetivo = total + Σ ajustes.delta    — derivado, nunca gravado
```

`total` permanece exatamente como foi fechado, para sempre. É o que permite
responder "quanto este pedido custava quando o cliente confirmou" seis meses
depois — e é o que impede que uma correção silenciosa apague uma divergência de
caixa que precisava aparecer.

### Congelamento do destino

O pedido congela `enderecoTextual`, `nomeAreaSnapshot` e `taxaSnapshot` no
fechamento (ADR-020).

**O `GeoSnapshot` com latitude e longitude sai do modelo.** A premissa P5 fixa a
taxa por área nomeada, não por distância; não há distância a calcular, e
coordenada exata é dado pessoal que o `CLAUDE.md` proíbe em log. Guardar um
campo que não alimenta nenhuma regra e cria obrigação de LGPD é custo puro.

### Invariantes — verificadas na construção, não em teste

```
1.  total == itemsSubtotal + deliveryFee + tip − discount
2.  itemsSubtotal == Σ item.subtotal
3.  todos os componentes ≥ 0, e discount ≤ itemsSubtotal + deliveryFee
4.  metodoDeclarado == DINHEIRO ∧ trocoPara != null  →  trocoPara ≥ total
5.  total é imutável após o fechamento; correção só por Ajuste
6.  estado terminal (ENTREGUE, RETIRADO)  →  existe ≥ 1 Liquidacao
```

## Consequências

**Positivas**

- A cobrança é auditável e reconstruível meses depois, com a versão original
  intacta e o histórico de correções ao lado.
- A divergência entre declarado e liquidado vira **dado**, e com ele a medição
  de preparo de caixa que o produto promete.
- O troco deixa de ser conta de cabeça e passa a ser cálculo verificável.
- O modelo de dados concorda com os não-objetivos: não há onde lançar comissão
  sobre venda porque o campo não existe.
- Substituição de item deixa de destruir a informação do pedido original.

**Negativas**

- `Liquidacao` como entidade separada custa uma tabela, um relacionamento e uma
  consulta a mais em todo lugar que hoje leria um campo. É o preço de a premissa
  P1 ser verdadeira.
- `totalEfetivo` derivado significa que **nenhum relatório pode somar `total`**
  achando que soma o que entrou. Isso vai ser esquecido pelo menos uma vez;
  precisa de teste que pegue.
- Ajuste como lista somente-inserção cresce sem limite em pedidos muito
  corrigidos. Aceito: são poucos, e a alternativa é perder o histórico.
- A regra 4 rejeita pedido no fechamento, o que exige uma volta ao cliente no
  canal de conversa — mais um turno, e turno tem custo (H8.3).

## Alternativas consideradas

- **Um campo `metodoPagamento` apenas.** Rejeitada. É o desenho intuitivo e o
  errado: perde a informação de que era preciso levar troco, que é exatamente o
  insumo do fechamento de caixa. Foi o erro da v1.0.
- **Sobrescrever `total` na substituição de item.** Rejeitada: apaga o valor que
  o cliente confirmou, e com ele a possibilidade de explicar uma divergência.
- **Troco como campo livre de texto no pedido** ("levar troco pra 50").
  Rejeitada: não calcula, não valida, não entra em nenhuma apuração — e é o que
  o caderno já faz.
- **Manter `serviceFee` zerado "por precaução".** Rejeitada: campo disponível é
  campo que um dia é usado, e o produto se comprometeu publicamente a não cobrar
  percentual da venda. Reintroduzir depois é uma migration; reverter uma
  cobrança indevida é uma crise.
- **`Money` como `long` em centavos.** Alternativa legítima e comum, que remove
  a discussão de escala. Rejeitada por legibilidade e por mapear pior em JPA e
  em JSON; o risco de precisão fica controlado pelo value object, que força
  escala 2 e arredondamento explícito. É escolha, não descuido.

## Emenda de 26/08/2026 — a taxa cobrada passa a ser a taxa congelada

O modelo decidido aqui tem `deliveryFee` no bloco de valores. O `pedido.md` §6
congela `taxaSnapshot`, vindo do `merchant-service`, com a justificativa de que
"reajuste da taxa do bairro mudaria o histórico".

**Eram dois campos para a mesma quantia, sem invariante nenhuma os
relacionando** — o I1 somava o primeiro enquanto o §6 congelava o segundo.
Congelava-se um campo e cobrava-se do outro.

`deliveryFee` sai do modelo. O I1 soma `taxaSnapshot` diretamente. A
"Decomposição do valor" logo no início desta decisão — a que nomeia
`itemsSubtotal`, `deliveryFee`, `tip` e `discount` — descreve o modelo como foi
tomado e não se reescreve; o que muda é o registrado abaixo.

### Por que um campo e não dois com uma regra

Com dois campos, `deliveryFee == taxaSnapshot` seria invariante a testar para
sempre e a violar por descuido. Com um, a divergência não tem onde acontecer —
mesmo movimento do I7 depois da ADR-006: a guarda deixou de precisar de código.

**E hoje não existe caso em que os dois difiram.** Não há promoção no produto: o
I12 põe `desconto == zero` na entrega, e o desconto tem origem única, que é a
retirada. Dois campos guardavam uma divergência que nada produzia.

Quando promoção existir, ela entra por **`desconto`**, e o I12 muda — que é
exatamente o que a prosa depois dele já antecipa ao dizer que ele vale *enquanto
o desconto tiver origem única*. O caminho está aberto e é o certo: promoção é
desconto; a cotação congelada continua sendo o que a área custava.

### O que o I10 ganha

Antes, ele prendia `deliveryFee` e `nomeAreaSnapshot` na retirada e deixava
`taxaSnapshot` livre — um pedido de retirada podia carregar área nula e taxa
congelada de R$ 7,50, e nada proibia.

Na retirada, `taxaSnapshot` é **zero, não nulo**: o I1 é aritmética e não ganha
caso especial. A distinção continua no par — zero com `nomeAreaSnapshot = null`
é *não houve área*; zero com área nomeada seria *a área é grátis*.

### E o congelamento passa a proteger o que se cobra

A justificativa do §6 era verdadeira sobre um campo que o total não usava. Agora
é verdadeira sobre o campo que o cliente paga.

### Idioma

Pela ADR-035: `itemsSubtotal` → `subtotalDosItens`, `tip` → `gorjeta`,
`discount` → `desconto`, `stockControlledSnapshot` → `estoqueControladoSnapshot`
— o conceito fica, boolean deliberado e mais estreito que o `modoDeControle`
(`catalogo.md:243`); só o idioma muda. `total` e `subtotal` ficam — mesma
palavra nos dois idiomas. `taxaSnapshot` e `nomeAreaSnapshot` seguem a segunda
camada.

Os renomes entram agora, e não no marco 3 como a ADR-035 §5 adia os outros: a
§5 adia renome que custa o mesmo depois, e estas linhas estão sendo editadas de
qualquer jeito pelo colapso do campo.

### Onde isto repercute

`pedido.md` §1, I1, I2, I3, I10, I12, a prosa do I12 e a tabela do §6;
**ADR-028**, cuja guarda de pedido mínimo é escrita sobre `itemsSubtotal`;
ADR-022 e ADR-024, que citam o campo antigo e ganharam nota.
