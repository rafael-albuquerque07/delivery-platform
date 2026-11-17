---
name: novo-caso-de-uso
description: Implementa uma funcionalidade nova atravessando as camadas hexagonais de um microsserviço deste monorepo — domínio, caso de uso, persistência, API, migration e testes. Use ao adicionar rota, criar caso de uso, implementar história do PRD, expor recurso novo ou estender um serviço existente. Numa rodada, vale junto com a metodo-delivery-platform.
---

# Novo caso de uso

Como se acrescenta funcionalidade num serviço deste monorepo, respeitando a arquitetura hexagonal e
os invariantes de negócio. **Esta skill não é dona de regra de arquitetura:** as regras moram no
`CLAUDE.md` e nas ADRs, e ela aponta para elas. **Os exemplos são arquivos reais do repositório**,
não código inventado — leia-os.

## Antes de escrever qualquer linha

Três perguntas. Elas mudam o desenho, não só o código.

**1. Qual serviço é dono deste dado?**
Se a resposta for "dois", o recorte está errado. Nenhum serviço lê o banco de outro — integração é
por API ou evento (`contracts/eventos.md`). O identificador da loja é `estabelecimentoId`
(ADR-035), em todo lugar.

**2. A operação exige permissão dentro de um estabelecimento?**
Quase tudo que o comerciante faz exige. O caso de uso recebe o usuário do token e confere a
permissão — **nunca confia no `estabelecimentoId` que veio da URL**. As permissões são o enum
`Permissao` do `merchant` (ADR-011). Como cada serviço confere:

- **no `catalog`**, pela porta `AutorizacaoComercialPort`, que devolve a lista de permissões
  (ADR-045) — exemplo: `backend/services/catalog-service/src/main/java/com/deliveryplatform/catalog/application/port/out/AutorizacaoComercialPort.java`;
- **no `merchant`**, por dentro, sem porta — exemplo: `backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/application/usecase/DefinirAreasDeEntregaService.java`.

**3. A operação toca dinheiro?**
Se toca, valor **nunca** vem do request: é recalculado no servidor a partir da fonte de verdade
(catálogo, área de entrega, pedido). Use o `Money` de `:value-types`
(`backend/value-types/src/main/java/com/deliveryplatform/valuetypes/Money.java`), nunca `BigDecimal`
solto e jamais `double`.

## Ordem de implementação: de dentro para fora

```
1. domain          →  2. application  →  3. infrastructure  →  4. api
   regra pura          caso de uso        persistência          HTTP
```

Começar pelo controller é o erro mais comum, e produz domínio contaminado por preocupação de
transporte.

## Onde cada coisa mora

`<nome>` é o serviço sem o sufixo: `merchant`, `catalog`, `order`.

| Camada | Caminho, dentro de `backend/services/<nome>-service/src/main/java/com/deliveryplatform/<nome>/` |
|---|---|
| Domínio | `domain/model/` — sem Spring, sem JPA, sem Jackson |
| Portas de saída, inclusive a do repositório | `application/port/out/` — **nunca `domain/repository/`** (`CLAUDE.md`, `## Regras que quebram o build se violadas`) |
| Caso de uso | `application/usecase/` |
| Persistência | `infrastructure/persistence/` — a entidade JPA ou o documento Mongo, **nunca** em `domain` |
| API | `api/controller/` |

**Migration:**

- **Flyway**, nos serviços de banco relacional: `src/main/resources/db/migration/V<n>__descricao.sql`.
  `ddl-auto` é `validate` e continua assim. Migration aplicada é **imutável**: correção é migration
  nova. Índice em toda coluna que filtra por estabelecimento, unicidade no banco, e recurso
  específico do PostgreSQL é permitido (ADR-014).
- **Mongock**, nos dois serviços de documento, `catalog` e `conversation`:
  `infrastructure/persistence/migration/`, com o mesmo rigor — exemplo: `backend/services/catalog-service/src/main/java/com/deliveryplatform/catalog/infrastructure/persistence/migration/V001CriaIndicesDoProduto.java`.

**Contrato:** `contracts/openapi/<nome>-service.json`. Hoje existem três: `catalog`, `identity` e
`merchant`. Serviço que ganha a primeira rota ganha o arquivo. Mudança incompatível derruba o
pipeline — é para isso que ele existe.

## O exemplo vivo: as áreas de entrega do `merchant`

Uma escrita de configuração inteira, de dentro para fora, já no repositório:

| Camada | Arquivo |
|---|---|
| Domínio | `backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/domain/model/AreaDeEntrega.java` |
| Caso de uso | `backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/application/usecase/DefinirAreasDeEntregaService.java` |
| API | `backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/api/controller/ConfiguracaoDaLojaController.java` |
| Migration | `backend/services/merchant-service/src/main/resources/db/migration/V3__cria_area_de_entrega.sql` |
| Teste de integração | `backend/services/merchant-service/src/test/java/com/deliveryplatform/merchant/integration/AreasDeEntregaIT.java` |

A rota é `PUT /api/v1/merchants/{estabelecimentoId}/settings/areas-entrega`, com a **lista inteira**
no corpo (ADR-081). *Leia os cinco antes de escrever o seu.*

## As regras que valem em toda camada

- **Invariante verificada na construção**, não em teste: objeto inválido não chega a existir.
- **A recusa do domínio:** o domínio lança `IllegalArgumentException`, e o caso de uso a traduz, num ponto só, numa exceção com motivo — como `backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/application/usecase/DefinirAreasDeEntregaService.java`, que a traduz em `AreasRecusadas`. **Nunca** um tratador de `IllegalArgumentException`, que faria de todo defeito um erro do cliente (`CLAUDE.md`, `## Armadilhas deste repositório`; ADR-060).
- **Enum, nunca `String` livre**, para status, método, modalidade e papel.
- **Máquina de estado como tabela de transições válidas**; transição fora da tabela lança exceção
  (`docs/dominio/pedido.md` é o modelo).
- **Injeção por construtor**, campos `final`. Nada de `@Autowired` em campo.
- **Evento:** o caso de uso grava domínio **e** outbox na mesma transação — nunca publica direto no
  broker. Quem consome registra em `processed_messages` antes do efeito.
- **DTO é `record`** com Bean Validation; entidade nunca é `@RequestBody`.
- **Listagem paginada**, com `Pageable`; nunca `findAll()` cru.
- **Erro em `ProblemDetail`** (RFC 7807); nunca stack trace na resposta.

## Os códigos de status

A régua mora em `CLAUDE.md`, `## Convenções de código` (transição fora da tabela → 409), e na ADR-063 (regra do comerciante é 400; defeito nosso é 500). Em resumo, sem substituí-la:

- **400**: erro de quem chamou · **409**: o estado do mundo não permite · **401**: sem token.
- **Recurso de outra loja é 404, com o mesmo corpo do inexistente** — nunca 403, que vazaria a
  existência (ADR-056). **O 403 fica com um significado só:** *"você não tem acesso a esta loja, ou não tem a permissão exigida."*.
- Criação → 201 · exclusão lógica → 204.

## Testes obrigatórios

| Camada | Onde | O que verificar |
|---|---|---|
| **Domínio** | `src/test/java/.../unit/` | Invariante rejeita objeto inválido; transição fora da tabela lança exceção; cálculo de valor bate, inclusive arredondamento |
| **Caso de uso** | `src/test/java/.../unit/` | Permissão negada impede o efeito; porta externa substituída |
| **Integração** | `src/test/java/.../integration/`, classe terminada em `IT` | Migration roda; consulta filtra por estabelecimento; unicidade dispara |
| **API** | `src/test/java/.../integration/`, classe terminada em `IT` | O código de sucesso; 403 sem acesso à loja; **404 para recurso de outra loja**; 400 com payload inválido; 409 em conflito |

Teste que toca banco usa **Testcontainers**, contra a mesma imagem de produção. Não existe H2 neste
repositório.

**O teste de isolamento é obrigatório e frequentemente esquecido:** usuário da loja A pede o recurso
da loja B → **404, com o mesmo corpo do inexistente** (ADR-056). Exemplo: `backend/services/catalog-service/src/test/java/com/deliveryplatform/catalog/integration/ConsultaDeProdutoIT.java`.

## Checklist antes do commit

- [ ] `domain` não importa Spring, JPA, Hibernate nem Jackson
- [ ] A porta do repositório está em `application/port/out/`
- [ ] Persistência em `infrastructure/persistence/`
- [ ] Injeção por construtor, campos `final`
- [ ] DTO é `record` com Bean Validation; entidade não é `@RequestBody`
- [ ] Enum no lugar de `String` livre
- [ ] Valor monetário usa `Money`; nenhum valor veio do request
- [ ] `estabelecimentoId` da URL confrontado com o usuário autenticado
- [ ] Migration criada; nada de `ddl-auto`
- [ ] Listagem paginada
- [ ] Erro em `ProblemDetail`
- [ ] Se publica evento: outbox na mesma transação
- [ ] Se consome evento: `processed_messages` antes do efeito
- [ ] Teste de isolamento entre estabelecimentos, com **404**
- [ ] `contracts/openapi/<nome>-service.json` atualizado
- [ ] Se contradiz uma ADR: a ADR ganhou emenda na mesma alteração
- [ ] `./gradlew :services:<nome>-service:test`, de dentro de `backend/`, passa

## O que o build pega e o que ele não pega

**O `HexagonalArchitectureTest` derruba o build** em todos os serviços: import de framework
no domínio, anotação de persistência em classe de domínio, dependência na direção errada.

**Ninguém pega para você** — revise à mão:

| Erro | Consequência |
|---|---|
| Confiar no `estabelecimentoId` da URL | Vazamento entre estabelecimentos |
| Responder 403 a recurso de outra loja | Vaza que o recurso existe (ADR-056) |
| Aceitar preço ou total do request | Cliente escolhe quanto paga |
| `String` em vez de enum para status | Typo vira bug silencioso em produção |
| `findAll()` sem paginação | Rota inviável com volume real |
| Publicar direto no broker | Evento perdido quando a transação falha |
| Consumir sem deduplicar | Efeito duplicado no primeiro retry |
| Alterar migration já aplicada | Ambientes divergem em silêncio |
| Mudar comportamento que uma ADR decidiu | A documentação passa a mentir |

## Referências

- `CLAUDE.md` — invariantes e convenções do repositório
- `docs/PRD.md` — premissas de negócio e o que está fora de escopo
- `docs/architecture/decisions/` — decisões, com alternativas e consequências
- `.claude/skills/metodo-delivery-platform/` — como se trabalha numa rodada

## Como conferir esta skill

Todo caminho completo citado aqui tem de existir. Da raiz do repositório:

```bash
grep -o '`[^` ]*`' .claude/skills/novo-caso-de-uso/SKILL.md | tr -d '`' | grep -E '^(backend|contracts|docs|frontend|\.claude)/' | grep -v '<' | sort -u | while read -r f; do if [ -e "$f" ]; then echo "ok    $f"; else echo "FALTA $f"; fi; done
```

Uma linha `FALTA` é a skill desatualizada: conserte-a na mesma rodada que moveu o arquivo.
