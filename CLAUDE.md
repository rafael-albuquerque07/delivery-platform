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
   somente-inserção, com autor, motivo e data). `totalEfetivo = total + Σ
   ajustes.delta` é derivado, nunca gravado por cima. Nenhum relatório soma
   `total` para dizer quanto entrou.

6. **Comprovante em imagem não confirma Pix.** Só o webhook do PSP, com `txid`
   correlacionado ao pedido.

7. **Quem publica precisa de outbox. Quem consome precisa de
   `processed_messages`.** Sem exceção.

8. **Nenhum serviço lê o banco de outro.** Integração é por API ou evento.

9. **Nenhum identificador vindo da URL é confiável** sem confronto com o usuário
   autenticado e as permissões no estabelecimento.

10. **Divergência de caixa nunca é compensada em silêncio.** Falta e sobra são
    registradas e aparecem no fechamento. Abatê-las automaticamente do
    pagamento do entregador apaga a métrica que o produto existe para produzir.

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

## Configuração de ambiente que funciona (Windows)

Verificado em 23/08/2026, build verde de ponta a ponta com o VS Code aberto.
Se o build quebrar, volte para exatamente isto antes de investigar.

| Item | Valor |
|---|---|
| Repositório | `C:\dev\delivery-platform` — **fora** do perfil do usuário |
| `GRADLE_USER_HOME` | `C:\gradle` — permanente, nível User, fora do perfil |
| Gradle | **8.14.3**, fixado no wrapper |

Definir a variável só com `$env:` não basta: vale numa janela só, e o VS Code
herda o ambiente na inicialização. Use
`[Environment]::SetEnvironmentVariable("GRADLE_USER_HOME","C:\gradle","User")`
e reabra o VS Code.

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
| Precisa de permissão comercial em outro serviço | Consultar `merchant-service` via porta, com cache **em processo** (60 s, Caffeine) e política de **negar** quando indisponível — ADR-011. Nunca no Redis: a cache existe para evitar ida à rede |
| Vai escalar um serviço para 2+ instâncias | A cache de autorização é em processo. O consumidor de `VinculoAlteradoV1` precisa de **fila exclusiva por instância** ligada a um exchange fanout. Com fila compartilhada, só uma instância invalida e as outras seguem com permissão revogada até o TTL — silenciosamente. ADR-011 |
| `allowEmptyShould(true)` no ArchUnit | Muleta temporária: com só `.gitkeep` nas camadas, zero classes = falha. **Remova por serviço assim que ele tiver classes** — mantido depois, uma camada apagada ou pacote renomeado passa em silêncio |
| Declarar versão de Testcontainers | Não declare. O BOM do Boot 4.1.x traz testcontainers-bom 2.x, onde os artefatos mudaram de nome: `org.testcontainers:testcontainers-junit-jupiter`, `-postgresql`, `-mongodb`, `-rabbitmq` |
| Vai somar `total` num relatório | Não. `total` é o valor congelado no fechamento. O que entrou é `Σ Liquidacao.valorEfetivo`; o que o pedido vale hoje é `totalEfetivo` |
| Vai calcular quanto o entregador ganha por pedido | Não existe. Remuneração vem do vínculo e é apurada por jornada — ADR-022 |
| Vai colocar permissão ou papel dentro do JWT | Não. Permissão é do vínculo usuário × estabelecimento e é resolvida por requisição, com cache curto. No token vai só a identidade |
| Serviço de autorização indisponível | **Negar.** Fail-closed é decisão assumida: liberar em caso de dúvida transforma uma queda em acesso irrestrito |
| Faixa de horário que cruza a meia-noite | `fim < inicio` pertence ao dia de início e se estende ao seguinte. Teste com pedido à 01:00 é obrigatório |
| Build quebra em `build-logic:compilePluginsBlocks` — arquivo ausente ou erro de parse | Primeiro volte à configuração de ambiente acima; foi assim que a série de falhas de 22/08 terminou. Se persistir, rode de um terminal fora do VS Code e desabilite `redhat.java` e `vscjava.vscode-gradle` no workspace. Causa-raiz nunca foi isolada |
| Vai desabilitar `redhat.java` | O pacote Salesforce Apex o declara como dependência dura e o mantém ligado. Desabilite o Salesforce no workspace junto |
| Tentado a atualizar o Gradle | **A 9.7.1 falha** em `compilePluginsBlocks` neste build-logic. O wrapper fixa 8.14.3, que é a única versão com build verde. Bump é tarefa própria, verificada com `--rerun-tasks --no-build-cache` |
| `GRADLE_USER_HOME` apontando para dentro de `C:\Users\` | Não. Ver a configuração de ambiente acima |
| Vai guardar coordenada, áudio ou número de cartão | Não guarde. Endereço textual e bairro no lugar da coordenada, transcrição no lugar do áudio, `txid` no lugar do cartão. A forma mais barata de cumprir a LGPD é não ter o dado — ADR-013 §4 |
| Restaurou um banco a partir de backup | **Reaplique as exclusões de titular** com data posterior à do backup antes de o serviço voltar a atender. Backup restaurado ressuscita dado apagado — `docs/operacao/exclusao-de-titular.md` |

---

## Onde as decisões moram

`docs/architecture/decisions/` — uma ADR por decisão, com contexto, alternativas
consideradas e consequências negativas assumidas.

**Se você for mudar algo que uma ADR decidiu, atualize a ADR na mesma alteração.**
Código que contradiz ADR sem justificativa é o defeito, não a ADR.

Documentos de referência:

- `docs/PRD.md` — o que o produto é, para quem, e o que está fora de escopo
- `docs/architecture/` — como o sistema funciona
- `docs/dominio/` — as regras vigentes: agregados, invariantes, tabelas de
  transição e fórmulas de apuração. **Leia antes de escrever regra de negócio.**
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
- **Todo dado pessoal é alcançável por identificador estável do titular**
  (`usuarioId`, `contatoId`), indexado. Nunca só dentro de texto livre — sem isso
  não há como cumprir pedido de exclusão. ADR-013 §6.

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
