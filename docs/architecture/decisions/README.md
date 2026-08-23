# Registros de decisão (ADR)

| ADR | Decisão | Status |
|---|---|---|
| 001 | Monorepo | ⬜ a escrever |
| 002 | Banco por serviço | ⬜ a escrever |
| 003 | ~~Mensageria híbrida RabbitMQ + MQTT~~ | ⛔ sem objeto — MQTT saiu do MVP (020, 021) |
| 004 | Um estabelecimento por pedido | ⬜ a escrever |
| 005 | ~~PostGIS e Redis GEO~~ | ⛔ sem objeto — geoprocessamento saiu do MVP (020) |
| 006 | Carrinho no Redis: TTL, durabilidade e ordem do checkout | ⬜ a escrever |
| 007 | Mongock para versionamento de esquema no MongoDB | 🟨 decidida, formalizar |
| 008 | MongoDB como replica set de nó único | 🟨 decidida, formalizar |
| 009 | Modelo de valores do pedido | ✅ aceita · **emendada pela v1.1** |
| 010 | Saga do pedido: pivô em `PRONTO`, pagamento fora da transação | ✅ aceita · **reescrita pela v1.1** |
| 011 | Autorização comercial: cache em processo, invalidação por evento, fail-closed | ✅ aceita |
| 012 | Roteamento do gateway | ⬜ a escrever |
| 013 | Retenção e anonimização de dados pessoais (LGPD) | ⚠️ **a escrever antes do marco 7** |
| 014 | Não adotar H2; Testcontainers como fonte de verdade | ✅ aceita · **emendada pela v1.1** |
| 015 | Emitir JWT com `NimbusJwtEncoder` | ✅ aceita |
| 016 | Front-end mínimo antes da PWA completa | ✅ aceita |
| 017 | MongoDB como decisão de aprendizado | ✅ aceita |
| 018 | Snapshot de opções no item e cotação pelo catálogo | ✅ aceita |
| 019 | `DeliveryQuotePort`: cotação por distância geodésica | ⛔ **revogada** — ver 020 |
| 020 | Taxa de entrega por área nomeada (bairro / faixa de CEP) | ✅ aceita |
| 021 | Catálogo de serviços do MVP — oito serviços | ✅ aceita |
| 022 | A remuneração do entregador pertence ao vínculo | ✅ aceita |
| 023 | Fronteira `order` × `payment`: pedido é dono do registro | ✅ aceita |

## Onde mora o quê

**ADR** responde *por que* uma decisão foi tomada, com alternativas e
consequências negativas. É histórico: uma ADR aceita não é reescrita por gosto,
é emendada ou revogada com data.

**`docs/dominio/`** responde *qual é a regra hoje* — agregados, invariantes,
tabelas de transição, fórmulas de apuração. É estado corrente: sempre reflete o
que o código deve fazer agora.

Quando as duas divergirem, a divergência é o defeito. Corrija na mesma alteração.

## Formato

Título · Status · Contexto · Decisão · Consequências (positivas e negativas) ·
Alternativas consideradas.

Uma ADR sem "alternativas consideradas" e sem consequências negativas é
decoração, não registro de decisão.

## Emendas

Uma ADR alterada por revisão de escopo mantém o número e ganha um bloco de
emenda no topo, com data e motivo. O texto abaixo do bloco é sempre o vigente —
a versão anterior vive no histórico do Git, que é onde histórico deve viver.

Foi assim com 009, 010 e 014 na revisão v1.1. Não foi assim com a 019, que ficou
marcada como "aceita" por três dias enquanto já estava revogada em outro
documento — e é essa a falha que este parágrafo existe para não repetir.

Quando uma ADR nova **corrige um documento publicado** (um PDF em
`docs/referencia/`, que não se reescreve), ela registra isso numa seção "Emenda
que esta decisão provoca" no fim. Ver 011 e 023.

Emenda é para mudança de decisão, de justificativa ou de custo assumido.
Rótulo que muda sem que a coisa mude — número de marco, nome de arquivo, nome
de serviço — é troca direta, com o motivo na mensagem do commit. A ADR-016 foi
o primeiro caso: a decisão continua a mesma, só o marco referenciado mudou de
nome.
