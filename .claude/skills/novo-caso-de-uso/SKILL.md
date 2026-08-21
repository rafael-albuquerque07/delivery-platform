---
name: novo-caso-de-uso
description: Implementa uma funcionalidade nova atravessando as camadas hexagonais de um microsserviço deste monorepo — domínio, caso de uso, persistência, API, migration e testes. Use ao adicionar endpoint, criar caso de uso, implementar história do PRD, expor recurso novo ou estender um serviço existente.
---

# Novo caso de uso

Procedimento para adicionar funcionalidade em qualquer serviço deste monorepo,
respeitando a arquitetura hexagonal e os invariantes de negócio.

## Antes de escrever qualquer linha

Responda três perguntas. Elas mudam o desenho, não só o código.

**1. Qual serviço é dono deste dado?**
Se a resposta for "dois", o recorte está errado. Nenhum serviço lê o banco de
outro — integração é por API ou evento. Confira o catálogo em
`docs/architecture/` antes de criar tabela em lugar duvidoso.

**2. A operação exige permissão dentro de um estabelecimento?**
Quase tudo que o comerciante faz exige. Nesse caso o caso de uso recebe o
`userId` do token e consulta a permissão pela porta de autorização — nunca
confia no `merchantId` que veio da URL.

**3. A operação toca dinheiro?**
Se toca, valor **nunca** vem do request. É sempre recalculado no servidor a
partir da fonte de verdade (catálogo, área de entrega, pedido). Use `Money`,
nunca `BigDecimal` solto e jamais `double`.

## Ordem de implementação: de dentro para fora

Escreva nesta ordem. Começar pelo controller é o erro mais comum e produz
domínio contaminado por preocupação de transporte.

```
1. domain          →  2. application  →  3. infrastructure  →  4. api
   regra pura          caso de uso        persistência          HTTP
```

---

## 1. Domínio

`domain/model/` — sem Spring, sem JPA, sem Jackson. O `HexagonalArchitectureTest`
derruba o build se você importar qualquer um deles.

```java
public record AreaEntrega(
        UUID id,
        UUID merchantId,
        String nome,
        Set<String> identificadores,
        Money taxa,
        boolean ativa
) {
    public AreaEntrega {
        Objects.requireNonNull(nome, "nome é obrigatório");
        if (taxa.isNegativa()) {
            throw new TaxaInvalidaException(taxa);   // exceção de domínio, não IllegalArgument
        }
    }
}
```

Regras que valem aqui:

- **Invariante verificada na construção**, não em teste. Objeto inválido não deve
  chegar a existir.
- **Enum, nunca `String` livre** para status, método, modalidade e papel.
- **Máquina de estado como tabela de transições válidas**, com transição fora da
  tabela lançando exceção de domínio.
- Interface do repositório fica em `domain/repository/`, definida em termos do
  domínio — não da tabela.

## 2. Caso de uso

`application/usecase/` — orquestra, não decide regra. A regra está no domínio.

```java
@Service
public class CadastrarAreaEntregaUseCase {

    private final AreaEntregaRepository repository;
    private final MerchantAuthorizationPort autorizacao;

    public CadastrarAreaEntregaUseCase(AreaEntregaRepository repository,
                                       MerchantAuthorizationPort autorizacao) {
        this.repository = repository;
        this.autorizacao = autorizacao;
    }

    @Transactional
    public AreaEntrega executar(UUID merchantId, UUID userId, DadosArea dados) {
        autorizacao.exigir(userId, merchantId, Permissao.MERCHANT_SETTINGS_UPDATE);
        var area = new AreaEntrega(UUID.randomUUID(), merchantId, dados.nome(), ...);
        return repository.salvar(area);
    }
}
```

- **Injeção por construtor**, campos `final`. Nada de `@Autowired` em campo.
- Dependência externa entra por **porta** declarada em `application/port/out/`.
- Se o caso de uso publica evento, ele grava domínio **e** outbox na mesma
  transação — nunca publica direto no broker.

## 3. Infraestrutura

### Entidade e mapeamento

`infrastructure/persistence/entity/` — a entidade JPA vive aqui, **nunca** em
`domain`. A duplicação é intencional: impede que decisão de mapeamento
contamine a regra.

```java
@Entity
@Table(name = "areas_entrega")
class AreaEntregaJpaEntity {
    @Id private UUID id;
    @Column(name = "merchant_id", nullable = false) private UUID merchantId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal taxa;
    // ...
}
```

O mapper em `infrastructure/persistence/mapper/` converte entidade ↔ domínio.

### Migration

Toda estrutura nasce em migration. `ddl-auto` é `validate` e continua assim.

```
services/<serviço>/src/main/resources/db/migration/V<n>__descricao.sql
```

Regras:

- Migration aplicada é **imutável**. Correção é migration nova.
- Índice em toda coluna usada para filtrar por estabelecimento.
- Restrição de unicidade no banco, não só na aplicação.
- Recursos específicos do PostgreSQL são permitidos — não há segundo dialeto a
  agradar (ADR-014).

Serviço documental usa `changeUnit` do Mongock, com o mesmo rigor.

## 4. API

`api/controller/` traduz HTTP em comando. Não contém regra.

```java
@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/areas-entrega")
class AreaEntregaController {

    @PostMapping
    ResponseEntity<AreaEntregaResponse> cadastrar(
            @PathVariable UUID merchantId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CadastrarAreaRequest request) {

        var area = useCase.executar(merchantId, UUID.fromString(jwt.getSubject()), request.paraDados());
        return ResponseEntity.status(HttpStatus.CREATED).body(AreaEntregaResponse.de(area));
    }
}
```

- **`record` para DTO**, com Bean Validation. Nunca entidade JPA como
  `@RequestBody`.
- **Paginação obrigatória** em listagem: `Pageable`, nunca `findAll()` cru.
- **Erro em `ProblemDetail`** (RFC 7807). Nunca stack trace na resposta.
- Códigos: criação → 201 · exclusão lógica → 204 · conflito de estado → 409 ·
  validação → 400 · sem token → 401 · sem permissão → 403.

## 5. Contrato

Atualize `contracts/openapi/<serviço>-api.yaml`. Mudança incompatível derruba o
pipeline — é para isso que ele existe.

## 6. Testes obrigatórios

Escreva nesta proporção. A maior parte da suíte roda em milissegundos.

| Camada | Onde | O que verificar |
|---|---|---|
| **Domínio** | `test/.../unit/` | Invariante rejeita objeto inválido; transição de estado fora da tabela lança exceção; cálculo de valor bate com o esperado, inclusive arredondamento |
| **Caso de uso** | `test/.../unit/` | Autorização negada impede o efeito; porta externa mockada |
| **Integração** | `test/.../integration/` | Migration roda; consulta filtra por estabelecimento; restrição de unicidade dispara |
| **API** | `test/.../integration/` | 201 no caminho feliz; 403 sem permissão; 400 com payload inválido; 409 em conflito |

Teste que toca banco usa **Testcontainers**, contra a mesma imagem de produção.
Não existe H2 neste repositório.

O teste de isolamento é obrigatório e frequentemente esquecido:

```java
@Test
void naoRetornaDadoDeOutroEstabelecimento() {
    // usuário do estabelecimento A pede o recurso do estabelecimento B
    // → 403, e nunca 404 com vazamento de existência
}
```

---

## Checklist antes de abrir o PR

- [ ] `domain` não importa Spring, JPA, Hibernate nem Jackson
- [ ] Entidade JPA está em `infrastructure/persistence/entity`
- [ ] Injeção por construtor, campos `final`
- [ ] DTO é `record` com Bean Validation; entidade não é `@RequestBody`
- [ ] Enum no lugar de `String` livre
- [ ] Valor monetário usa `Money`; nenhum valor veio do request
- [ ] `merchantId` da URL confrontado com o usuário autenticado
- [ ] Migration criada; nada de `ddl-auto`
- [ ] Listagem paginada
- [ ] Erro em `ProblemDetail`
- [ ] Se publica evento: outbox na mesma transação
- [ ] Se consome evento: `processed_messages` antes do efeito
- [ ] Teste de isolamento entre estabelecimentos existe
- [ ] OpenAPI atualizado
- [ ] Se contradiz uma ADR: a ADR foi atualizada na mesma alteração
- [ ] `./gradlew :services:<serviço>:test` passa

---

## O que o build pega e o que ele não pega

**O ArchUnit derruba automaticamente:** import de framework no domínio, anotação
de persistência em classe de domínio, dependência na direção errada.

**Ninguém pega para você** — revise à mão:

| Erro | Consequência |
|---|---|
| Confiar no `merchantId` da URL | Vazamento entre estabelecimentos |
| Aceitar preço ou total do request | Cliente escolhe quanto paga |
| `String` em vez de enum para status | Typo vira bug silencioso em produção |
| `findAll()` sem paginação | Endpoint inviável com volume real |
| Publicar direto no broker | Evento perdido quando a transação falha |
| Consumir sem deduplicar | Efeito duplicado no primeiro retry |
| Alterar migration já aplicada | Ambientes divergem em silêncio |
| Mudar comportamento que uma ADR decidiu | Documentação passa a mentir |

---

## Referências

- `CLAUDE.md` — invariantes e convenções do repositório
- `docs/PRD.md` — premissas de negócio e o que está fora de escopo
- `docs/architecture/` — fronteiras dos serviços e fluxos
- `docs/architecture/decisions/` — decisões, com alternativas e consequências
