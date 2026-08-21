# delivery-platform

Plataforma de delivery multiestabelecimento em microsserviços. Nove serviços de
negócio e um gateway, cada um com processo, banco, migrations, testes, container,
contrato e pipeline próprios.

> **Estado: esqueleto (Fase 0).** A estrutura, o build e a infraestrutura estão
> montados. Ainda não há regra de negócio — a etapa 1 começa pelo
> `identity-service`.

---

## Requisitos

- JDK 21 (o Gradle Toolchain baixa se não houver)
- Docker e Docker Compose
- ~16 GB de RAM se subir tudo — mas o dia a dia usa só o perfil `core`

## Primeiros passos

```bash
cp .env.example .env          # preencha os valores; .env está no .gitignore
docker compose --profile core up -d
cd backend && ./gradlew build
```

> **Primeira execução:** o build ainda não foi resolvido contra o Maven Central.
> Confirme as versões marcadas em `backend/gradle/libs.versions.toml` — as do
> Spring Boot e do Spring Cloud foram verificadas contra fontes oficiais; as de
> MapStruct, Mongock, ArchUnit e Resilience4j precisam de conferência no primeiro
> `./gradlew dependencies`.

## Como se trabalha aqui

Você **não** sobe os dez aplicativos. Sobe a infraestrutura e roda pelo IDE
apenas o serviço em que está mexendo:

```bash
docker compose --profile core up -d           # bancos, brokers, storage
./gradlew :services:order-service:bootRun     # só o que interessa agora
```

| Perfil do Compose | Sobe |
|---|---|
| `core` | PostgreSQL/PostGIS, MongoDB, Redis, RabbitMQ, Mosquitto, MinIO |
| `services` | gateway e os nove microsserviços |
| `observability` | Prometheus, Grafana, Loki, Tempo |
| `full` | tudo |

Comandos úteis:

```bash
./gradlew build                              # backend inteiro
./gradlew :services:order-service:test       # testes de um serviço
./gradlew printModules                       # lista os módulos
```

---

## Estrutura

```
delivery-platform/
├── backend/                    build Gradle multi-projeto (Kotlin DSL)
│   ├── build-logic/            convention plugins — toda configuração compartilhada
│   ├── gradle/libs.versions.toml
│   ├── infra/gateway/
│   └── services/               os nove microsserviços
├── contracts/                  OpenAPI, AsyncAPI e JSON Schema dos eventos
├── docs/                       PRD, arquitetura, ADRs e PDFs de referência
├── infra/                      configuração de Postgres, Mongo, MQTT e observabilidade
├── frontend/                   entra na etapa 4 (ADR-016)
└── .github/workflows/          um pipeline por serviço + workflow reutilizável
```

### Dentro de cada serviço

Arquitetura hexagonal. A divisão é por **anel de dependência**, não por camada
técnica:

```
api ────────────────┐
                    v
infrastructure -> application -> domain
```

- `domain` não conhece Spring, JPA nem MongoDB;
- `application` orquestra casos de uso e depende de interfaces (`port/out`);
- `infrastructure` implementa persistência, mensageria e clientes;
- `api` traduz HTTP em comando;
- entidades JPA vivem em `infrastructure/persistence/entity`, nunca em `domain`.

**Isso é verificado, não combinado.** Cada serviço tem um
`HexagonalArchitectureTest` (ArchUnit) que quebra o build se a regra for violada.
Em revisão manual, a arquitetura erode em duas semanas.

---

## Serviços e portas

| Serviço | Porta | Persistência | Publica eventos |
|---|---:|---|---|
| gateway | 8080 | — | — |
| identity-service | 8081 | PostgreSQL | sim |
| merchant-service | 8082 | PostgreSQL + Redis | sim |
| catalog-service | 8083 | MongoDB + Redis | sim |
| inventory-service | 8084 | PostgreSQL | sim |
| order-service | 8085 | PostgreSQL + Redis | sim |
| payment-service | 8086 | PostgreSQL | sim |
| delivery-service | 8087 | PostgreSQL + Redis | sim |
| geolocation-service | 8088 | PostGIS + Redis | sim |
| notification-service | 8089 | MongoDB | não (terminal) |

Sete bancos PostgreSQL e dois MongoDB — nove bancos lógicos.
**Nenhum serviço acessa o banco de outro.**

---

## Decisões que já moldam este esqueleto

| Decisão | Onde aparece |
|---|---|
| **ADR-014** — sem H2 | Um único profile `dev`; Testcontainers em todo teste de banco; `maximum-pool-size: 5` em cada serviço, porque sete deles no default de 10 estouram o `max_connections` do Postgres |
| **ADR-015** — JWT com `NimbusJwtEncoder` | Todos os serviços são Resource Server; só o `identity-service` emitirá |
| **ADR-016** — front mínimo antes da PWA | `frontend/` é só um README até a etapa 4 |
| **ADR-017** — MongoDB como aprendizado | Mongock nos serviços documentais; MongoDB sobe como **replica set de nó único**, sem o qual não há transação multi-documento e portanto não há Outbox |

O índice completo está em [`docs/architecture/decisions/`](docs/architecture/decisions/README.md).

## Documentos

| Arquivo | O que responde |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | O que o produto é, para quem, e o que está fora de escopo |
| [`CLAUDE.md`](CLAUDE.md) | Invariantes e convenções — leitura obrigatória antes do primeiro commit |
| [`docs/architecture/decisions/`](docs/architecture/decisions/README.md) | Por que cada decisão foi tomada, com alternativas e consequências |
| `docs/referencia/*.pdf` | Versões publicadas da arquitetura, do PRD e da revisão de escopo |

---

## Regras que valem desde a primeira linha

- **Migrations, sempre.** `ddl-auto: validate` em todo serviço relacional; toda
  estrutura nasce em Flyway (SQL) ou Mongock (MongoDB).
- **Publica → Outbox. Consome → `processed_messages`.** Sem exceção. Retrofit de
  Outbox significa revisitar todo ponto de publicação.
- **Nenhum segredo no Git.** Só `.env.example`, com nomes. Gitleaks roda no CI.
- **Timeout em toda chamada entre serviços.** O default é esperar para sempre, e
  a primeira chamada sem timeout é uma indisponibilidade latente.
- **`correlationId` desde o começo.** É código, não infraestrutura — e precisa
  estar no payload do evento desde o primeiro evento.
- **Valor cobrado vem do pedido.** Nunca de um número enviado pelo cliente.

---

## Roadmap

| Etapa | Entrega |
|---|---|
| 0 | ✅ Esqueleto: build, módulos, Compose, CI |
| 1 | PostgreSQL, Flyway, Testcontainers, CI mínimo |
| 2 | `identity-service`: JWT RS256, JWKS, senhas |
| 3 | `merchant-service` + gateway — vira microsserviços de fato |
| 4 | Front-end mínimo (entrega 15.A) |
| 5 | `catalog-service` |
| 6 | `inventory` + `order` + RabbitMQ + Outbox + idempotência |
| 7 | `payment-service` e Saga completa |
| 8 | `delivery-service` — aqui os nove requisitos originais estão atendidos |
| 9 | `geolocation-service`: MQTT, PostGIS, Redis GEO, rotas |
| 10 | `notification-service` |
| 11 | Observabilidade |
| 12 | Hardening e DevSecOps |

Ao fim da **etapa 8** todos os requisitos originais estão cumpridos. As etapas
seguintes ampliam o escopo, não provam mais nada sobre arquitetura.
