# CLAUDE.md

Plataforma de delivery para comércio de bairro. Monorepo com 8 microsserviços de
negócio e um gateway, cada um com processo, banco, migrations, testes, contrato e
pipeline próprios.

**Contexto de produto:** o cliente é o **comerciante**, não o consumidor. A
plataforma frequentemente **não custodia o dinheiro** (pagamento na entrega), o
entregador **pertence ao estabelecimento** e o pedido nasce numa **conversa de
WhatsApp**. Se uma decisão parecer estranha, confira as premissas em
`docs/PRD.md` §5 antes de "corrigir".

---

## Comandos

```bash
# infraestrutura (o dia a dia usa só isto)
docker compose --profile core up -d

# build e testes
cd backend
./gradlew build                              # tudo
./gradlew :services:order-service:test       # um serviço
./gradlew :services:order-service:bootRun    # rodar um serviço
./gradlew printModules                       # listar módulos
```

Perfis do Compose: `core` (bancos e brokers) · `services` (aplicações) ·
`observability` · `full`. **Não suba `full` para trabalhar** — são muitos
containers e você quase nunca precisa deles.

---

## Regras que quebram o build se violadas

Cada serviço tem `HexagonalArchitectureTest` (ArchUnit). Ele falha se:

- `domain` importar Spring, JPA, Hibernate ou Jackson
- uma classe de `domain` tiver anotação de persistência
- a direção das dependências for invertida

```
api ─────────────────┐
                     v
infrastructure ──> application ──> domain
```

- `domain/` — regra pura, sem framework. Interfaces de repositório ficam aqui.
- `application/` — casos de uso e orquestração; depende de portas (`port/out`).
- `infrastructure/` — persistência, mensageria, clientes HTTP, segurança.
- `api/` — traduz HTTP em comando.

Entidade JPA vive em `infrastructure/persistence/entity`, **nunca** em `domain`.
A duplicação entre modelo de domínio e entidade é intencional.

---

## Invariantes de negócio

Estas não são preferências de estilo. Quebrar qualquer uma é defeito.

1. **Nenhuma entrega ou retirada conclui sem liquidação registrada** — método
   efetivo, valor efetivo e responsável pela custódia. `NAO_LIQUIDADO` é registro
   válido; ausência de registro não é.

2. **Valor cobrado vem do pedido.** Preço, total e taxa são sempre recalculados
   no servidor a partir do catálogo. Valor que chega no request é ignorado.

3. **`metodo_declarado` ≠ `metodo_liquidado`.** São campos distintos. O cliente
   pede dinheiro e paga no cartão porque não teve troco — isso é o caso normal.

4. **Item do pedido é congelado.** Nome, preço-base, opções escolhidas com nome e
   acréscimo. Alteração posterior no catálogo não muda pedido existente.

5. **Total é imutável.** Substituição e correção geram `Ajuste` (lista
   somente-inserção, com autor, motivo e data). `total_efetivo` é derivado,
   nunca gravado por cima.

6. **Comprovante em imagem não confirma Pix.** Só o webhook do PSP, com `txid`
   correlacionado ao pedido.

7. **Quem publica precisa de outbox. Quem consome precisa de
   `processed_messages`.** Sem exceção.

8. **Nenhum serviço lê o banco de outro.** Integração é por API ou evento.

9. **Nenhum identificador vindo da URL é confiável** sem confronto com o usuário
   autenticado e as permissões no estabelecimento.

---

## Convenções de código

- **Injeção por construtor**, campos `final`. Nada de `@Autowired` em campo.
- **`record` para DTOs.** Não usamos Lombok.
- **Enum, nunca `String` livre**, para status, método, modalidade e papel.
  Persistir com `@Enumerated(EnumType.STRING)`.
- **`Money`** (value object com `BigDecimal` escala 2 e `RoundingMode.HALF_UP`)
  para todo valor. `double` e `float` são proibidos na cadeia de dinheiro.
- **Entidade JPA nunca é `@RequestBody`.** DTO de request próprio, com Bean
  Validation.
- **Máquina de estados como tabela de transições válidas**, testada; transição
  fora da tabela lança exceção de domínio → HTTP 409.
- **Paginação obrigatória** em toda listagem. Nada de `findAll()` sem `Pageable`.
- **Erro em `ProblemDetail`** (RFC 7807). Nunca stack trace na resposta.
- **Timeout obrigatório** em toda chamada entre serviços. O padrão da biblioteca
  é esperar para sempre.

---

## Armadilhas deste repositório

| Situação | O que fazer |
|---|---|
| Precisa criar ou alterar tabela | Migration Flyway. `ddl-auto` é `validate` e continua assim |
| Precisa de índice ou validação no MongoDB | Mongock `changeUnit`, nunca comando manual no shell |
| MongoDB não aceita transação | Ele sobe como **replica set de nó único**. Outbox depende disso |
| `too many clients` no Postgres | `maximum-pool-size: 5` por serviço já está configurado; não aumente sem motivo |
| Tentado a adicionar H2 para acelerar teste | Não. Testcontainers contra a mesma imagem de produção. Ver ADR-014 |
| Tentado a usar `subprojects {}` no build raiz | Não. Convention plugins em `build-logic/`. `subprojects` quebra o configuration cache |
| Teste de integração lento localmente | `testcontainers.reuse.enable=true` em `~/.testcontainers.properties` |
| Precisa de permissão comercial em outro serviço | Consultar `merchant-service` via porta, com cache curto no Redis e política de **negar** quando indisponível |

---

## Onde as decisões moram

`docs/architecture/decisions/` — uma ADR por decisão, com contexto, alternativas
consideradas e consequências negativas assumidas.

**Se você for mudar algo que uma ADR decidiu, atualize a ADR na mesma alteração.**
Código que contradiz ADR sem justificativa é o defeito, não a ADR.

Documentos de referência:

- `docs/PRD.md` — o que o produto é, para quem, e o que está fora de escopo
- `docs/architecture/` — como o sistema funciona
- `contracts/` — OpenAPI, AsyncAPI e JSON Schema dos eventos

---

## Segurança

- Nenhum segredo no repositório. Apenas `.env.example`, com nomes de variáveis.
  Gitleaks roda no CI.
- Token JWT assinado com chave assimétrica; só o `identity-service` emite, todos
  validam pela chave pública.
- Log sem token, senha, documento, dado de pagamento ou coordenada exata.
- Webhook com assinatura validada e processamento idempotente — o endereço é
  público e a mensagem se repete.

---

## Escopo — o que **não** construir

Estes itens saíram deliberadamente. Não os reintroduza sem revisar as premissas
do PRD:

leilão de ofertas · pool competitivo de entregadores · navegação entre
estabelecimentos · busca no marketplace · cálculo de rota viária e ETA ·
telemetria GPS · aplicativo nativo de consumidor · comissão sobre venda ·
repasse financeiro

Adiados com justificativa (não cortados): controle **quantitativo** de estoque
(marco 10) e rastreamento em mapa (marco 11).
