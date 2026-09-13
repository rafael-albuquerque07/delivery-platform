# ADR-041 — Observabilidade sai do repositório até o marco 11

**Status:** Aceita — 13/09/2026
**Relacionada:** ADR-001 (monorepo), ADR-012 (gateway), ADR-037 (cadeia de
filtros), ADR-031 (`correlationId` em evento)
**Origem:** a primeira vez que um serviço deste repositório subiu numa porta

## Contexto

Em 13/09/2026 o `identity-service` subiu localmente pela primeira vez — Tomcat
em 8081, Flyway aplicando a `V1` contra um Postgres que sobrevive. No meio do log
de inicialização, uma linha:

```
o.s.b.a.e.web.EndpointLinksResolver - Exposing 2 endpoints beneath base path '/actuator'
```

O `application.yml` pede três:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

`micrometer-registry-prometheus` não está declarado em lugar nenhum do
repositório — nem no `libs.versions.toml`, nem no
`delivery.spring-service-conventions`, nem no build de módulo nenhum. O endpoint
`/actuator/prometheus` **nunca existiu**, nos nove.

E o `infra/observability/prometheus/prometheus.yml` raspa exatamente esse caminho,
nos nove alvos, de quinze em quinze segundos.

### O que a investigação encontrou depois

Não era uma dependência faltando. Era o stack inteiro:

| Contêiner | Quem alimenta | Existe? |
|---|---|---|
| Prometheus | `/actuator/prometheus` nos nove | **não** — sem registry |
| Grafana | Prometheus e Loki | **não** — as duas fontes estão vazias |
| Loki | um coletor de log (Promtail, Alloy) | **não** — não há coletor |
| Tempo | exportador OTLP nos nove | **não** — não há bridge de tracing |

Quatro contêineres, quatro entradas, zero.

E a mesma ausência explica o padrão de log dos nove serviços:

```yaml
console: "%d{...} %-5level [%X{traceId:-},%X{spanId:-}] %logger{36} - %msg%n"
```

Sem Micrometer Tracing, nada popula o MDC. Toda linha de log de todo serviço
carrega um `[,]` vazio, e carregaria por dez marcos.

### Onde o roadmap põe isso

`README.md`, tabela de etapas:

```
| 11 | Observabilidade |
```

O stack foi escrito no **marco 0**. Estamos no **1**. Ele foi escrito dez marcos
antes de existir qualquer coisa para observar, e portanto num momento em que era
impossível executá-lo — que é a condição em que as outras sete peças desta
lista nasceram.

### A oitava, e a primeira achada rodando

Este repositório já tinha sete peças com garantia escrita e execução zero: o
guardião da VM, as políticas de reinício, o gitleaks dentro de um pipeline que
nunca disparava, os nove pipelines quebrados pelo bit de execução do `gradlew`,
`flyway-core` sem o starter, `contracts/openapi/` com um `.gitkeep` dentro, e o
`:value-types` sem workflow.

As sete foram achadas **lendo**. Esta foi achada em dezenove segundos de log de
inicialização, no dia em que alguém rodou a coisa. É a melhor evidência que o
projeto tem a favor da própria regra: *peça que nunca rodou não é peça, é
intenção* — e a forma barata de descobrir é rodar.

## Decisão

> **Observabilidade sai do repositório até o marco 11.** O que não recebe nada
> não fica escrito como se recebesse.

### O que sai

1. O perfil `observability` do `docker-compose.yml` e os quatro contêineres —
   Prometheus, Grafana, Loki, Tempo — mais o volume `grafana-data`.
2. O diretório `infra/observability/` inteiro. O `prometheus.yml` fica
   **preservado literalmente no apêndice desta ADR**.
3. `prometheus` da exposição do actuator nos nove `application.yml`, que passa a
   ser `health,info`.
4. `%X{traceId:-},%X{spanId:-}` do padrão de log dos nove serviços.
5. `GRAFANA_ADMIN_PASSWORD` do `.env.example`.

### O que fica, e por quê

**`spring-boot-starter-actuator` fica.** Ele entrega `/actuator/health`, que a
ADR-037 libera sem token justamente porque é o que uma sonda precisa alcançar.
`health` e `info` continuam expostos, e `show-details: never` continua.

**O `correlationId` em payload de evento fica.** Ele não é o `traceId` do MDC, e
o comentário que justificava a linha de log — *"correlationId entra desde o
início: retrofit em payload de evento é caro"* — confundia os dois. O argumento
é verdadeiro sobre o campo do evento (ADR-031) e não tem nada a ver com o
`%X{traceId}`. Sai o segundo; fica o primeiro, intacto.

### Por que não bastava declarar o registry

`io.micrometer:micrometer-registry-prometheus`, com versão gerenciada pelo BOM,
é **uma linha** no convention plugin, e faria `/actuator/prometheus` existir nos
nove. A documentação do Boot 4.1.1 confirma que é a biblioteca, e não um
`spring-boot-starter-*`, porque não existe starter para ela — é a exceção
declarada à regra deste projeto de preferir o starter.

Foi considerado e rejeitado: resolve um quarto do problema e deixa três
contêineres recebendo nada. Métrica que ninguém olha não é observabilidade, é um
endpoint — e o marco 11 não é adiamento sem data: é onde o stack é decidido
inteiro, com **quem alimenta cada peça** escolhido junto.

## Consequências

**Positivas**

- A oitava instância do padrão é a primeira a ser **removida** em vez de
  consertada. Consertar cedo demais é exatamente como as sete anteriores
  nasceram: alguém viu uma peça pela metade e completou a peça, em vez de
  perguntar se ela devia existir agora.
- `docker compose --profile full` passa a subir só o que funciona.
- Toda linha de log de todo serviço deixa de carregar um `[,]` vazio.
- O `.env.example` perde uma variável sem consumidor. E o `CLAUDE.md` perde a
  contagem que já tinha divergido — ele dizia *"as oito variáveis"*, o compose
  interpolava sete, e depois desta decisão interpola seis. O número sai; a lista
  fica sendo o `.env.example`.
- A configuração e a realidade passam a concordar: o actuator pede dois
  endpoints e entrega dois.

**Negativas**

- **Apagar é mais barato que reescrever, e o marco 11 paga a diferença.** Ele vai
  recomeçar de um `prometheus.yml` preservado no apêndice de uma ADR em vez de um
  arquivo no lugar dele. Mitigação: está preservado, literal, e a decisão está
  datada.
- **O actuator fica sem métrica nenhuma.** Se algo estiver lento no marco 6 —
  Saga, outbox, consumo de fila —, não há número para olhar, só log. Aceito: a
  alternativa era manter quatro contêineres de pé para produzir um número que
  ninguém está olhando.
- **`%X{traceId}` sai e um dia volta**, e quando voltar são nove arquivos a
  editar de novo. É edição mecânica, e a alternativa era dez marcos de `[,]`.
- **Uma decisão fica adiada sem data**, e está registrada abaixo para não ser
  redescoberta do zero.
- **O repositório fica menos impressionante de olhar.** Um `docker-compose.yml`
  com Prometheus, Grafana, Loki e Tempo parece um sistema maduro. Esta ADR troca
  essa aparência por um compose em que tudo que está escrito funciona — e essa é
  a troca que o projeto vem fazendo desde o começo.

## Alternativas consideradas

- **Declarar `micrometer-registry-prometheus` e ligar só as métricas.** Uma
  linha, versão pelo BOM, e o alvo do Prometheus ficaria verde no mesmo dia.
  Rejeitada no corpo da decisão: liga um quarto e deixa três mentindo.
- **Ligar os quatro** — registry, `spring-boot-starter-opentelemetry` exportando
  OTLP para o Tempo, e um coletor para o Loki. Rejeitada: é antecipar o marco 11
  inteiro, com dependências novas nos nove serviços e um conjunto de contêineres
  a manter honesto a partir de agora, para observar um sistema que ainda tem um
  serviço com código.
- **Deixar como está.** Rejeitada explicitamente, e vale registrar por quê: é a
  opção que produziu as sete instâncias anteriores. Nenhuma delas foi uma decisão
  de deixar como estava — todas foram a ausência de alguém olhando.
- **Manter o perfil `observability` com os contêineres e um comentário dizendo
  que nada os alimenta.** Rejeitada: comentário não impede `docker compose
  --profile observability up`, e o que a pessoa vê é quatro contêineres de pé.
  Stack de pé sem entrada é pior que stack ausente, porque parece funcionar.

## O que esta decisão **não** decide

**Quem pode ler `/actuator/prometheus` quando ele existir.** A cadeia da ADR-037
exige autenticação em tudo que não seja `/actuator/health/**`, e o Prometheus não
tem token nosso. Três caminhos estavam na mesa e nenhum foi escolhido:

| Caminho | Custo |
|---|---|
| `permitAll` no endpoint, com a porta publicada só em `127.0.0.1` | Uma linha. Assume que métrica na rede interna do compose é aceitável |
| Porta de gerência separada (`management.server.port`), não publicada | Canônico e o mais defensável. Configuração nova nos nove e uma segunda cadeia de filtros |
| Basic auth dedicado ao scrape | Mantém "nada sem autenticação" intacto. Custa um segundo mecanismo de autenticação e um segredo novo |

A decisão foi **adiada de propósito**, para quando existir um consumidor real —
o mesmo critério com que a ADR-037 adiou onde mora o validador de `iss` e `aud`.

**Se o stack volta igual.** Prometheus, Grafana, Loki e Tempo foram escolha do
marco 0, sem ADR. O marco 11 escolhe de novo, e pode escolher outra coisa.

**Se `%X{traceId}` volta com Micrometer Tracing ou com OpenTelemetry direto.** O
Boot 4 introduziu `spring-boot-starter-opentelemetry`, que traz exportação de
métrica e trace por OTLP num pacote só. É candidato, não decisão.

---

## Apêndice — o que foi removido, preservado literalmente

`infra/observability/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: delivery-services
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - gateway:8080
          - identity-service:8081
          - merchant-service:8082
          - catalog-service:8083
          - settlement-service:8084
          - order-service:8085
          - payment-service:8086
          - delivery-service:8087
          - conversation-service:8088
```

O bloco do `docker-compose.yml`:

```yaml
  # ──────────────────────────────────────────── observabilidade
  prometheus:
    restart: unless-stopped
    profiles: ["observability", "full"]
    image: prom/prometheus:latest
    ports:
      - "127.0.0.1:9091:9090"
    volumes:
      - ./infra/observability/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro

  grafana:
    restart: unless-stopped
    profiles: ["observability", "full"]
    image: grafana/grafana:latest
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD}
    ports:
      - "127.0.0.1:3000:3000"
    volumes:
      - grafana-data:/var/lib/grafana

  loki:
    restart: unless-stopped
    profiles: ["observability", "full"]
    image: grafana/loki:latest
    ports:
      - "127.0.0.1:3100:3100"

  tempo:
    restart: unless-stopped
    profiles: ["observability", "full"]
    image: grafana/tempo:latest
    ports:
      - "127.0.0.1:3200:3200"
```

E o padrão de log dos nove serviços:

```yaml
console: "%d{yyyy-MM-dd HH:mm:ss} %-5level [%X{traceId:-},%X{spanId:-}] %logger{36} - %msg%n"
```

Duas observações para quem reescrever no marco 11, que custaram uma investigação
e não devem custar duas:

1. `infra/observability/grafana/dashboards/`, `loki/` e `tempo/` continham só
   `.gitkeep`. Não havia dashboard, nem configuração de Loki, nem de Tempo.
2. O Tempo subia sem arquivo de configuração e sem receptor OTLP declarado.
