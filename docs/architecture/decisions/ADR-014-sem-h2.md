# ADR-014 — Não adotar H2; PostgreSQL real em desenvolvimento e Testcontainers em teste

**Status:** Aceita — 16/08/2026 · **emendada em 22/08/2026** pela arquitetura v1.1
**Relacionada:** ADR-002 (banco por serviço), ADR-020 (taxa por área), ADR-021 (catálogo de serviços)

> **Emenda v1.1 — a decisão continua, a justificativa mudou.** O texto original
> sustentava a recusa ao H2 nos dois serviços que mais dependiam de recursos
> específicos do PostgreSQL: `inventory` (lock e concorrência) e `geolocation`
> (consulta espacial). **Os dois saíram do MVP** (ADR-021), e o PostGIS saiu
> junto com eles — a imagem do Compose passou a ser `postgres:17-alpine`.
>
> Isso enfraquece o argumento original e é honesto dizer. A decisão **se mantém**,
> por razões que sobrevivem ao corte e que estão abaixo.

## Contexto

Havia a proposta de um profile `dev-h2` para acelerar a inicialização local, ao
lado de um `dev-docker` com PostgreSQL real. O próprio documento de tecnologias
reconhecia que o H2 não valida índices, locks nem concorrência.

Manter dois bancos exigiria **migrations portáveis entre dialetos** — o que na
prática proíbe índice parcial, `SELECT … FOR UPDATE`, `jsonb`, tipos `interval` e
qualquer coisa que não exista nos dois.

### O que sustenta a decisão sem `inventory` e sem `geolocation`

O corte de escopo tirou os dois exemplos mais vistosos, não o problema. O que
permanece, e é mais determinante do que era antes:

| Onde | Por que o H2 não serve |
|---|---|
| `settlement` | Apuração é somatório com filtro por método e situação sobre lista somente-inserção. Divergência de arredondamento ou de agregação entre dialetos produz **extrato errado** — e o extrato é o produto (PRD §1) |
| `order` | `Money` como `numeric(19,2)`; `total` imutável com ajustes derivados. Semântica de precisão precisa ser a de produção |
| Outbox, em todos | `SELECT … FOR UPDATE SKIP LOCKED` é o padrão de despacho. O H2 não o implementa com a mesma semântica |
| `processed_messages` | Idempotência depende de constraint única e do comportamento exato em conflito |
| Todos | `ddl-auto: validate` compara o esquema com o que o Flyway criou. Validar contra outro dialeto valida outra coisa |

**A decisão fica mais forte, não mais fraca.** Antes ela protegia dois serviços
especializados; agora protege a apuração de dinheiro, que é o coração do produto.

## Decisão

Nenhum H2. Desenvolvimento local com **PostgreSQL via Docker Compose**, em um
único profile `dev`. Todo teste que toca banco usa Testcontainers contra a mesma
imagem.

**Sem PostGIS.** A extensão saiu com a ADR-020 — não há geoprocessamento no MVP,
e extensão criada "por precaução" é superfície que alguém acaba usando. Volta com
o marco 11, se voltar.

MongoDB segue a mesma regra: Testcontainers, replica set de nó único, nunca banco
documental falso em memória.

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
- **Revogar esta ADR agora que `inventory` e `geolocation` saíram.** Considerada
  seriamente na revisão v1.1, e rejeitada: o corte removeu os exemplos, não o
  motivo. `SKIP LOCKED` no outbox e precisão monetária no `settlement` são
  razões mais fortes que as originais.
