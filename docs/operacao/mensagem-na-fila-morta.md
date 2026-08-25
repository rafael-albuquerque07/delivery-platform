# Procedimento — mensagem na fila morta

**Status:** esqueleto · **Base:** ADR-026
**Alerta que traz você aqui:** profundidade de qualquer fila morta acima de zero

> Uma mensagem morta é um evento de negócio que não teve efeito. Nesta escala,
> é o pedido de um cliente real — por isso o alerta dispara em **uma**, e não
> num limiar.

---

## Regra que vale antes de qualquer coisa

**Nunca cancele um pedido por causa de mensagem na fila morta.**

Retentativa esgotada gera alerta, nunca cancelamento automático — e o mesmo vale
para a decisão humana tomada às pressas. O pedido cujo evento morreu continua
válido: a comida existe, o cliente espera. Cancelá-lo transforma falha de
infraestrutura em perda de venda.

---

## 1. Antes de mexer

| Passo | Por quê |
|---|---|
| **Anote qual fila morta** disparou | O nome dela diz qual consumidor falhou, e o reprocessamento é sempre por origem |
| **Leia o envelope**: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId` | É o suficiente para diagnosticar sem abrir o serviço |
| **Não copie o corpo da mensagem para lugar nenhum** | O `payload` pode conter dado pessoal, e log com dado pessoal é proibido — ADR-013 §8. Use o `correlationId` |
| **Veja quantas são** | Uma é defeito de dado. Dezenas ao mesmo tempo é incidente de infraestrutura, e o diagnóstico é outro |

---

## 2. Diagnóstico: transitório ou permanente

A pergunta que decide tudo o que vem depois.

| Sinal | Provável causa | O que fazer |
|---|---|---|
| Muitas mensagens, mesma janela de tempo, serviços com erro de conexão | **transitório** — banco, broker ou serviço esteve fora além das quatro tentativas | §3 — reprocessar em lote |
| Uma mensagem, ou poucas, espalhadas no tempo | **permanente** — dado que este consumidor não sabe tratar, ou defeito | §4 — corrigir antes de reprocessar |
| `eventVersion` que o consumidor não conhece | **contrato** — implantação fora de ordem (ADR-027 §3) | §5 |

**Não reprocesse antes de decidir isto.** Reprocessar uma mensagem de causa
permanente devolve ela à fila morta, e a única coisa que muda é o horário.

---

## 3. Transitório — reprocessar

1. Confirme que a causa passou: o serviço responde, o banco aceita conexão.
2. Mova as mensagens da fila morta de volta para a fila de trabalho de origem.
3. Acompanhe a profundidade da fila morta: se voltarem, **não é transitório** —
   vá para §4.

**Reprocessar é seguro**, e o motivo tem nome: todo consumidor registra o que já
processou em `processed_messages` (invariante 7 do `CLAUDE.md`). Uma mensagem que
morreu depois de ter tido efeito parcial não duplica ao voltar.

É a única operação deste runbook que pode ser feita sem investigar antes — desde
que o §2 tenha dito "transitório".

---

## 4. Permanente — corrigir, depois reprocessar

Nesta ordem, e sem pular:

1. **Reproduza** com o `eventId`, em ambiente local. Se não reproduzir, o
   diagnóstico do §2 estava errado.
2. **Corrija o defeito** e implante.
3. **Só então** reprocesse.

Se a mensagem for irrecuperável — dado que nunca fez sentido, evento emitido por
defeito já corrigido —, registre a decisão de descartá-la com `eventId`,
`correlationId` e motivo, e diga qual pedido ficou sem aquele efeito. Descarte
sem registro é a mesma perda silenciosa que a fila morta existe para impedir.

---

## 5. O caso que engana: sobra de caixa que é mensagem perdida

Leia isto **antes** de tratar qualquer sobra no fechamento como erro de
conferência.

Se um evento de conclusão de pedido morreu antes de virar lançamento na jornada,
a liquidação está gravada no pedido e o `settlement` não sabe dela. O
`dinheiroEsperado` fica **menor** do que o dinheiro que o entregador tem em mãos,
e a diferença aparece como **sobra**.

O sistema sinaliza — mas com o rótulo errado. Parece erro de caixa e é mensagem
perdida.

> **Diante de uma sobra, confira a fila morta do `settlement` antes de conversar
> com o entregador.**

Se houver mensagem lá para aquela jornada: reprocesse primeiro, refaça a
conferência depois. A sobra desaparece sozinha. Acusar alguém de erro de caixa
por causa de uma fila é o tipo de coisa que faz o comerciante voltar para o
papel.

Se a jornada já estiver **fechada**, o fechamento não é alterado — nunca. A
liquidação tardia entra como `AjusteDeFechamento`, exatamente como o Pix que
confirma depois (`liquidacao.md` §7).

---

## 6. Depois

1. **Registre**: qual fila, quantas mensagens, causa, o que foi feito, quando.
2. **Confirme que a fila morta voltou a zero.**
3. Se a causa for de desenho e não de dado, ela vira armadilha no `CLAUDE.md` ou
   emenda em ADR — não fica só no registro do incidente.

---

## A escrever antes do primeiro cliente

- Comandos concretos de inspeção e de reprocessamento, por broker
- Quem tem autoridade para descartar mensagem
- **Parada do consumidor por falha em série** — hoje uma queda longa despeja a
  fila de trabalho inteira na fila morta, e o §3 vira trabalho em lote. É
  pendência nomeada na ADR-026
- **A métrica de profundidade ainda não existe.** A ADR-026 §7 decide que a
  profundidade de cada fila morta é exposta ao Prometheus com alerta em zero,
  mas o plugin `rabbitmq_prometheus` não está habilitado e não há alvo de
  scrape para o broker — o `prometheus.yml` só aponta para o `/actuator` dos
  serviços. Hoje a única forma de ver uma fila morta é a interface de
  management, à mão. Requisito do marco 3, junto do primeiro consumidor
- Painel com a profundidade das filas mortas ao lado da fila de pedidos, para o
  operador ver sem abrir console
