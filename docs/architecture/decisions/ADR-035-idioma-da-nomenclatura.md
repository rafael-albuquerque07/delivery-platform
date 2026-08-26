# ADR-035 — Domínio em português, o resto em inglês, e o adaptador traduz

**Status:** Aceita — 26/08/2026
**Fecha a pendência de:** `docs/dominio/catalogo.md` §1 e §10 — "decidir antes do primeiro código"
**Relacionada:** ADR-012 (rotas do gateway), ADR-018 (modelo do catálogo em inglês), ADR-027 (envelope de evento)
**Precisa existir antes do marco 1** — a primeira classe de domínio se chama `Usuario` ou `User`

## Contexto

O `catalogo.md` §1 registra a divergência e propõe a resposta:

> A ADR-018 modela isto em inglês (`Product`, `optionGroups`, `minSelect`). Os
> documentos de domínio da v1.1 usam português — *bairro*, *troco*, *jornada*,
> *comanda* não têm tradução honesta. A divergência é real e precisa de decisão
> **antes do primeiro código**.

O §10 do mesmo documento a data como "antes do primeiro código do
`catalog-service`" — marco 2. **Essa data está errada**, e o erro é de
perspectiva: a nota foi escrita da cadeira do catálogo, porque é a ADR-018 que
está em inglês. Mas a decisão prende na **primeira classe de domínio que
existir**, e ela é `Usuario`, no marco 1.

### O estado atual é misto, e o mapa do misto é o argumento

| | Português | Inglês |
|---|---|---|
| Agregados e invariantes | tudo | — |
| Eventos de domínio | `PedidoRecebidoV1`, `LiquidacaoConfirmadaV1` | — |
| Envelope do evento | — | `eventId`, `eventType`, `occurredAt` |
| Portas | 7 | 2 — `CatalogPort`, `DeliveryQuotePort` |
| Value objects | `Faixa`, `VinculoSnapshot` | `Money` — 11 ocorrências |
| Modelo da ADR-018 | — | `Product`, `basePrice`, `minSelect`, `priceDelta` |
| Nomes de serviço | — | `order-service`, `merchant-service` |
| Rotas | — | `/api/v1/catalog/**` |

E a costura aparece dentro de uma linha só, na ADR-012:

```
/merchants/{estabelecimentoId}/...
 └ inglês  └ português
```

Isso não é desleixo. **É a fronteira do adaptador aparecendo no texto** — e a
decisão abaixo é reconhecê-la em vez de escolher um idioma para tudo.

## Decisão

### 1. A regra, em uma frase

> **O negócio fala português. O meio fala inglês. O adaptador traduz — que é
> literalmente a função dele.**

### 2. Quatro camadas, e onde cada uma cai

| Camada | Idioma | Exemplos |
|---|---|---|
| **Domínio** — agregados, entidades, value objects de negócio, invariantes, eventos de domínio, portas | **português** | `Usuario`, `Pedido`, `Liquidacao`, `Jornada`, `PedidoEntregueV1`, `AutorizacaoComercialPort` |
| **Padrões de engenharia** — vocabulário de arquitetura, não de negócio | **inglês** | `Money`, `Port`, `Adapter`, `Repository`, `Factory`, `Snapshot` |
| **Framework e infraestrutura** | **inglês** | `JpaRepository`, `SecurityFilterChain`, `eventId`, `occurredAt`, `correlationId` |
| **Superfície HTTP** — nome de serviço, prefixo de rota | **inglês** | `order-service`, `/api/v1/catalog/**` |

**A segunda camada é a que evita a discussão boba.** `Money` não é português mal
resolvido: é o nome de um padrão, como `Repository`. Traduzir para `Dinheiro`
não aproxima o código do negócio — afasta-o da literatura de onde o padrão veio.
A pergunta que separa as camadas é: **a Marli usaria essa palavra?** Ela diz
*troco*, *bairro*, *jornada*, *comanda*. Ela não diz *money*, e também não diz
*repositório*.

### 3. O identificador na rota é português, o segmento é inglês

```
/api/v1/merchants/{estabelecimentoId}/pedidos
       └ inglês    └ português          └ ?
```

O segmento `merchants` é endereço — parte do meio, junto com `/api/v1`. O
`{estabelecimentoId}` é **conceito de negócio atravessando a fronteira**, e
mantê-lo em português é o que faz a invariante 9 do `CLAUDE.md` — *identificador
da URL nunca é confiável* — falar da mesma coisa que o domínio chama de
`estabelecimentoId`, sem tradução no meio do raciocínio de segurança.

O terceiro segmento — o recurso — segue o **domínio**: `/pedidos`, não
`/orders`. O prefixo do serviço é do meio; o recurso é do negócio.

**Isto expõe uma divergência a resolver:** a ADR-012 escreve
`/merchants/{estabelecimentoId}` e o `estabelecimento.md` §3 escreve
`/api/v1/merchants/{merchantId}`. Os dois não podem estar certos. Vence
`{estabelecimentoId}`, por esta decisão.

### 4. Persistência segue o domínio

Tabela, coluna e índice em português: `usuario`, `estabelecimento_id`,
`idx_pedido_estabelecimento_estado`. O nome do arquivo de migração é misto por
construção, e está certo: `V1__cria_usuario.sql` — o prefixo é do Flyway, o
assunto é do negócio.

### 5. As exceções existentes, com prazo

| O quê | O que fazer | Quando |
|---|---|---|
| `DeliveryQuotePort` | → `CotacaoDeEntregaPort` | marco 3, quando o `order` a implementar |
| `CatalogPort` | → `CatalogoPort` | marco 2 |
| Modelo da ADR-018 — `Product`, `basePrice`, `minSelect`, `priceDelta` | **não se reescreve a ADR.** Emenda apontando para cá; o código nasce em português | marco 2 |
| `POST /internal/catalog/quote` e o payload dele | prefixo fica; campos do payload viram domínio — `estabelecimentoId`, `produtoId` | marco 2 |
| `ADR-020` — `AreaEntrega` com `merchantId`; `DeliveryQuote`; método `quote()` | O `estabelecimento.md` §5 já tem a versão vigente — `AreaDeEntrega`, `identificadorNormalizado`, sem `merchantId`. A ADR-020 **não se reescreve**; emenda apontando para o domínio, e o código segue o §5 | marco 3 |
| `ADR-018` — `stockControlled: boolean` | **Não é só idioma.** O domínio tem `modoDeControle`, enum de três estados (`SEM_CONTROLE \| QUALITATIVO \| QUANTITATIVO`). A ADR-018 é anterior ao enum, e traduzir o booleano fixaria um conceito que não existe | marco 2 |

**Nada disso é feito agora.** Renomear porta que ninguém implementou é editar
texto por gosto; o momento certo é quando o código encostar nela, e aí o
compilador confere.

## Consequências

**Positivas**

- A primeira classe do projeto tem nome decidido, e não escolhido por acaso pelo
  primeiro serviço a compilar.
- A regra é aplicável sem consultar ninguém: **a Marli usaria essa palavra?**
- O mapeamento de idioma coincide com a fronteira que a arquitetura hexagonal já
  desenha. Não há regra nova a lembrar — há uma regra existente que agora também
  vale para nomes.
- `{estabelecimentoId}` na rota deixa de ser inconsistência e passa a ser
  costura declarada.

**Negativas**

- **Código misto é feio na primeira leitura.** `UsuarioJpaRepository`,
  `PedidoController`, `Money valorEfetivo`. Quem chega estranha, e vai continuar
  estranhando — é o custo de o negócio não falar inglês.
- **A fronteira exige julgamento em casos raros.** `Snapshot` é padrão ou
  negócio? Decidi padrão, e `VinculoSnapshot` fica misto. Um caso ambíguo por
  trimestre é o preço.
- **Nomes longos.** `OperacaoDoEstabelecimentoPort` contra
  `MerchantOperationPort`. Aceito: o nome longo diz o que faz.
- **Quem for ler este código fora do Brasil não entende metade.** É consequência
  real e assumida — o produto atende comércio de bairro brasileiro, e o
  vocabulário dele é o que o código precisa refletir.

## Alternativas consideradas

- **Tudo em inglês.** É o que a maior parte da literatura assume, e o que a
  ADR-018 já fazia. Rejeitada porque *bairro*, *troco*, *jornada*, *comanda* e
  *liquidação* não têm tradução honesta — `neighborhood` e `change` perdem
  precisão, e `settlement` significa outra coisa em fintech. Traduzir força o
  código a falar de um negócio que não é este.
- **Tudo em português, inclusive framework.** `RepositorioDeUsuario`,
  `CadeiaDeFiltrosDeSeguranca`. Rejeitada: briga com toda a documentação do
  Spring, e nenhuma busca por erro encontra nada.
- **Português no domínio e inglês só no que o framework impõe**, sem a camada de
  padrões. Quase igual a esta decisão, e cai no debate `Money` versus `Dinheiro`
  toda vez que alguém cria um value object. A terceira camada existe para
  encerrar esse debate por escrito.
- **Adiar para o marco 2**, como o `catalogo.md` §10 propõe. Rejeitada: o marco 1
  escreve `Usuario` antes disso, e adiar significa decidir por acidente.

## Emendas que esta decisão provoca

**ADR-012** — a rota vira `/api/v1/merchants/{estabelecimentoId}/...`, e o
`estabelecimento.md` §3 concorda com ela em vez de escrever `{merchantId}`.

**ADR-018** — ganha emenda apontando para cá. O texto dela **não se reescreve**:
ele registra a decisão como foi tomada, em inglês, e a emenda diz que o código
nasce em português quando o `catalog-service` for escrito.

**`catalogo.md` §1 e §10** — a nota de nomenclatura e o item de pendência saem,
substituídos por referência a esta ADR.
