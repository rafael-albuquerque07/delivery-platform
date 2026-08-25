# Procedimento — reconciliação com o provedor de pagamento

**Status:** esqueleto · **Base:** ADR-023, ADR-030, `docs/dominio/pagamento.md` §8
**Vale a partir do marco 4.** Antes dele não há cobrança para reconciliar.

> A ADR-023 registrou isto nas consequências negativas: `txid` e valor existem
> nos dois lados, e a divergência **precisa de procedimento, não de um `catch`**.
> Este é o procedimento.
>
> Os números aqui — periodicidade, prazo de espera — são **propostas de
> partida**. Calibram-se com o primeiro cliente e com o PSP escolhido.

---

## A regra que vale mais que o resto do documento

> **Nenhuma divergência se resolve editando o nosso lado para bater com o deles.**

Confirmar uma liquidação na mão porque "o PSP diz que pagou" destrói a única
coisa que a invariante 6 do `CLAUDE.md` protege: que só a notificação assinada
confirma. Se a notificação existe, reprocesse-a. Se não existe, o caso é D2 e
não é reconciliação — é incidente.

O mesmo vale ao contrário: **não se apaga `Notificacao`, não se apaga
`Liquidacao`.** Os dois são somente-inserção. Correção é lançamento novo.

---

## 1. Quando roda

| | Proposta de partida |
|---|---|
| Periodicidade | uma vez por dia |
| Janela | o `diaOperacional` anterior, fechado — corte às 04:00 no fuso da loja (ADR-025) |
| Escopo | um estabelecimento por vez. Nunca "todas as lojas" numa varredura só |

Rodar sobre o dia já fechado evita o falso positivo mais comum: cobrança criada
há dois minutos que ainda não foi paga não é divergência, é cobrança nova.

**Antes de começar, confira a fila morta do `payment` e do `order`**
(`docs/operacao/mensagem-na-fila-morta.md`). Uma mensagem parada lá explica D1
sem precisar de mais nada, e é a causa mais provável.

---

## 2. A comparação

De um lado, o extrato do provedor. **Do outro, o nosso registro — e o nosso
registro é a `Cobranca`, não a `Liquidacao`.** A `Liquidacao` é consequência; a
cobrança é o que tem `referenciaExterna` para casar com o extrato.

```
para cada cobrança do dia operacional:
    extrato do PSP  ×  Cobranca.estado  ×  Liquidacao.situacao no pedido
```

Casa por `referenciaExterna`, nunca por valor e horário. Dois pedidos de R$ 48,00
às 20h15 é sábado normal, não coincidência.

---

## 3. Os quatro casos

### D1 — o PSP confirmou e o pedido não

O mais comum, e quase sempre benigno: a notificação se perdeu, ou parou na fila
morta.

| Ordem | O que fazer |
|---|---|
| 1 | Procure a `Notificacao` correspondente. **Ela existe?** |
| 2 | Existe, e foi `ACEITA` → o evento se perdeu depois. Reprocesse pela fila morta |
| 3 | Existe, e foi `DESCARTADA_INVALIDA` → **pare.** Assinatura inválida com pagamento real no extrato é caso D2 |
| 4 | Não existe → peça o reenvio ao provedor. O caminho é dele, e a assinatura vem junto |

**O que nunca fazer:** marcar a liquidação como `CONFIRMADA` pelo painel para
"acertar o número". Se o produto tiver essa tela, ela é um defeito.

Enquanto não resolve, o pedido fica com a liquidação em
`AGUARDANDO_CONFIRMACAO` — que é exatamente o estado correto para "recebemos
indício e não recebemos confirmação".

### D2 — o pedido confirmou e o PSP não conhece

**Este não é tarefa. É incidente.** Uma liquidação confirmada sem contraparte no
extrato significa uma de três coisas, e nenhuma é benigna:

- notificação forjada que passou pela validação — a assinatura está comprometida;
- ambiente trocado — confirmação de sandbox chegando em produção;
- alguém confirmou pelo caminho que o D1 acabou de proibir.

| Ordem | O que fazer |
|---|---|
| 1 | **Não corrija o pedido.** O registro errado é a evidência |
| 2 | Guarde o `payloadBruto` da notificação e o extrato do dia |
| 3 | Se houver suspeita de assinatura comprometida, **gire o segredo do PSP** antes de qualquer outra coisa |
| 4 | Só depois decida o que fazer com o pedido, e registre a decisão |

O passo 3 vem antes do 4 porque a correção do pedido pode esperar e a chave não.

### D3 — valores diferentes para a mesma referência

Duas causas prováveis, e a distinção importa:

| Se | Então |
|---|---|
| A cobrança foi recriada por mudança de valor (B10) | há duas `referenciaExterna` para o pedido. Confira se a **antiga** também foi paga — se foi, é devolução de origem `LIQUIDACAO_DUPLICADA` |
| O provedor aceitou valor parcial | é liquidação parcial legítima (`pedido.md` §7). Não force o valor cheio |

Se o extrato mostra **mais** que o `totalEfetivo`, o caminho não é corrigir a
liquidação — é registrar uma `Devolucao` (ADR-030). O dinheiro entrou; o que
falta é ele voltar.

### D4 — notificação sem correlação conhecida

A corrida do `pagamento.md` §3, ou cobrança de outro ambiente.

| Ordem | O que fazer |
|---|---|
| 1 | **Não descarte.** O payload está guardado; é dele que sai a resposta |
| 2 | Procure a cobrança pelo `txid` no provedor e recupere o `pedidoId` |
| 3 | Achou → grave a correlação e reprocesse a notificação |
| 4 | Não achou, e o `txid` não é nosso → registre como ruído do endereço público. Não é divergência |

Uma sequência de D4 do mesmo tipo, no mesmo dia, não é ruído: é varredura no
endereço público, e vira assunto de segurança.

---

## 4. Registrar

Cada divergência tratada, com: referência externa, pedido, caso (D1–D4), o que
foi feito, quem fez, quando. Somente-inserção, como o resto.

**Registre também as reconciliações limpas.** "Rodou e não achou nada" é a
informação que, meses depois, distingue "nunca houve divergência" de "ninguém
rodou".

---

## 5. O que a reconciliação não é

- **Não é fechamento de caixa.** O fechamento apura o dinheiro do expediente por
  entregador (`liquidacao.md`); a reconciliação confere um provedor externo
  contra o nosso registro. Sobra no fechamento tem outra causa provável — fila
  morta — e outro procedimento.
- **Não é conferência de valor do pedido.** Se o `totalEfetivo` está errado, o
  caminho é `Ajuste`, e a reconciliação não opina sobre isso.
- **Não é cobrança.** Pedido `NAO_LIQUIDADO` não aparece aqui: não houve
  cobrança, logo não há o que reconciliar.

---

## A escrever antes do marco 4

- **O formato real do extrato**, que só existe depois de o PSP estar escolhido —
  e com ele a decisão entre puxar por API ou conferir arquivo
- **Quem executa**, e o que acontece quando o executante é o próprio comerciante
  numa operação de uma pessoa
- **O limiar de D4 que vira alerta de segurança** em vez de tarefa
- **Se a reconciliação vira automática** quando houver volume. Hoje é console e
  leitura — e, como toda automação, ela só vale a pena depois de o procedimento
  manual ter rodado o bastante para se saber o que ele de fato faz
