# Domínio — Cobrança, confirmação e devolução

**Serviço:** `payment-service` · **Status:** vigente (v1, 25/08/2026)
**Fontes:** PRD §5 (P1), PRD §6 H4.4 e E5, Resposta v1.1 §5.3, ADR-023, ADR-009, ADR-010, ADR-030
**Invariantes do `CLAUDE.md` que este documento detalha:** 1, 6, 7, 8
**Entra no marco 4** — Pix confirmado de verdade. Cresce no marco 8 — cartão e cobrança antecipada

Há uma frase no PRD que decide quase tudo o que está escrito aqui, e ela não
está na seção de pagamento. Está na tabela de premissas:

> **P1** — A plataforma **não custodia** o valor na maioria das transações.
> *Se deixar de valer:* volta a ser necessário adquirente, captura e **estorno**
> como caminho principal.

O produto assumiu que o dinheiro é entregue na porta, ao entregador, em espécie,
maquininha ou Pix. A plataforma não é intermediária desse dinheiro — ela é
testemunha dele. Por isso este serviço é o menor dos oito no marco 4 e por isso
a seção mais longa deste documento é sobre **devolução**, que é a parte em que a
diferença entre custodiar e testemunhar dói.

---

## 1. O que este serviço decide, e o que ele apenas registra

| Fato | Quem decide |
|---|---|
| Existe uma cobrança Pix para este pedido | **Aqui** |
| O provedor confirmou o recebimento | **Aqui** — e só aqui |
| A assinatura da notificação é legítima | **Aqui** |
| Esta notificação já foi processada | **Aqui** |
| Quanto o pedido vale | `order-service` — `totalEfetivo` |
| O pedido foi liquidado | `order-service` (ADR-023) |
| O pedido foi entregue, retirado ou cancelado | `order-service` |
| Quanto entrou no caixa do dia | `settlement-service` |
| Existe devolução devida | `order-service` — ADR-030 |

> **O `payment-service` não é o livro-caixa. Ele é a fronteira com o provedor.**

A razão está na ADR-023: a invariante 1 do `CLAUDE.md` — nenhuma conclusão sem
liquidação registrada — só é verificável dentro de uma transação se quem
controla a transição de estado controla também o registro. Quem controla a
transição é o `order`. Logo a `Liquidacao` mora lá, e aqui mora a conversa com
quem move o dinheiro.

Sobra, para este serviço, o que não é do pedido: **o segredo do provedor, um
endereço público na internet, uma política de retentativa que não é a nossa, e
o formato de quem a gente não controla.**

---

## 2. Agregado

`Cobranca` é raiz de agregado. Uma cobrança pertence a **um** pedido e a um
provedor.

```
Cobranca  (raiz)
├── cobrancaId, pedidoId, estabelecimentoId
├── provedor            identificador do PSP — o adaptador em uso
├── referenciaExterna   txid do Pix, id da intenção de cartão
├── valorSolicitado     Money — congelado na criação
├── estado              CRIADA | CONFIRMADA | EXPIRADA | CANCELADA | FALHOU
├── criadaEm, expiraEm
├── Notificacao   [n]   somente-inserção — o que o PSP mandou
└── Estorno       [0,n] somente-inserção — marco 8
```

**`valorSolicitado` é congelado.** Se um `Ajuste` mudar o `totalEfetivo` depois
de a cobrança existir, a cobrança **não muda de valor** — ela é cancelada e
outra é criada, ou a diferença vira devolução (§5). Cobrança que muda de valor
depois de o QR estar na mão do cliente é cobrança que o cliente não reconhece.

### `Notificacao`

```
Notificacao
├── idNoProvedor      chave de idempotência — do PSP, não nossa
├── recebidaEm
├── assinaturaValida  boolean
├── payloadBruto      exatamente como chegou, sem normalizar
└── resultado         ACEITA | DESCARTADA_DUPLICADA | DESCARTADA_INVALIDA
```

**O payload bruto é guardado íntegro e nunca reescrito.** Não é zelo: é a única
coisa que resolve uma discussão com o provedor seis meses depois. Normalizar
antes de guardar apaga justamente o campo inesperado que explicaria a
divergência.

**Notificação descartada também vira registro.** Uma assinatura inválida não é
não-evento — é tentativa, e uma sequência delas é ataque. O que ela não produz é
**efeito**: nada muda no pedido, nada é publicado.

---

## 3. O fluxo do Pix na entrega, e a corrida que ele esconde

O caminho está na ADR-023 e não se repete aqui. O que se acrescenta é a ordem
de escrita dentro do passo 3, que parece detalhe de implementação e não é:

```
payment recebe "crie cobrança para o pedido X, valor Y"
    1.  cria a cobrança no PSP           →  volta txid
    2.  GRAVA a correlação txid ↔ pedidoId   ← antes do passo 3
    3.  devolve o QR ao order
```

**O passo 2 vem antes do 3 porque o webhook pode chegar antes da resposta.** O
cliente que já está com o celular na mão paga em três segundos; a resposta
síncrona ao `order` pode demorar mais que isso num dia ruim de rede. Se a
correlação só fosse gravada depois, existiria uma janela em que uma notificação
legítima chega e o serviço não sabe de que pedido ela é.

Notificação sem correlação conhecida **não se descarta** — vira divergência
(§8), com o payload guardado. Descartar é perder um pagamento real com aparência
de mensagem inválida.

### O que a demora do webhook não pode fazer

Nada. O pedido é entregue com a liquidação em `AGUARDANDO_CONFIRMACAO` e a
transição T19 acontece assim mesmo (`pedido.md` §7). O entregador não espera na
porta pela latência de um provedor, e é por isso que a situação da liquidação é
um campo e não um estado do pedido.

### Comprovante em imagem

Invariante 6 do `CLAUDE.md`, e é a regra que este serviço existe para tornar
verdadeira: **o que o cliente mostra na tela não entra em lugar nenhum deste
fluxo.** Só a notificação assinada do provedor muda situação. Nenhuma tela do
produto oferece "confirmar mesmo assim".

---

## 4. O webhook — o único endereço público do sistema

`/api/v1/webhooks/**` é liberado sem JWT no gateway (ADR-012), porque o PSP não
tem token nosso. A autenticação acontece **aqui dentro**, pela assinatura do
corpo. Três consequências que valem estar escritas:

| | Regra |
|---|---|
| **Assinatura** | Validada antes de qualquer leitura de campo. Corpo bruto, não o objeto desserializado — normalizar antes de conferir invalida a conferência |
| **Idempotência** | Pela chave do **provedor**, não pela nossa. Reenvio é o comportamento normal de um PSP, não é falha |
| **Resposta** | Confirma o recebimento assim que a notificação é gravada. O efeito no pedido é por evento, não dentro da requisição do PSP |

A terceira é a que se erra: se a publicação do `LiquidacaoConfirmadaV1`
acontecesse dentro da requisição do webhook, uma indisponibilidade do broker
faria o PSP receber erro e reenviar — e o reenvio bate na idempotência, que já
gravou. O pagamento ficaria confirmado no provedor e ausente no pedido, em
silêncio.

Por isso a publicação sai pelo **outbox**, na mesma transação em que a
notificação é gravada (invariante 7). O que o PSP recebe é "recebi", não "já
processei tudo".

---

## 5. Devolução — o que este produto pode devolver, e o que ele só pode registrar

Esta é a seção que P1 obriga a existir, e a distinção que ela carrega é do mesmo
tipo daquela entre recuperar conta e recuperar estabelecimento: **duas coisas
que parecem uma.**

| | Dinheiro que a plataforma custodiou | Dinheiro que a plataforma nunca teve |
|---|---|---|
| Quando ocorre | Pix online, cartão — **marco 8** | tudo o que é pago na porta — **marco 4** |
| Quem devolve | o PSP, comandado daqui | o comerciante, por fora |
| O que o sistema faz | **estorna** e registra | **registra que é devido** |
| Quando termina | confirmação do PSP | alguém marca como paga |

> **Estorno é uma das formas de devolver, não é sinônimo de devolver.**
> No marco 4 não há um único caso em que a plataforma possa estornar, porque
> não há um único caso em que ela tenha recebido.

O que ela pode fazer é o que um caderno faria melhor que a memória: dizer que há
R$ 6,00 devidos àquele cliente, desde quando, por qual motivo, e se já foram
pagos. Fingir que "estornou" seria mentir no relatório do fechamento.

### O objeto

`Devolucao` vive no agregado `Pedido`, ao lado de `Liquidacao`, e é
**somente-inserção** — mesmo tratamento dos ajustes de valor. A decisão e o
porquê estão na **ADR-030**.

```
Devolucao
├── devolucaoId
├── valor              Money — sempre positivo
├── origem             AJUSTE_POSTERIOR | CANCELAMENTO_DE_PEDIDO_PAGO
│                      | LIQUIDACAO_DUPLICADA | CONFIRMACAO_APOS_CANCELAMENTO
├── formaDeDevolucao   ESTORNO_PSP | FORA_DO_SISTEMA
├── situacao           DEVIDA | EXECUTADA | CANCELADA
├── referenciaExterna  id do estorno no PSP | null
├── motivo             obrigatório, sempre
├── registradaPor      usuário
└── momento            timestamp
```

Três coisas nele não são óbvias:

**`valor` é sempre positivo.** Devolução não é liquidação com sinal trocado —
esse era o caminho fácil, e a ADR-030 explica por que ele quebra o fechamento do
entregador e a contagem de liquidações.

**`formaDeDevolucao` não é o método.** Não interessa aqui se o comerciante
devolveu em espécie ou por Pix pessoal: interessa se **o sistema executou** ou
se apenas registrou. `FORA_DO_SISTEMA` é o caso normal no marco 4, não a
exceção.

**`situacao = DEVIDA` é um estado que pode durar.** Não há prazo, não há
cobrança automática, não há alerta. É informação para o comerciante, e ele
resolve com o cliente como resolveria sem sistema nenhum — a diferença é que
agora ele lembra.

---

## 6. Os quatro caminhos que produzem devolução

A conta que os define é uma só:

```
Σ liquidações CONFIRMADAS  >  totalEfetivo   →  há devolução devida
```

**Gorjeta não entra dessa conta.** Ela é campo próprio (H5.2) e nunca foi
devida — foi dada. Somá-la produziria devolução onde não há.

| # | Origem | O que aconteceu | Marco |
|---|---|---|---|
| 1 | `AJUSTE_POSTERIOR` | O pedido foi pago e depois um `Ajuste` derrubou o `totalEfetivo` — cortesia por item que veio errado, desconto negociado depois da entrega | 4 |
| 2 | `LIQUIDACAO_DUPLICADA` | Duas liquidações confirmadas para o mesmo valor. O caso real: o cliente pagou o QR e, achando que não passou, pagou em dinheiro também | 4 |
| 3 | `CONFIRMACAO_APOS_CANCELAMENTO` | O webhook do Pix chegou **depois** de o pedido ser cancelado | 4 |
| 4 | `CANCELAMENTO_DE_PEDIDO_PAGO` | T12 — cancelado a partir de `PAGO`. É o caso que H4.4 nomeia, e o único com estorno de verdade | 8 |

### O terceiro é o desconfortável

`PRONTO → CANCELADO` é T18, e a tabela de transições diz **perda total**. Se
havia uma cobrança Pix em `AGUARDANDO_CONFIRMACAO`, o cliente ainda pode pagar
depois — e o PSP não sabe que a pizza foi para o lixo.

A regra:

> A notificação é aceita e a liquidação **é confirmada**, mesmo com o pedido em
> `CANCELADO`. O que ela produz, junto, é uma `Devolucao` de origem
> `CONFIRMACAO_APOS_CANCELAMENTO`, no valor confirmado.

Recusar a confirmação seria mais simples e seria errado por dois motivos. O
dinheiro **saiu da conta do cliente** — negar o registro não o traz de volta,
só o esconde. E `CANCELADO` é terminal (`pedido.md` §4): não há transição
saindo dele, então não existe estado para onde levar o pedido. O fato registrado
é "foi cancelado, e mesmo assim entrou dinheiro que precisa voltar".

Antes disso, a mitigação óbvia: **cancelamento cancela a cobrança no PSP**, e
uma cobrança cancelada normalmente não é mais pagável. A janela existe porque
"normalmente" não é "sempre" — o cliente pode ter aberto o QR antes.

### O segundo é o mais frequente

Pagamento duplicado por insegurança do cliente é o caso corriqueiro do Pix na
porta, e ele aparece **antes** do webhook chegar: o entregador recebe em
dinheiro, o pedido conclui, e a confirmação do Pix chega vinte minutos depois.
Duas liquidações confirmadas, uma devolução devida.

Isso não é defeito do desenho — é o desenho funcionando. Duas liquidações é
exatamente o que `pedido.md` §7 manda registrar para pagamento parcial, e a
diferença entre parcial e duplicado é a soma, não a estrutura.

---

## 7. Devolução depois do expediente fechado

O caso 2 acima quase sempre atravessa o fechamento — o webhook chega depois de a
jornada fechar.

**Nada é reescrito.** A `Jornada` fechada é imutável (`liquidacao.md` §2), e a
correção existe: `AjusteDeFechamento`, somente-inserção, posterior ao
fechamento. É o mesmo mecanismo do dinheiro que apareceu depois, e não precisa
de nada novo.

O que muda é a leitura. Um `AjusteDeFechamento` originado de devolução **não é
erro de conferência do entregador** — ele conferiu certo, o valor entrou duas
vezes. Se o extrato não distinguir os dois, o entregador leva a culpa por um
acerto que estava correto, e é assim que se perde a confiança que
`vinculoSnapshot` foi criado para proteger.

> A devolução ajusta a conta do **cliente**. Ela só toca a jornada quando o
> valor devolvido passou pela mão do entregador — e, quando toca, diz que
> passou.

---

## 8. Divergência com o provedor

A ADR-023 registrou isto como pendência do marco 4, nas consequências negativas:
`txid` e valor existem nos dois lados, e "o PSP confirmou e o pedido continua
pendente" precisa de procedimento, não de um `catch`.

O procedimento é `docs/operacao/reconciliacao-de-pagamento.md`. O que pertence
ao domínio são os quatro formatos possíveis da divergência:

| # | Divergência | O que provavelmente aconteceu |
|---|---|---|
| D1 | PSP confirmou, pedido sem liquidação confirmada | Notificação perdida, ou parou na fila morta (ADR-026) |
| D2 | Pedido confirmado, PSP não conhece | Grave — confirmação sem origem legítima. Nunca é "provavelmente" |
| D3 | Valores diferentes para o mesmo `txid` | Cobrança recriada, ou pagamento parcial que o provedor aceitou |
| D4 | Notificação sem correlação conhecida | A corrida do §3, ou cobrança de outro ambiente |

D2 é a única que nunca tem explicação benigna e é a única que interrompe a
operação em vez de virar tarefa.

---

## 9. Eventos

### Publicados

| Evento | Quando | Consumidores previstos |
|---|---|---|
| `LiquidacaoConfirmadaV1` | Notificação válida e inédita confirma o recebimento | `order`, `settlement` |
| `CobrancaExpiradaV1` | A cobrança venceu sem confirmação | `order` |
| `EstornoExecutadoV1` | O PSP confirmou o estorno — **marco 8** | `order` |

`LiquidacaoConfirmadaV1` já existe em `pedido.md` §8, listado do lado do
consumidor. Ele é publicado **aqui**, e a linha de lá permanece — é o mesmo
evento visto das duas pontas.

### Consumidos

| Evento | Origem | O que este serviço faz |
|---|---|---|
| `PedidoCanceladoV1` | `order` | Cancela a cobrança no PSP, se houver uma em aberto |
| `DevolucaoDevidaV1` | `order` | **Só quando `formaDeDevolucao = ESTORNO_PSP`** — marco 8. `FORA_DO_SISTEMA` não chega aqui |

A segunda linha é a que mantém a fronteira honesta: o `payment` não decide se
uma devolução existe, nem como ela é paga. Ele executa a que foi marcada para
execução, e ignora o resto.

---

## 10. Invariantes

A série é **B**, de `Cobranca`. Não é `P` porque `P1`–`P6` já são as premissas
do PRD, citadas por nome no `CLAUDE.md` e em cinco ADRs — duas coisas chamadas
`P1` num repositório é uma confusão que só aparece na revisão de código.

| # | Invariante | O que ela impede |
|---|---|---|
| B1 | Um pedido tem no máximo **uma** cobrança em estado `CRIADA` | Dois QR válidos, cliente paga os dois |
| B2 | Assinatura é conferida sobre o **corpo bruto**, antes de qualquer leitura de campo | Conferir o que já foi normalizado é não conferir |
| B3 | Notificação inválida é registrada e **não produz efeito nenhum** | Endereço público sem rastro de tentativa |
| B4 | Notificação repetida não produz segundo evento — chave é o id **do provedor** | O PSP reenvia por desenho; contar duas vezes infla o caixa |
| B5 | A correlação `referenciaExterna ↔ pedidoId` é gravada **antes** de o QR sair daqui | Webhook chega antes da resposta e não se sabe de quem é |
| B6 | `payloadBruto` é guardado íntegro, somente-inserção, nunca normalizado | Divergência com o provedor vira palavra contra palavra |
| B7 | Nenhum dado de cartão é gravado ou registrado em log — só o identificador da transação | ADR-013 §4, e é o dado que ninguém quer ter |
| B8 | Estorno só existe sobre cobrança `CONFIRMADA`, e no máximo até o valor confirmado | Dinheiro saindo de onde nunca entrou |
| B9 | Este serviço nunca grava `Liquidacao` nem lê o banco do `order` | ADR-023; dois donos do mesmo fato |
| B10 | `valorSolicitado` é imutável — mudança de valor cancela e recria a cobrança | Cliente paga um QR que mudou de valor depois de emitido |

---

## 11. Uma observação honesta sobre este serviço

No marco 4 ele faz **uma** coisa: cobrança Pix com QR dinâmico, webhook e
confirmação. É pouco, e é a mesma discussão que `entrega.md` §11 faz sobre o
`delivery-service`.

Os argumentos para mantê-lo separado, e eles não são simetria:

- **O segredo do PSP.** Uma credencial que move dinheiro não deve morar no
  serviço que também expõe o painel de pedidos.
- **Um endereço público na internet**, com política de retentativa que não é
  nossa e formato que não controlamos. Isolar a superfície pública num serviço
  fino é o motivo de ele ser fino.
- **É onde o marco 8 aterrissa.** Cartão, cobrança antecipada e estorno real
  crescem aqui sem tocar no pedido — e a emissão fiscal chega junto.

O argumento contrário — que isto poderia ser um adaptador dentro do
`order-service` até o marco 8 — é legítimo e está registrado nas alternativas da
ADR-023. Se ele vencer, que seja por ADR nova, e não por alguém achar o serviço
vazio e começar a enfiar coisa nele.

---

## 12. O que este documento deliberadamente não decide

- **Qual PSP.** É escolha de fornecedor, com consequência de contrato e de
  transferência internacional (`docs/operacao/revisao-juridica.md` §7). O
  adaptador existe para que a escolha seja tardia.
- **Prazo de expiração da cobrança Pix.** Configuração. O número de partida sai
  do primeiro cliente, não daqui.
- **Se a devolução devida vira cobrança ativa contra o comerciante.** Hoje é
  informação, não obrigação rastreada com prazo. Se o volume mostrar que
  devolução devida vira devolução esquecida, isso vira decisão — e provavelmente
  ADR.
- **Emissão fiscal** (H10.1, marco 8). Nasce do pedido concluído, e a relação
  dela com estorno é assunto de quando existir.
- **Split de pagamento entre plataforma e comerciante.** Não existe: não há
  comissão sobre venda (PRD §7). Se um dia houver, P1 caiu e este documento
  inteiro é reescrito, não emendado.
