# ADR-014 — Não adotar H2; PostgreSQL real em desenvolvimento e Testcontainers em teste

**Status:** Aceita — 16/08/2026
**Relacionada:** ADR-002 (banco por serviço)

## Contexto

Havia a proposta de um profile `dev-h2` para acelerar a inicialização local, ao
lado de um `dev-docker` com PostgreSQL real. O próprio documento de tecnologias
reconhecia que o H2 não valida PostGIS, índices, locks nem concorrência.

Essas são exatamente as características centrais de dois dos nove serviços: o
`inventory-service` existe para resolver concorrência sob lock e o
`geolocation-service` existe para consulta espacial. Nos demais, o ganho se
reduz a alguns segundos de startup.

Manter dois bancos exigiria migrations portáveis entre dialetos — o que na
prática proíbe índice parcial, `SELECT … FOR UPDATE`, `jsonb` e tipos
geográficos.

## Decisão

Nenhum H2. Desenvolvimento local com PostgreSQL/PostGIS via Docker Compose, em
um único profile `dev`. Todo teste que toca banco usa Testcontainers contra a
mesma imagem.

## Consequências

**Positivas** — uma única fonte de verdade de SQL; migrations livres para usar
recursos do PostgreSQL desde a primeira; o que passa no teste roda contra a
tecnologia de produção.

**Negativas** — desenvolvimento exige Docker em execução; a primeira execução
baixa imagens; teste de integração é mais lento que banco em memória.

**Mitigações** — `docker compose --profile core up -d` uma vez por dia;
`testcontainers.reuse.enable=true`; manter a maior parte da suíte como teste de
domínio puro, sem Spring e sem banco.

## Alternativas consideradas

- **`dev-h2` apenas em `identity` e `merchant`.** Rejeitada: benefício marginal,
  custo permanente de manter migrations portáveis.
- **Banco em memória compartilhado nos testes.** Rejeitada: reintroduz o mesmo
  problema de divergência com outro nome.
