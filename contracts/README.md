# Contratos

Esquemas, não classes Java compartilhadas. Nada aqui vira dependência de código
entre serviços — a única coisa que se compartilha é o **formato**.

- `openapi/` — um arquivo por serviço, validado no CI
- `asyncapi/` — eventos de domínio publicados via RabbitMQ
- `events/` — JSON Schema por evento, versionado

## Regra de versionamento

Todo evento carrega `eventId`, `eventType`, `eventVersion`, `occurredAt`,
`correlationId` e `payload`. Mudança incompatível cria uma nova versão
(`order-placed-v2.json`); a anterior continua publicada até nenhum consumidor
depender dela.

Contrato de evento é mais difícil de mudar que contrato REST — o consumidor
pode estar processando uma mensagem antiga que já está na fila.

O sufixo `V1` usado nos documentos de domínio — `PedidoRecebidoV1` — é a forma
abreviada do par: `eventType: "PedidoRecebido"` mais `eventVersion: 1`. A
versão vive no campo e no nome do arquivo de esquema, não no `eventType`.
