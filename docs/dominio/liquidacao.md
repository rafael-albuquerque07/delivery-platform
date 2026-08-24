# Domínio — Liquidação e fechamento de expediente

**Serviço:** `settlement-service` · **Status:** vigente (v1.1, 21/08/2026)
**Fontes:** PRD §5 (P1, P2), PRD §6 E5, E6 e E7, ADR-002, ADR-009, ADR-022
**Invariantes do `CLAUDE.md` que este documento detalha:** 1, 3, 5, 7, 8

> O PRD §1 diz que **esta** é a funcionalidade que vende o produto: "ao fim do
> turno, uma tela mostra quanto entrou por método, quanto cada entregador
> recebeu e deve, qual a divergência de caixa e quanto ele tem a pagar de diária
> e comissão". Até este documento, era a funcionalidade menos especificada do
> repositório — tinha critérios de aceite e nenhuma regra de cálculo.

---

## 1. O problema, em uma frase

Às onze da noite, o dono precisa saber **quanto dinheiro deveria estar na mão do
Jorge**, quanto está, e quem deve para quem. Hoje ele descobre no papel e a
conversa termina em desconfiança. O sistema tem que chegar ao mesmo número por
um caminho que os dois consigam conferir.

---

## 2. Agregado

`Jornada` é raiz de agregado. Uma jornada é o turno de **um entregador em um
estabelecimento**. O mesmo entregador em duas lojas no mesmo dia tem duas
jornadas independentes, com remunerações possivelmente diferentes (H6.1).

```
Jornada  (raiz)
├── entregadorId, estabelecimentoId
├── estado                  ABERTA | EM_CONFERENCIA | FECHADA
├── abertura                momento: Instant, responsavel, fundoDeTrocoEntregue
├── diaOperacional          CONGELADO na abertura — ADR-025
├── vinculoSnapshot         remuneração CONGELADA na abertura
├── Lancamento        [n]   somente-inserção
├── Fechamento        [0,1] imutável depois de gravado
└── AjusteDeFechamento[n]   somente-inserção, posterior ao fechamento
```

**`vinculoSnapshot` é congelado na abertura.** Se o comerciante alterar a diária
do Jorge às 22h, a jornada aberta às 18h continua valendo o que valia quando
começou. Sem isso, o extrato muda depois de o entregador já ter visto o número —
e é precisamente aí que a confiança se perde.

**`diaOperacional` também é congelado, e é o da abertura.** Uma jornada aberta
às 18h de sábado e fechada às 2h30 de domingo é a jornada de **sábado** — que é
como as duas pessoas que a conferem a chamam. Congelar em vez de derivar tem o
mesmo motivo do `vinculoSnapshot`: um documento assinado não muda porque uma
constante mudou.

```
VinculoSnapshot
├── modeloDeRemuneracao   DIARIA | COMISSAO | DIARIA_MAIS_COMISSAO | TAXA_FIXA
├── valorDiaria           Money  (zero quando não se aplica)
├── comissaoPorEntrega    Money  (zero quando não se aplica)
└── taxaFixaPorEntrega    Money  (zero quando não se aplica)
```

### Lançamentos

Tudo que acontece na jornada é `Lancamento`, **somente-inserção**. Nada é
editado, nada é apagado.

| Tipo | Origem | Campos próprios |
|---|---|---|
| `ENTREGA_CONCLUIDA` | `PedidoEntregueV1` | `pedidoId`, `momento: Instant` |
| `LIQUIDACAO_DE_ENTREGA` | `PedidoEntregueV1` / `LiquidacaoConfirmadaV1` | `liquidacaoId`, `metodo`, `valorEfetivo`, `gorjeta`, `situacao` — duplica o que `pedido.md` §7 já registra no `order-service`, deliberadamente: nenhum serviço lê o banco de outro (ADR-002) |
| `ADIANTAMENTO` | Painel | `valor`, `motivo`, `autor` |
| `NAO_LIQUIDADO` | `PedidoEntregueV1` | `pedidoId`, `valorNaoRecebido`, `motivo` |

**Dois objetos, nomes parecidos.** A `Liquidacao` de `pedido.md` I6 vive no
`order-service`, dentro do `Pedido`, e registra que o dinheiro foi
efetivamente recebido — método, valor efetivo, custódia. O `Lancamento` de
tipo `LIQUIDACAO_DE_ENTREGA` desta tabela vive no `settlement-service`,
dentro da `Jornada`, e é o reflexo daquela liquidação no acerto do
entregador. **I6 exige a primeira; J1 restringe o segundo.** Toda entrega
produz os dois. Toda retirada produz só o primeiro.

**Todo cancelamento não produz nenhum dos dois.** Pedido cancelado não exige
`Liquidacao` (I6 só alcança `ENTREGUE` e `RETIRADO`) e não gera lançamento —
inclusive quando a entrega já tinha saído e voltou. Uma entrega devolvida é um
pedido cancelado, não um pedido entregue e não pago.

**`PedidoRetiradoV1` não gera lançamento.** Retirada não envolve entregador —
não há jornada para lançar. A `Liquidacao` do pedido existe assim mesmo (I6:
o dinheiro entrou no caixa da loja); o que não existe é reflexo dela em
jornada nenhuma.

**Todo `Lancamento` herda o `diaOperacional` da sua jornada**, em vez de
recalculá-lo pelo próprio momento. Sem isso, uma liquidação registrada às 05:00
dentro de um turno aberto às 03:00 cairia num dia e o fechamento em outro — e J9
quebraria por um caso que ninguém reproduziria em teste.

**Idempotência.** A chave natural de um lançamento de liquidação de entrega é
`liquidacaoId`; a de uma entrega é `pedidoId`. Mensagem repetida **não** gera
segundo lançamento — é a invariante 7 do `CLAUDE.md` aplicada onde ela mais dói:
contar a mesma entrega duas vezes infla a comissão e destrói o fechamento
(H7.3).

---

## 3. Estados da jornada

| De | Para | Gatilho | Guarda |
|---|---|---|---|
| — | `ABERTA` | Comerciante abre o turno | Não existe jornada `ABERTA` para o mesmo par entregador × estabelecimento |
| `ABERTA` | `EM_CONFERENCIA` | Comerciante inicia o acerto | **Nenhum pedido do entregador em `SAIU_PARA_ENTREGA` ou `NAO_ENTREGUE`** |
| `EM_CONFERENCIA` | `ABERTA` | Reabertura antes de fechar | Nenhum `Fechamento` gravado |
| `EM_CONFERENCIA` | `FECHADA` | Comerciante confirma | `dinheiroConferido` informado |

**Não existe transição saindo de `FECHADA`.** Jornada fechada não é editável
(H7.3). Correção posterior é `AjusteDeFechamento` — §7.

Um pedido só pode ir para `SAIU_PARA_ENTREGA` se o entregador tiver jornada `ABERTA` (T16
em `pedido.md`). É assim que a invariante 1 do `CLAUDE.md` — nenhuma entrega sem
liquidação registrada — se torna estruturalmente garantida em vez de esperada.

---

## 4. Apuração — as fórmulas

Todos os somatórios são sobre lançamentos **desta jornada**. `Money`, escala 2,
`HALF_UP`.

### 4.1 Grandezas

```
F    fundoDeTrocoEntregue                                    (da abertura)
E    contagem de lançamentos ENTREGA_CONCLUIDA
LD   Σ valorEfetivo   · metodo = DINHEIRO · situacao = CONFIRMADA
LC   Σ valorEfetivo   · metodo = CARTAO   · situacao = CONFIRMADA
LP   Σ valorEfetivo   · metodo = PIX      · situacao = CONFIRMADA
LA   Σ valorEfetivo   · situacao = AGUARDANDO_CONFIRMACAO     (Pix pendente)
LN   Σ valorNaoRecebido · tipo = NAO_LIQUIDADO
GD   Σ gorjeta        · metodo = DINHEIRO · situacao = CONFIRMADA
GC   Σ gorjeta        · metodo ∈ {CARTAO, PIX} · situacao = CONFIRMADA
A    Σ valor          · tipo = ADIANTAMENTO
```

### 4.2 Conferência de caixa

O entregador entrega **o dinheiro da loja**. A gorjeta em dinheiro é dele e não
entra na conferência — contá-la obrigaria a revistar o bolso de alguém todo dia,
o que é péssimo produto e pior relação de trabalho.

```
dinheiroDaLoja    = LD − GD
dinheiroEsperado  = F + dinheiroDaLoja
divergencia       = dinheiroConferido − dinheiroEsperado
```

`dinheiroConferido` é contado e informado pelo comerciante. Negativo = falta;
positivo = sobra. **As duas são registradas.** Sobra silenciosamente absorvida é
o mesmo defeito que falta silenciosamente absorvida: some o sinal de que algo
está errado no preparo de caixa.

> **Por que o fundo de troco entra e o troco devolvido não.** O entregador
> recebe R$ 50 num pedido de R$ 38 e devolve R$ 12 do próprio bolo. O saldo do
> bolso move +50 −12 = +38, que é exatamente o `valorEfetivo`. O troco se anula
> sozinho. O fundo só existe para o caso em que ele não consegue fazer o troco
> com o que já tem em mãos.

### 4.3 Extrato

Segue a estrutura de H7.2, literalmente.

```
Créditos    C = valorDiaria
              + (E × comissaoPorEntrega)
              + (E × taxaFixaPorEntrega)
              + GC                          gorjeta que caiu na conta da loja

Débitos     D = dinheiroDaLoja              o que ele recebeu em nome da loja
              + F                           o fundo que levantou
              + A                           adiantamentos já recebidos

saldoLiquido = C − D
```

- `saldoLiquido > 0` → **a loja paga** esse valor ao entregador.
- `saldoLiquido < 0` → **o entregador entrega** esse valor à loja, e fica com o
  restante do dinheiro como pagamento. É como a operação já funciona no balcão.

`LA`, `LP` e `LN` **não entram em nenhuma das duas colunas**:

| Grandeza | Por quê |
|---|---|
| `LP` (Pix confirmado) | Caiu direto na conta da loja; o entregador nunca teve custódia |
| `LA` (Pix pendente) | Ainda não é receita nem calote. Coluna própria no extrato |
| `LN` (não liquidado) | A loja não recebeu. Não é dívida do entregador — §5 |

### 4.4 Exemplo trabalhado

Jorge, turno da noite. Vínculo: diária R$ 80,00 + comissão R$ 4,00 por entrega.
Fundo de troco R$ 50,00. Seis entregas.

| Lançamento | Valor | Gorjeta |
|---|---:|---:|
| Dinheiro · confirmada | 48,00 | — |
| Dinheiro · confirmada | 62,50 | 5,00 |
| Dinheiro · confirmada | 35,00 | — |
| Cartão · confirmada | 71,00 | 4,00 |
| Cartão · confirmada | 44,00 | — |
| Pix · **aguardando confirmação** | 38,00 | — |
| Adiantamento | 30,00 | — |

```
LD = 145,50   GD = 5,00   LC = 115,00   GC = 4,00   LA = 38,00   A = 30,00   E = 6

dinheiroDaLoja   = 145,50 − 5,00            = 140,50
dinheiroEsperado =  50,00 + 140,50          = 190,50
dinheiroConferido (contado no balcão)       = 190,00
divergencia      = 190,00 − 190,50          =  −0,50   ← falta R$ 0,50

C = 80,00 + (6 × 4,00) + 0,00 + 4,00        = 108,00
D = 140,50 + 50,00 + 30,00                  = 220,50
saldoLiquido = 108,00 − 220,50              = −112,50
```

**Leitura para o comerciante:** "Jorge entrega R$ 112,50 e fica com R$ 78,00 de
pagamento. Faltaram R$ 0,50 no caixa. R$ 38,00 de Pix ainda aguardam
confirmação do banco."

Confere: ele tem R$ 190,50 de dinheiro da loja, entrega R$ 112,50, retém
R$ 78,00 — que é exatamente `C − A` = 108,00 − 30,00, a remuneração ainda não
adiantada.

### 4.5 Divergência nunca é compensada em silêncio

`divergencia` **não** entra em `saldoLiquido`. São números diferentes, com
naturezas diferentes: um é acerto contratual, o outro é um erro que precisa
aparecer.

O comerciante decide o que fazer com ela — absorver ou descontar — e a decisão é
um `AjusteDeFechamento` explícito, com autor e motivo. Se o sistema abater a
falta do pagamento automaticamente, a divergência some do relatório e a métrica
de acerto de caixa passa a mostrar zero para sempre.

---

## 5. `NAO_LIQUIDADO` — quem absorve

O cliente não pagou. O entregador fez a entrega.

**Padrão: o entregador recebe a comissão e não é debitado do valor.** O trabalho
foi feito; o risco de crédito é do estabelecimento, que decidiu vender sem
receber antes.

O `merchant-service` mantém a política `responsabilizaEntregadorPorNaoLiquidado`
(padrão `false`). Quando `true`, cada `NAO_LIQUIDADO` gera um lançamento de
débito com o valor, e o extrato mostra a linha nominalmente — nunca embutida no
saldo sem explicação.

Esta é uma decisão de **política do estabelecimento**, não de arquitetura. O que
a arquitetura decide é que ela seja explícita, configurável e visível no
extrato, em vez de virar uma subtração que o entregador descobre no fim do mês.

---

## 6. Fechamento

Ao confirmar, grava-se um `Fechamento` **imutável** com todas as grandezas da §4
materializadas — não recalculadas na leitura.

```
Fechamento
├── momento: Instant, responsavel
├── F, E, LD, LC, LP, LA, LN, GD, GC, A        grandezas congeladas
├── dinheiroEsperado, dinheiroConferido, divergencia
├── creditos C, debitos D, saldoLiquido
└── vinculoSnapshot                             o mesmo da abertura
```

**Por que materializar em vez de derivar.** Se o extrato for recalculado a cada
leitura, uma mudança futura na fórmula reescreve retroativamente extratos que
entregador e comerciante já assinaram. O fechamento é um documento, não uma
consulta.

`totalDoDiaEmJornadasPorMetodo` do estabelecimento é a soma dos fechamentos do dia, e
**tem que bater** com a soma das liquidações **de entrega** registradas
(H7.3). Isso é teste, não esperança.

"Do dia" aqui é o **dia operacional** (ADR-025), não o dia civil: a soma é sobre
os fechamentos cujo `diaOperacional` é o dia procurado.

**Este número não é o caixa da loja.** Ele consolida o que passou por jornada
de entregador. Pedido retirado no balcão satisfaz I6 — a `Liquidacao` existe,
o dinheiro entrou —, mas não gera lançamento nenhum e por isso não aparece
aqui. Nem venda de balcão sem pedido. O fechamento de caixa do
estabelecimento, que consolidaria os dois, está no §9 e não tem dono.

---

## 7. Correção depois do fechamento

```
AjusteDeFechamento              — somente-inserção
├── tipo      DIVERGENCIA_ABSORVIDA | DIVERGENCIA_DESCONTADA
│             | LIQUIDACAO_TARDIA | CORRECAO_DE_REMUNERACAO
├── delta     Money             — pode ser negativo
├── motivo    texto obrigatório
├── autor     usuário
└── momento   Instant

saldoEfetivo = Fechamento.saldoLiquido + Σ ajustes.delta
```

Mesmo padrão da invariante 5 do `CLAUDE.md`: o valor original permanece, a
correção é lançamento novo.

### Liquidação que chega depois

O webhook do Pix atrasa e chega com a jornada já `FECHADA`. Três regras:

1. O `Fechamento` **não é alterado**. Nunca.
2. Cria-se um `AjusteDeFechamento` de tipo `LIQUIDACAO_TARDIA`, com o valor.
3. O `LA` do fechamento original permanece registrado — é o histórico de que
   aquele valor esteve pendente naquele momento.

Sem a regra 1, um webhook de terceiro consegue reescrever um documento que duas
pessoas já conferiram e assinaram. É a mesma classe de problema que faz o
comerciante desconfiar do sistema e voltar para o papel.

---

## 8. Invariantes

| # | Invariante | O que quebra sem ela |
|---|---|---|
| J1 | Uma liquidação **de entrega** pertence a exatamente uma jornada | Valor contado duas vezes (H7.3) |
| J2 | Lançamento é somente-inserção; nunca `UPDATE`, nunca `DELETE` | Histórico reescrito |
| J3 | Mensagem repetida não gera lançamento duplicado — chave em `processed_messages` | Comissão inflada |
| J4 | `FECHADA` não transita para nenhum estado | Extrato muda depois de assinado |
| J5 | `saldoLiquido` e `divergencia` são grandezas **separadas** | Erro de caixa desaparece do relatório |
| J6 | `vinculoSnapshot` congelado na abertura | Remuneração muda no meio do turno |
| J7 | Jornada não entra em `EM_CONFERENCIA` com pedido em rota | Fecha-se um turno que ainda está acontecendo |
| J8 | Só existe uma jornada `ABERTA` por entregador × estabelecimento | Lançamentos se espalham entre turnos |
| J9 | `Σ fechamentos.LD` do dia == `Σ liquidações de entrega DINHEIRO` do dia | O total do dia não bate (H7.3) |
| J10 | Todo lançamento tem origem rastreável — `pedidoId` ou autor humano | Aparece dinheiro sem procedência |

---

## 9. O que este documento deliberadamente não decide

- **Quem pode abrir e fechar jornada.** É permissão contextual: a
  `GERENCIAR_JORNADA` definida em [`estabelecimento.md`](estabelecimento.md) §2.
  Se ela deve ser item concedível ou privilégio exclusivo de administrador
  continua em aberto.
- **Formato do extrato exportável** (H7.2). É apresentação.
- **Fechamento de caixa da loja** (não do entregador), que consolida também
  vendas de balcão. É escopo maior, e depende deste.
