# delivery-platform

Plataforma de delivery para **comércio de bairro**, em microsserviços. Oito
serviços de negócio e um gateway, cada um com processo, banco, migrations,
testes, container, contrato e pipeline próprios.

> **O cliente é o comerciante, não o consumidor.** A plataforma frequentemente
> não custodia o dinheiro, o entregador pertence ao estabelecimento e o pedido
> nasce numa conversa de WhatsApp. Se uma decisão parecer estranha, confira as
> premissas em [`docs/PRD.md`](docs/PRD.md) §5 antes de "corrigir".

> **Estado: esqueleto (marco 0).** A estrutura, o build e a infraestrutura estão
> montados. Ainda não há regra de negócio — o marco 1 começa pelo
> `identity-service`.

---

## Requisitos

- JDK 21 (o Gradle Toolchain baixa se não houver)
- Docker e Docker Compose
- ~12 GB de RAM se subir tudo — mas o dia a dia usa só o perfil `core`

## Primeiros passos

```bash
cp .env.example .env          # preencha os valores; .env está no .gitignore
docker compose --profile core up -d
cd backend && ./gradlew build
```

> **Windows:** mantenha o repositório **fora** do perfil do usuário — em
> `C:\dev\`, não em `Documents`. Sincronização de nuvem e antivírus corporativo
> apagam os arquivos que o Kotlin DSL gera em `build/`, e o sintoma é um build
> que falha em arquivos diferentes a cada tentativa. Aponte também
> `GRADLE_USER_HOME` para fora do perfil.

## Como se trabalha aqui

Você **não** sobe os nove aplicativos. Sobe a infraestrutura e roda pelo IDE
apenas o serviço em que está mexendo:

```bash
docker compose --profile core up -d           # bancos, brokers, storage
./gradlew :services:order-service:bootRun     # só o que interessa agora
```

| Perfil do Compose | Sobe |
|---|---|
| `core` | PostgreSQL, MongoDB, Redis, RabbitMQ, MinIO |
| `services` | gateway e os oito microsserviços |
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
│   └── services/               os oito microsserviços
├── contracts/                  OpenAPI, AsyncAPI e JSON Schema dos eventos
├── docs/
│   ├── PRD.md                  o que o produto é e para quem
│   ├── dominio/                as REGRAS vigentes — leia antes de codificar
│   ├── architecture/decisions/ ADRs
│   └── referencia/             PDFs publicados
├── infra/                      configuração de Postgres, Mongo e observabilidade
├── frontend/                   entra no marco 3 (ADR-016)
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

| Serviço | Porta | Persistência | Documento de domínio |
|---|---:|---|---|
| gateway | 8080 | — | — |
| identity-service | 8081 | PostgreSQL | — (autenticação, sem domínio de negócio) |
| merchant-service | 8082 | PostgreSQL + Redis | [`estabelecimento.md`](docs/dominio/estabelecimento.md) |
| catalog-service | 8083 | MongoDB + Redis | [`catalogo.md`](docs/dominio/catalogo.md) |
| settlement-service | 8084 | PostgreSQL | [`liquidacao.md`](docs/dominio/liquidacao.md) |
| order-service | 8085 | PostgreSQL + Redis | [`pedido.md`](docs/dominio/pedido.md) |
| payment-service | 8086 | PostgreSQL | — (fronteira com o PSP; ver ADR-021) |
| delivery-service | 8087 | PostgreSQL + Redis | [`entrega.md`](docs/dominio/entrega.md) |
| conversation-service | 8088 | MongoDB | [`conversa.md`](docs/dominio/conversa.md) |

Seis bancos PostgreSQL e dois MongoDB — oito bancos lógicos.
**Nenhum serviço acessa o banco de outro.**

> **Não há coluna "publica eventos" nesta tabela, de propósito.** A anterior
> tinha, estava desatualizada, e um agente tomou uma decisão de build lendo ela.
> Quem publica o quê está em `docs/dominio/<serviço>.md`, na seção de eventos,
> que é onde a informação é mantida.

### Serviços adiados

| Serviço | Volta em | Por quê saiu |
|---|---|---|
| `inventory` | marco 10 | A maioria dos produtos opera em disponibilidade qualitativa (P6) |
| `geolocation` | marco 11 | Taxa é por bairro, não por distância (P5, ADR-020) |

`notification` foi **absorvido** pelo `conversation-service` e não volta —
ADR-021.

---

## Decisões que já moldam este esqueleto

| Decisão | Onde aparece |
|---|---|
| **ADR-014** — sem H2 | Um único profile `dev`; Testcontainers em todo teste de banco; `maximum-pool-size: 5` em cada serviço relacional |
| **ADR-015** — JWT com `NimbusJwtEncoder` | Todos os serviços são Resource Server; só o `identity-service` emitirá |
| **ADR-016** — front mínimo antes da PWA | `frontend/` é só um README até o marco 3 |
| **ADR-017** — MongoDB como aprendizado | Mongock nos serviços documentais; MongoDB sobe como **replica set de nó único**, sem o qual não há transação multi-documento e portanto não há Outbox |
| **ADR-020** — taxa por área nomeada | Sem PostGIS, sem MQTT, sem motor de rotas |
| **ADR-021** — oito serviços no MVP | Este `settings.gradle.kts`, este Compose, esta tabela |
| **ADR-022** — remuneração no vínculo | Não existe campo de pagamento do entregador no pedido |
| **ADR-012** — rotas por recurso | `application.yml` do gateway. **Pendência do marco 1:** o `SecurityFilterChain` que libera `/api/v1/webhooks/**` sem JWT e exige token em todo o resto ainda não está escrito — o pacote `security/` do gateway só tem `.gitkeep` |

Índice completo em [`docs/architecture/decisions/`](docs/architecture/decisions/README.md).

## Documentos

| Arquivo | O que responde |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | O que o produto é, para quem, e o que está fora de escopo |
| [`docs/dominio/`](docs/dominio/README.md) | **As regras vigentes** — agregados, invariantes, tabelas de transição, fórmulas |
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
- **Valor cobrado vem do pedido.** Nunca de um número enviado pelo cliente, e
  nunca calculado pela interpretação automática do canal.
- **Nenhuma entrega ou retirada conclui sem liquidação registrada.**

---

## Marcos

A numeração é a de [`docs/PRD.md`](docs/PRD.md) §10, e é a única que vale.

| Marco | Entrega | O comerciante consegue |
|---|---|---|
| 0 | ✅ Esqueleto: build, módulos, Compose, CI | — |
| 1 | Conta, estabelecimento, equipe e permissões | Cadastrar a loja e o time, com poderes diferentes |
| 2 | Cardápio com opções e disponibilidade qualitativa | Publicar o cardápio e marcar o que acabou |
| 3 | Painel de pedidos e ciclo de preparo | Receber e acompanhar pedidos numa tela |
| 4 | Liquidação presencial, troco e Pix confirmado | Registrar como cada pedido foi pago de verdade |
| 5 | Entregadores, vínculo, remuneração e despacho | Despachar e saber quem está com cada pedido |
| **6** | **Fechamento de expediente** | **Fechar o dia com extrato por entregador** |
| 7 | Canal WhatsApp determinístico | Receber pedido pelo WhatsApp com menu numerado |
| 8 | Pagamento online e emissão fiscal | Cobrar antecipado e emitir documento |
| 9 | Atendimento assistido e escalonamento | Atender sozinho a maior parte dos pedidos |
| 10 | Controle quantitativo e substituição de item | Operar minimercado com contagem de estoque |
| 11 | Rastreamento e telemetria | Mostrar a entrega no mapa |

**Ao fim do marco 6 existe produto completo e vendável.** A loja cadastra equipe
e cardápio, recebe e prepara pedidos, despacha com entregador próprio, registra
como cada pedido foi liquidado e fecha o expediente com extrato. Os marcos 7 a 9
ampliam o alcance; 10 e 11 abrem segmentos novos.
