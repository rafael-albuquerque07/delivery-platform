# ADR-003 — Mensageria híbrida: RabbitMQ para domínio, MQTT para telemetria

**Status:** ⛔ **Sem objeto** — 25/08/2026
**Motivo:** o assunto saiu do MVP antes de a decisão ser escrita
**Removido por:** ADR-021 (catálogo de serviços), ADR-020 (taxa por área)

---

## Uma distinção que o índice não cabia

Esta ADR **nunca foi escrita**. Não é o mesmo que revogada.

| | ADR-019 | Esta |
|---|---|---|
| Chegou a ser aceita | sim | **não** |
| Tem texto no histórico do Git | sim | **não existe** |
| O que resta | o registro da revogação | este documento |

O número foi reservado quando o assunto ainda existia. Quando ele deixou de
existir, sobrou uma linha riscada no índice e mais nada — e uma linha riscada não
explica o que se deixou de decidir nem o que aconteceria se o assunto voltasse.
É o que este arquivo passa a fazer.

## O que ela teria decidido

Dois barramentos, com protocolos diferentes porque o tráfego era diferente:

```
RabbitMQ    eventos de domínio        não podem se perder, baixa frequência
Mosquitto   posição do entregador     podem se perder, alta frequência
(MQTT)                                fire-and-forget, um tópico por entregador
```

O argumento era legítimo e continua sendo: **posição de entregador atualizada a
cada poucos segundos não tem a mesma natureza que "o pedido foi entregue".** Um
é sinal contínuo cuja última amostra torna a anterior irrelevante; o outro é
fato, e perder um fato é defeito. Forçar os dois no mesmo broker faz um dos dois
pagar caro — ou telemetria com garantia que ela não precisa, ou evento de
domínio sem a garantia que ele exige.

## Por que o assunto desapareceu

O MQTT existia para servir o `geolocation-service`. A **ADR-021** cortou esse
serviço do MVP, junto com o `inventory`. A **ADR-020** tirou o geoprocessamento
do caminho — a área de entrega passou a ser **escolhida numa lista**, não
deduzida de coordenada.

Sem rastreamento em tempo real, não há telemetria de alta frequência. Sem
telemetria, o segundo broker serve a zero casos.

O PRD §7 registra "rastreamento em mapa em tempo real" como **não-objetivo**
explícito, com a razão: *o comerciante conhece o entregador e o alcança por
telefone*. O marco 11 é onde isso voltaria, se voltasse.

## O que sobreviveu

**Um broker só, RabbitMQ**, para tudo o que o MVP tem — que é evento de domínio,
e nada mais. Está na ADR-002 e no `contracts/`.

**E o princípio continua vivo**, mesmo sem o segundo broker: protocolo segue a
forma do tráfego. É o mesmo raciocínio que a ADR-026 aplicou à fila morta e que
a ADR-027 aplicou à evolução de contrato — a garantia que se exige de uma
mensagem depende do que ela é, não de onde ela passa.

## O que foi descartado

- Mosquitto no `docker-compose`, com volume, porta e configuração próprios
- Tópico por entregador, com níveis de QoS a escolher e justificar
- Um segundo barramento para operar, monitorar, ter fila morta e ter runbook
- A ponte entre os dois, que alguém teria de escrever e manter

O `docker-compose.yml` hoje tem **um** broker. A ADR-021 registra a remoção do
Mosquitto entre as consequências positivas dela.

## Se o assunto voltar

O marco 11 traz rastreamento e telemetria. Se ele acontecer, a pergunta volta —
e **volta como ADR nova, com número novo.** Este número fica onde está, porque
número de ADR é endereço histórico e não vaga a preencher.

Duas coisas terão mudado até lá, e as duas empurram a resposta para longe de um
segundo broker:

- o sistema terá **um** barramento em produção, com fila morta, idempotência e
  procedimento operacional já rodados (ADR-026);
- e o custo de operar dois brokers passará a ser medido contra uma operação real,
  não contra uma planilha.
