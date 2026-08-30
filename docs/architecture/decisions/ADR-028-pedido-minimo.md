# ADR-028 — Pedido mínimo por modalidade, sobre o subtotal dos itens

**Status:** Aceita — 24/08/2026
**Fecha a consequência assumida da:** ADR-024 (desconto de retirada)
**Relacionada:** ADR-009 (modelo de valores), ADR-020 (taxa por área)
**Detalhada em:** `docs/dominio/estabelecimento.md` §4 · `docs/dominio/pedido.md` T01
**Sem prazo** — mas entra antes do marco 3, junto do fechamento do pedido

## Contexto

A ADR-024 nomeou isto como consequência negativa e não resolveu:

> **Não existe pedido mínimo no modelo.** A invariante I3 permite
> `discount ≤ itemsSubtotal + deliveryFee`, então um desconto de R$ 5 num pedido
> de R$ 10 de retirada é legal e sai por R$ 5.

O caso concreto: a loja oferece R$ 5 de desconto para quem vem buscar, alguém
pede um refrigerante de R$ 10, e leva por R$ 5. Nada no sistema impede — e nada
deveria impedir, porque foi o comerciante que configurou o desconto.

O que falta não é uma trava contra o desconto. É a coisa que toda pizzaria já
tem escrita no cardápio e o sistema não modela: **pedido mínimo**.

## Decisão

### 1. É por modalidade, e é configuração do estabelecimento

```
pedidoMinimoPorModalidade : Map<Modalidade, Money>

ENTREGA  → R$ 25,00
RETIRADA → R$ 0,00        zero = sem mínimo
```

Matriz, não valor único, pelo mesmo motivo de `metodosPorModalidade` já ser
matriz: mínimo para entrega e nenhum para retirada é a configuração comum, e é
justamente ela que o valor único não expressa.

**Zero é valor válido e significa "sem mínimo"** — não é ausência de
configuração. Mesma distinção que a ADR-020 fez para a taxa de área.

### 2. O mínimo é sobre `subtotalDosItens`. Nunca sobre o `total`

É a decisão que importa desta ADR, e a que se erra por descuido.

```
subtotalDosItens ≥ pedidoMinimoPorModalidade[modalidade]
```

Se o mínimo fosse sobre o `total`, a **taxa de entrega ajudaria o cliente a
atingi-lo**. Um mínimo de R$ 25 com taxa de R$ 9 viraria, na prática, um mínimo
de R$ 16 de comida — e num bairro mais caro, de R$ 13. O critério passaria a
depender de onde o cliente mora, o que ninguém quis e ninguém entenderia.

Pela mesma razão o desconto de retirada não conta: ele **abate** o que o cliente
paga e não muda o que ele comprou. Mínimo é sobre a compra.

```
Pedido de R$ 22 em itens, entrega no bairro de R$ 9, mínimo de R$ 25

  sobre subtotalDosItens 22 < 25  →  RECUSADO           ✓ correto
  sobre total           31 ≥ 25  →  aceito             ✗ o frete pagou o mínimo
```

### 3. Recusa no fechamento, com o número na mensagem

A verificação é guarda de `T01` em `pedido.md`, ao lado das que já existem —
itens vendáveis, área atendida, regra do troco.

A mensagem diz **quanto falta**, não que o pedido é inválido:

> "O pedido mínimo para entrega é R$ 25,00. Faltam R$ 3,00."

Mesma regra da recusa por troco em `estabelecimento.md` §4: a recusa que diz o
número resolve, a que diz "pedido inválido" faz o cliente ir embora. E no canal
de conversa, uma recusa sem número custa turnos — e turno custa dinheiro.

### 4. Não existe aceite manual por cima do mínimo

Diferente do aceite fora do horário, que `T02` permite com ato explícito de quem
tem `ALTERAR_STATUS`, o mínimo **não tem exceção pelo painel**.

O motivo é que os dois casos são diferentes. A loja fechada é uma condição do
estabelecimento, e o dono pode decidir atender assim mesmo. O pedido mínimo é
condição do pedido, e ele nem chega a existir: `T01` é a transição que o cria.

Se o comerciante quer atender um pedido abaixo do mínimo, ele baixa o mínimo, ou
o cliente acrescenta um item. Uma exceção manual aqui criaria um caminho para
pedido existir violando a guarda que o criou, e isso é o tipo de porta que
aparece depois em relatório sem explicação.

## Consequências

**Positivas**

- Fecha a consequência que a ADR-024 nomeou, e fecha pelo lado certo: o problema
  não era o desconto, era a ausência de mínimo.
- A escolha do `subtotalDosItens` impede a distorção em que o bairro do cliente
  altera o mínimo efetivo.
- É a regra que a loja já tem escrita no cardápio, então o comerciante não
  precisa aprender conceito nenhum — só digitar o número que ele já usa.
- Zero como "sem mínimo" mantém a configuração opcional sem campo nulo.

**Negativas**

- **Mais um campo por modalidade no cadastro**, e um campo que o comerciante pode
  configurar errado — mínimo alto demais recusa venda em silêncio, e o sintoma
  aparece como "ninguém pede aqui".
- **A recusa acontece no fim do fluxo**, depois de o cliente montar o pedido. O
  certo seria avisar no começo da conversa, mas o valor só existe quando há
  itens. Mitigação: o canal informa o mínimo ao abrir o cardápio, e o
  `conversation-service` pode avisar quando o carrinho ainda está longe — nada
  disso é regra de domínio, é apresentação.
- **Não há aceite manual**, e vai haver o dia em que o dono queria atender. A
  saída é baixar o mínimo, que leva dez segundos e afeta os próximos pedidos —
  não aquele.
- **O mínimo não impede o pedido de R$ 5 quando o mínimo é zero.** A loja que
  configurar zero e der R$ 5 de desconto continua vendendo refrigerante por R$ 5.
  É configuração dela, e o sistema não protege comerciante da própria política.

## Alternativas consideradas

- **Não modelar; deixar como está.** Rejeitada: é o estado que a ADR-024 já
  registrou como buraco, e é a única das consequências negativas dela que tinha
  solução barata.
- **Mínimo sobre o `total`.** Rejeitada pelo §2 — faz a taxa de entrega e o
  bairro do cliente alterarem o mínimo efetivo.
- **Valor único por estabelecimento**, sem separar por modalidade. Mais simples
  de configurar. Rejeitada: mínimo para entrega e nenhum para retirada é
  exatamente a configuração que a loja quer, porque o mínimo existe para pagar o
  custo da entrega. Um valor único obrigaria a escolher qual dos dois casos
  atender mal.
- **Mínimo como aviso, não como recusa** — aceita o pedido e mostra alerta ao
  comerciante. Rejeitada: transforma toda decisão numa interrupção no pico, e o
  atendente vai aceitar todas para tirar a tela da frente.
- **Cobrar taxa adicional abaixo do mínimo**, em vez de recusar. É o que alguns
  aplicativos fazem. Rejeitada: acrescenta um componente ao total que a ADR-009
  não tem, e a decomposição do valor é justamente onde este produto não improvisa.
- **Exceção manual pelo painel**, como o aceite fora do horário. Rejeitada
  pelo §4: `T01` é a transição que cria o pedido, e uma exceção aqui é um caminho
  para o pedido nascer violando a própria guarda.

## Emenda que esta decisão provoca

O documento de arquitetura v2 (`docs/referencia/`), §18.1, lista **pedido
mínimo** como decisão de produto sem dono e sem prazo. Passa a estar decidido
aqui.

## Emenda de 26/08/2026 — o campo mudou de nome

A guarda do §2 passa a ler `subtotalDosItens`. É renome de idioma (ADR-035),
decidido junto com a emenda da **ADR-009** — a semântica não muda, e o pedido
mínimo continua medido sem a taxa de entrega.
