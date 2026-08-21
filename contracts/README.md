# Contratos

Esquemas, não classes Java compartilhadas. Nada aqui vira dependência de código
entre serviços — a única coisa que se compartilha é o **formato**.

- `openapi/` — um arquivo por serviço, validado no CI
- `asyncapi/` — eventos RabbitMQ e tópicos MQTT
- `events/` — JSON Schema por evento, versionado

## Regra de versionamento

Todo evento carrega `eventId`, `eventType`, `eventVersion`, `occurredAt`,
`correlationId` e `payload`. Mudança incompatível cria uma nova versão
(`order-placed-v2.json`); a anterior continua publicada até nenhum consumidor
depender dela.

Contrato de evento é mais difícil de mudar que contrato REST — o consumidor
pode estar processando uma mensagem antiga que já está na fila.
