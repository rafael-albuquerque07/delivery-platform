# ADR-017 — MongoDB no catálogo e nas notificações como decisão de aprendizado

**Status:** Aceita — 16/08/2026
**Relacionada:** ADR-007 (Mongock), ADR-008 (replica set)

## Contexto

O catálogo tem variação real — tamanhos, sabores, adicionais, remoções, combos,
atributos por categoria. A justificativa de produto para MongoDB é legítima.

Ainda assim, **PostgreSQL com coluna `jsonb` e índice GIN cobriria o mesmo caso
de uso**, mantendo ACID, uma tecnologia a menos para operar e o Flyway como
única ferramenta de migration. Na escala deste sistema, MongoDB não é
necessidade técnica — é escolha.

## Decisão

Manter MongoDB nos dois serviços, registrando que a motivação primária é
**demonstrar persistência poliglota**, objetivo declarado do projeto, e a
secundária é a flexibilidade de esquema.

## Consequências

**Positivas** — aprendizado real de modelagem documental; o catálogo, serviço de
maior volume de leitura, escala independente do banco transacional.

**Negativas** — Outbox exige replica set (ADR-008); Flyway não cobre MongoDB,
exigindo Mongock (ADR-007); mais um banco para operar e backupear; sem
integridade referencial, unicidade passa a depender de índice e de código.

## Alternativas consideradas

- **PostgreSQL com `jsonb`.** Tecnicamente suficiente e operacionalmente mais
  simples. Rejeitada por conflitar com o objetivo pedagógico — mas é a resposta
  correta quando alguém perguntar "por que MongoDB?", e saber articulá-la vale
  mais do que a escolha em si.
- **MongoDB só no catálogo.** Rejeitada: templates e tentativas de notificação
  são igualmente documentais.
