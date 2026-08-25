# ADR-030 — Devolução não é estorno, e não é liquidação negativa

**Status:** Aceita — 25/08/2026
**Fecha:** a promessa de devolução de H4.4 do PRD, que não tinha dono nem modelo
**Relacionada:** ADR-009 (valores e liquidação), ADR-010 (saga e pivô), ADR-023 (fronteira order × payment)
**Detalhada em:** `docs/dominio/pagamento.md` §5–§7
**Precisa existir antes do marco 4** — o registro entra lá; a execução, no marco 8

## Contexto

O PRD promete devolução numa linha só, em H4.4:

> Pedido pago que é cancelado dispara devolução do valor

Lida rápido, essa linha parece pedir um estorno. Lida junto com a premissa P1,
pede outra coisa:

> **P1** — A plataforma **não custodia** o valor na maioria das transações.
> *Se deixar de valer:* volta a ser necessário adquirente, captura e **estorno**
> como caminho principal.

O próprio PRD diz que estorno **não** é o caminho principal deste produto. Ele é
o caminho de um produto que custodia dinheiro, e este não custodia — o pagamento
acontece na porta, ao entregador, em espécie, maquininha ou Pix direto para a
conta da loja.

### O que isso produz na prática

Um pedido pago na porta cujo valor cai depois — cortesia por item que veio
errado, desconto negociado — deixa uma diferença:

```
Σ liquidações CONFIRMADAS  >  totalEfetivo
```

Essa diferença é dinheiro do cliente que está com o comerciante. **A plataforma
não tem como devolvê-la: ela nunca a teve.** O que ela pode fazer é uma de duas
coisas — registrar que é devida, ou fingir que não existe.

Fingir é o comportamento padrão de quem não decide isto: o `totalEfetivo` cai,
a soma das liquidações não, e o relatório do fechamento passa a ter uma
diferença sem nome.

### Quatro caminhos, um só no marco 8

| Origem | Quando | Custodiado? |
|---|---|---|
| Ajuste posterior à liquidação | marco 4 | não |
| Liquidação duplicada — pagou o QR e pagou em dinheiro | marco 4 | não |
| Webhook de Pix confirma depois do cancelamento | marco 4 | não |
| Cancelamento de pedido pago online (T12, H4.4) | **marco 8** | **sim** |

Três dos quatro acontecem no marco 4, e nenhum deles tem estorno possível.

## Decisão

### 1. `Devolucao` é objeto próprio, no agregado `Pedido`, somente-inserção

Ao lado de `Liquidacao`, no `order-service`. Mesmo tratamento do `Ajuste`:
lançamento, não edição.

```
Pedido
├── Ajuste      [n]   muda o que se DEVE
├── Liquidacao  [n]   registra o que ENTROU
└── Devolucao   [n]   registra o que precisa VOLTAR      ← novo
```

Fica no `order` e não no `payment` porque é a mesma razão da ADR-023: o `order`
é dono do registro do dinheiro do pedido, e o `payment` é a fronteira com o
provedor. Uma devolução que na maioria dos casos **nem chega ao provedor** não
tem por que nascer do lado dele.

### 2. `formaDeDevolucao` separa executar de registrar

```
ESTORNO_PSP        o sistema comanda, o provedor executa   — marco 8
FORA_DO_SISTEMA    o comerciante devolve por fora          — o caso normal
```

O sistema só afirma "devolvido" quando ele mesmo devolveu. Em `FORA_DO_SISTEMA`
a situação passa a `EXECUTADA` porque **alguém marcou**, e o registro guarda
quem marcou. É promessa menor e verdadeira, em vez de promessa maior e falsa.

`FORA_DO_SISTEMA` não é a exceção. É o caso normal do marco 4 ao 7.

### 3. Devolução **não** é liquidação com valor negativo

Este é o caminho fácil, e é o que esta ADR existe para recusar. `valor` é sempre
positivo.

Uma liquidação negativa faria `Σ valorEfetivo` bater sozinha — e é justamente
por parecer elegante que ela é a armadilha. Quebraria três coisas de uma vez:

- **J1 e J3 de `liquidacao.md`** passariam a ter de filtrar sinal para não
  contar saída como entrada. Toda regra de apuração ganharia uma condição que
  não tem a ver com o negócio.
- **O fechamento do entregador somaria algo que ele não recebeu.** O lançamento
  `LIQUIDACAO_DE_ENTREGA` reflete dinheiro que passou pela mão dele; uma
  devolução paga na semana seguinte, pelo comerciante, não passou.
- **"Quantas liquidações teve este pedido" deixaria de ter resposta.** Duas
  entradas e uma saída viram três liquidações, e a distinção entre pagamento
  parcial e devolução — que hoje é estrutural — vira uma questão de sinal.

### 4. Devolução também não é um `Ajuste`

Tentador, porque o `Ajuste` já muda `totalEfetivo` e já é somente-inserção. Mas
os dois respondem perguntas diferentes, e **quando um causa o outro, os dois
existem ao mesmo tempo**:

```
cortesia de R$ 6,00 num pedido de 55,00 já pago por inteiro
    ├── Ajuste     delta −6,00   →  o pedido passa a valer 49,00
    └── Devolucao  valor  6,00   →  há 6,00 do cliente a devolver
```

Fundi-los perderia exatamente a informação que interessa: o pedido valer menos e
o dinheiro precisar voltar são fatos distintos, e o segundo pode continuar
pendente muito depois de o primeiro estar resolvido.

### 5. O registro entra no marco 4; a execução, no marco 8

O objeto nasce completo, com as duas formas. No marco 4 só uma delas ocorre.

Não é antecipação: a diferença entre liquidado e devido **acontece a partir do
marco 4**, e o relatório do marco 6 a exibe. Adiar o registro para o marco 8
significaria seis marcos exibindo uma diferença sem explicação — e depois uma
migração para nomear o que já estava lá.

### 6. Confirmação depois do cancelamento confirma, e gera devolução

Se o webhook do Pix chega com o pedido já em `CANCELADO`, a liquidação **é
confirmada** e nasce junto uma `Devolucao` de origem
`CONFIRMACAO_APOS_CANCELAMENTO`.

Recusar a confirmação seria mais simples e esconderia o fato: o dinheiro saiu da
conta do cliente, e negar o registro não o traz de volta. Além disso `CANCELADO`
é terminal (`pedido.md` §4) — não há transição saindo dele, então não existe
estado para onde levar o pedido. O que se registra é o que aconteceu: foi
cancelado, e mesmo assim entrou dinheiro que precisa voltar.

## Consequências

**Positivas**

- A diferença entre o que entrou e o que era devido passa a ter nome, dono e
  motivo, em vez de aparecer como sobra inexplicada no fechamento.
- `Σ valorEfetivo` continua sendo só entrada. Nenhuma regra de apuração ganha
  condição de sinal, e J1 e J3 seguem como estão.
- O marco 8 acrescenta uma forma ao objeto existente e não migra nada.
- A promessa de H4.4 fica cumprida no que o produto consegue cumprir, e
  explicitamente não cumprida no resto — que é melhor que cumprida no papel.

**Negativas**

- **`situacao = DEVIDA` pode durar para sempre.** Não há prazo, alerta ou
  cobrança automática: é informação para o comerciante, não obrigação rastreada.
  Se na prática devolução devida virar devolução esquecida, isso vira decisão
  nova — e está nomeado em `pagamento.md` §12.
- **Alguém marca `EXECUTADA` sem prova.** Em `FORA_DO_SISTEMA` não há como o
  sistema verificar. O registro guarda quem marcou e quando, e é só isso que ele
  pode fazer.
- **Mais um objeto no agregado `Pedido`**, que já tem itens, ajustes e
  liquidações. Aceito: o agregado é o lugar onde a consistência do dinheiro do
  pedido é verificável numa transação local, e devolução é dinheiro do pedido.
- **A conta que detecta devolução precisa de gorjeta fora dela.** Um lugar a
  mais onde esquecer a gorjeta produz número errado — e o erro seria devolução
  fantasma, que é pior que devolução faltando, porque parece dívida do
  comerciante com o cliente.

## Alternativas consideradas

- **Liquidação com valor negativo.** Rejeitada no §3. É a mais barata de
  implementar e a que mais custa depois, porque contamina todas as regras de
  apuração com uma condição de sinal e faz o fechamento do entregador somar
  dinheiro que ele não viu.
- **Um tipo `DEVOLUCAO` no `Ajuste` existente.** Rejeitada no §4: ajuste é o que
  se deve, devolução é o que volta, e quando um causa o outro os dois precisam
  coexistir.
- **`Devolucao` no `payment-service`.** Coerente à primeira vista — devolução
  parece assunto de pagamento. Rejeitada porque três dos quatro caminhos nunca
  tocam o provedor, e o `payment` passaria a guardar o que não executa. Seria
  também o segundo dono de um fato do pedido, que é exatamente o que a ADR-023
  recusou.
- **Não registrar devolução no MVP; o comerciante resolve com o cliente.**
  Defensável, e é o que acontece hoje sem sistema nenhum. Rejeitada porque o
  produto exibe `totalEfetivo` e a soma das liquidações lado a lado no marco 6 —
  a diferença fica visível de qualquer jeito, e sem nome ela vira suspeita de
  erro do entregador.
- **Recusar a confirmação do Pix quando o pedido está cancelado.** Rejeitada no
  §6: esconde um pagamento que ocorreu de fato.

## Pendências que esta decisão não fecha

**Se devolução devida deve virar obrigação com prazo.** Hoje é informação. Vira
decisão se o volume mostrar que o registro não basta — e a resposta provavelmente
é de produto, não de arquitetura.

**O prazo para um Pix `AGUARDANDO_CONFIRMACAO` virar `NAO_LIQUIDADO`**, que
`pedido.md` §9 já registra como pendência e que **continua aberta**. Ela encosta
nesta decisão sem ser resolvida por ela: quanto mais longa a espera, maior a
janela do §6 — confirmação chegando depois do cancelamento. Encurtar o prazo
reduz devoluções desse tipo e aumenta os `NAO_LIQUIDADO` falsos. Depende do PSP
escolhido, e por isso segue onde está.
