# Matriz de eventos — quem publica, quem assina

**Fonte única do pareamento.** Os documentos de domínio dizem **o que** cada
serviço faz com um evento; este arquivo diz **quem publica e quem assina**.
Quando os dois divergirem, é defeito — corrija na mesma alteração.

**Base:** ADR-027 (compatibilidade), ADR-031 (nome único), ADR-032 (a guarda
pergunta)

> **Por que a matriz mora aqui e não nos documentos de domínio.** Espalhada por
> oito arquivos, ela não tinha dono: cada produtor declarava consumidores que
> nenhum consumidor listava, e a divergência só aparecia quando alguém cruzava
> tudo à mão. A verificação de build do marco 3 (ADR-031) precisa de uma lista
> para ler, e esta é ela.

---

## 1. Eventos entre serviços

| Evento | Publica | Assinam |
|---|---|---|
| `PedidoRecebidoV1` | `order` | `conversation` |
| `PedidoConfirmadoV1` | `order` | `conversation` |
| `PedidoPagoV1` | `order` | `conversation` |
| `PedidoProntoV1` | `order` | `delivery`, `conversation` |
| `PedidoSaiuParaEntregaV1` | `order` | `delivery`, `conversation` |
| `PedidoEntregueV1` | `order` | `delivery`, `settlement`, `conversation` |
| `PedidoRetiradoV1` | `order` | `conversation` |
| `PedidoCanceladoV1` | `order` | `delivery`, `payment`, `conversation` |
| `DevolucaoDevidaV1` | `order` | `payment`, `settlement` |
| `LiquidacaoConfirmadaV1` | `payment` | `order`, `settlement` |
| `CobrancaExpiradaV1` | `payment` | `order` |
| `EstornoExecutadoV1` | `payment` | `order` |
| `EstabelecimentoCriadoV1` | `merchant` | `conversation` |
| `VinculoAlteradoV1` | `merchant` | **todos** |
| `ConfiguracaoOperacionalAlteradaV1` | `merchant` | `order`, `delivery`, `conversation` |
| `ExpedienteAlteradoV1` | `merchant` | `catalog`, `conversation`, `order` |
| `AreasDeEntregaAlteradasV1` | `merchant` | `conversation` |
| `VinculoEntregadorAlteradoV1` | `merchant` | `delivery` |
| `ProdutoPublicadoV1` | `catalog` | `conversation` |
| `ProdutoAlteradoV1` | `catalog` | `conversation` |
| `ProdutoDespublicadoV1` | `catalog` | `conversation` |
| `DisponibilidadeAlteradaV1` | `catalog` | `conversation` |
| `CategoriasReordenadasV1` | `catalog` | `conversation` |
| `JornadaAbertaV1` | `settlement` | `order`, `delivery` — invalidação de cache (ADR-033) |
| `JornadaFechadaV1` | `settlement` | `order`, `delivery` — invalidação de cache (ADR-033) |

**`VinculoAlteradoV1` é o único com consumidor coletivo**, e é infraestrutura, não
domínio: todo serviço que resolve permissão invalida a entrada de cache ao
recebê-lo. O mecanismo é descrito uma vez, em `docs/dominio/estabelecimento.md`
§3, e não se repete nos oito documentos de domínio.

**O `settlement` publica dois eventos, e só para invalidar cache.** Nenhum
consumidor mantém projeção de jornada: a verdade continua no `settlement` e é
consultada por porta síncrona; o evento é o caminho rápido e o TTL é a rede de
segurança (ADR-033). É a mesma forma do `VinculoAlteradoV1` na ADR-011.

---

## 2. Eventos consumidos pelo painel

Não são integração entre serviços. Alimentam tela, e a ausência de consumidor de
backend **é esperada** — não é furo de pareamento.

| Evento | Publica | Para quê |
|---|---|---|
| `EntregadorRetornouV1` | `delivery` | Painel de despacho |
| `ConversaEscalonadaV1` | `conversation` | Fila de atendimento humano |
| `CustoDeConversaExcedidoV1` | `conversation` | Alerta de teto |
| `EntregaDevolvidaV1` | `delivery` | Painel — o item voltou à loja |

---

## 3. O que deliberadamente **não** tem evento

Registrado para que a ausência seja decisão, e não esquecimento.

| Transição ou fato | Por que não publica |
|---|---|
| T20 · `SAIU_PARA_ENTREGA → NAO_ENTREGUE` | Ninguém precisa saber. O `delivery` modela tentativa dentro da entrega; o `settlement` **pergunta** em vez de escutar — ADR-032 |
| T21 · `NAO_ENTREGUE → SAIU_PARA_ENTREGA` | Idem |
| T17 · `PRONTO → AGUARDANDO_CLIENTE` | Retirada no balcão não move nenhum outro serviço |
| T04 · reserva de estoque | `AGUARDANDO_ESTOQUE` é inalcançável até o marco 10 |
| Fechamento de jornada | Fim de cadeia — §1 |
| `identity-service` — nenhum evento de domínio | Criar conta não é fato que outro serviço precise saber; o `merchant` descobre o usuário quando um vínculo é criado, e ninguém mais tem interesse. Detalhado em `docs/dominio/usuario.md` §5 |

---

## 4. Pendências desta matriz

| Item | Situação |
|---|---|
| `EntregaAtribuidaV1` → `conversation` | Declarado em `entrega.md` §9 com a ressalva de que **avisar o cliente é T16, não isto**. Sem comportamento escrito do lado do consumidor. Ou ganha um, ou o consumidor sai da declaração |
| `EntregaDevolvidaV1` | Corrigido na rodada da ADR-025 — o `settlement` não o consome, porque entrega devolvida significa pedido cancelado. Confirmar o consumidor atual, se houver |
| `TitularSolicitouExclusaoV1` | Nome escolhido na ADR-013 §7 para um evento que ainda não existe. Entra aqui quando existir |

---

## 5. Regra de manutenção

**Alterou produtor ou consumidor? Altere esta tabela na mesma alteração.**

Um evento novo entra aqui **antes** de ter esquema em `contracts/events/`. Um
consumidor novo entra aqui antes de existir código que assine. A ordem importa
porque é esta lista que a verificação de build lê — e verificação que corre atrás
da realidade não verifica nada.

A partir do marco 3, o build falha com:

- nome de evento declarado por mais de um serviço (ADR-031);
- evento nesta tabela sem esquema em `contracts/events/`;
- esquema em `contracts/events/` sem linha aqui.

E avisa, sem falhar, com evento cujo consumidor declarado não tem código que
assine — porque durante a construção de um marco isso é normal por algumas
semanas, e depois deixa de ser.

### Como esta tabela se constrói, e o erro que ela já cometeu

**A linha de consumidores é a união do que o produtor declara com o que cada
consumidor documenta.** Nunca só o lado do produtor.

A primeira versão desta matriz foi montada a partir das tabelas de eventos
publicados, e por isso herdou os furos delas: dois eventos tinham o `delivery`
como consumidor documentado em `entrega.md` desde sempre, e o `pedido.md` nunca
o declarou. A matriz reproduziu a omissão em vez de expô-la.

Construir de um lado só faz a verificação concordar com o defeito.
