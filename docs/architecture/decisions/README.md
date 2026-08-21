# Registros de decisão (ADR)

| ADR | Decisão | Status |
|---|---|---|
| 001 | Monorepo | ⬜ a escrever |
| 002 | Banco por serviço | ⬜ a escrever |
| 003 | Mensageria híbrida RabbitMQ + MQTT | ⬜ a escrever |
| 004 | Um estabelecimento por pedido | ⬜ a escrever |
| 005 | PostGIS e Redis GEO | ⬜ a escrever |
| 006 | Carrinho no Redis: TTL, durabilidade e ordem do checkout | ⬜ a escrever |
| 007 | Mongock para versionamento de esquema no MongoDB | 🟨 decidida, formalizar |
| 008 | MongoDB como replica set de nó único | 🟨 decidida, formalizar |
| 009 | Modelo de valores do pedido | ✅ aceita |
| 010 | Caminho da Saga para itens sem controle de estoque | ✅ aceita |
| 011 | Autorização comercial: cache, TTL, invalidação e fail-closed | ⬜ a escrever |
| 012 | Roteamento do gateway sob `/merchants/{id}` | ⬜ a escrever |
| 013 | Retenção e anonimização de dados pessoais (LGPD) | ⬜ a escrever |
| 014 | Não adotar H2; Testcontainers como fonte de verdade | ✅ aceita |
| 015 | Emitir JWT com `NimbusJwtEncoder` | ✅ aceita |
| 016 | Front-end mínimo antes da PWA completa | ✅ aceita |
| 017 | MongoDB como decisão de aprendizado | ✅ aceita |
| 018 | Snapshot de opções no item e cotação pelo catálogo | ✅ aceita |
| 019 | `DeliveryQuotePort`: cotação por distância geodésica | ⛔ **revogada** — ver 020 |
| 020 | Taxa de entrega por área nomeada (bairro / faixa de CEP) | ✅ aceita |

## Formato

Título · Status · Contexto · Decisão · Consequências (positivas e negativas) ·
Alternativas consideradas.

Uma ADR sem "alternativas consideradas" e sem consequências negativas é
decoração, não registro de decisão.
