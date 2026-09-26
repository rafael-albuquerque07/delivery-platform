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

### `VinculoAlteradoV1` — o contrato

**Implementado na rodada C-B (ADR-043).** Origem `merchant-service` · exchange
`delivery.eventos` (topic, durável) · chave de rota `merchant.vinculo.alterado.v1`.

O vínculo de uma pessoa com um estabelecimento mudou: papel, estado ou
permissões. É o evento que faz revogação de acesso valer em segundos em vez de
esperar o prazo de um cache.

**Quando é emitido.** Em toda escrita de equipe, uma linha de outbox por
operação, na mesma transação do fato:

| Operação | O que muda |
|---|---|
| `promover` | papel |
| `rebaixar` | papel |
| `suspender` | estado → `SUSPENSO` |
| `reativar` | estado → `ATIVO` |
| `remover` | estado → `REMOVIDO` |
| `sair` | estado → `REMOVIDO` |
| `alterarPermissoes` | permissões |
| aceite de convite | o vínculo nasce (ou volta) `ATIVO`, `COLABORADOR` |

**Formato.** O envelope comum de `events/_envelope-v1.json`; o `eventType` não
carrega a versão, que vive em `eventVersion`:

```json
{
  "eventId": "4f1b0a1e-7d4c-4a2e-9f0b-2c6d8e3a1b55",
  "eventType": "VinculoAlterado",
  "eventVersion": 1,
  "occurredAt": "2026-09-24T14:03:11.482913Z",
  "correlationId": "4f1b0a1e-7d4c-4a2e-9f0b-2c6d8e3a1b55",
  "payload": {
    "estabelecimentoId": "1c9c1f2e-3b44-4a71-9f2a-6b0d5e8c4a10",
    "usuarioId": "8d2a5f31-90c7-4b6e-a1d3-77f2e0b9c481",
    "membroId": "b0a4c7e2-51d8-42f9-8c33-1e6a9d0f5b27",
    "papel": "COLABORADOR",
    "estado": "SUSPENSO",
    "permissoes": ["ALTERAR_STATUS", "VER_PEDIDO"]
  }
}
```

**As quatro cláusulas.**

1. **O payload é estado, não delta.** `papel`, `estado` e `permissoes` descrevem
   o vínculo **depois** da mudança, inteiro. Aplicar duas vezes chega ao mesmo
   lugar, e quem perdeu um evento se conserta sozinho no próximo.
2. **`permissoes` é a lista completa, e ausência é negação.** Quem aplica
   **substitui** a lista que tinha; não faz união. Tratar a lista como
   incremento transforma revogação em concessão permanente.
3. **O consumidor é idempotente por `eventId`.** A entrega é pelo menos uma vez
   (ADR-043 §3); o `eventId` é a chave primária da linha de outbox e nunca se
   repete.
4. **O consumidor descarta evento velho por `occurredAt`.** A ordem de publicação
   não é garantida (ADR-043 §4). Para cada par `(estabelecimentoId, usuarioId)`,
   guarda-se o `occurredAt` do último evento aplicado e ignora-se qualquer
   anterior — senão uma suspensão pode ser desfeita por um evento mais velho que
   chegou depois.

**O que não carrega.** Nome e telefone: são dado do `identity-service` (ADR-001),
e o `usuarioId` basta para dizer *o que a pessoa pode fazer*. A tabela `outbox` é
cópia durável do evento, e a regra do `CLAUDE.md` sobre log vale para ela.

**Quem consome.** Ninguém ainda. O cache de autorização da ADR-011 mora em cada
serviço que pergunta (emenda na ADR-043), e o primeiro serviço com rota protegida
é o gatilho escrito.

### `ExpedienteAlteradoV1` — o contrato

**Implementado na rodada F (ADR-046).** Origem `merchant-service` · exchange
`delivery.eventos` (topic, durável) · chave de rota
`merchant.expediente.alterado.v1`.

O expediente de um estabelecimento mudou. Hoje o único motivo produzido é a
abertura.

**Quando é emitido.** Uma varredura no `merchant` percorre os estabelecimentos a
cada minuto e, para cada um que está **dentro do horário** e cujo expediente
corrente ainda não foi publicado, grava a marca d'água e o evento **na mesma
transação**.

**Dentro do horário, e não "aberta"** — pausa acontece *dentro* de um expediente
e não abre outro (`estabelecimento.md` §4). Uma loja pausada no instante da
abertura publica a abertura assim mesmo; não fizesse isso, o expediente dela
nunca abriria e os produtos ficariam esgotados o dia inteiro.

**Formato.** O envelope comum de `events/_envelope-v1.json`, com o `eventType`
sem a versão:

```json
{
  "eventId": "9a3c7f10-2b58-4d6e-b1c4-5e0f8a2d3b71",
  "eventType": "ExpedienteAlterado",
  "eventVersion": 1,
  "occurredAt": "2026-09-26T21:00:04.117293Z",
  "correlationId": "9a3c7f10-2b58-4d6e-b1c4-5e0f8a2d3b71",
  "payload": {
    "estabelecimentoId": "1c9c1f2e-3b44-4a71-9f2a-6b0d5e8c4a10",
    "motivo": "ABERTURA_DE_EXPEDIENTE",
    "expedienteDeReferencia": "2026-09-26"
  }
}
```

**As quatro cláusulas.**

1. **`occurredAt` e `expedienteDeReferencia` são coisas diferentes.** O
   primeiro é o instante da publicação; o segundo é o **dia operacional do
   início da faixa** que estava aberta (ADR-025, ADR-046 emendada), calculado
   no fuso da loja com hora de corte às 04:00. Às 01:30
   de domingo o instante é domingo e o expediente é sábado. **Quem usar o
   carimbo do envelope como dia vai errar uma vez por dia, na madrugada** — que
   é exatamente quando a pizzaria está vendendo.
2. **A reativação compara, nunca calcula.** O consumidor reativa produto e
   opção com `estado == ESGOTADO_HOJE ∧ expedienteDeReferencia != o que veio
   aqui`. Ele não precisa do fuso da loja nem da hora de corte, e não deve
   tentar derivá-los: o cálculo tem um dono só, o `merchant` (ADR-046 §6).
3. **O consumidor é idempotente por `eventId`.** A entrega é pelo menos uma vez
   (ADR-043 §3). A marca d'água do produtor garante que **uma abertura gera um
   evento**, não que **um evento chega uma vez**.
4. **`motivo` desconhecido é ignorado, não é erro.** Hoje só existe
   `ABERTURA_DE_EXPEDIENTE`; fechamento, pausa e retomada entram quando tiverem
   produtor. Acrescentar valor a enum é mudança compatível (ADR-027), e um
   consumidor que estoure com valor novo transforma uma mudança compatível em
   incidente.

**Uma abertura por dia operacional, e não por transição.** A loja que abre duas
vezes no mesmo dia — a padaria de 6h–14h e 18h–22h — publica **um** evento, o da
primeira. A marca d'água é `(estabelecimento, expediente)`, e a segunda abertura
do mesmo dia operacional não insere linha.

Isto **emenda o exemplo da ADR-025 §5**, que descrevia o catálogo recebendo dois
eventos e descartando o segundo por comparação. O resultado é o mesmo — o pão
que acabou no almoço continua acabado no jantar — mas por um caminho mais curto:
o produtor não chega a emitir.

**O que não carrega.** O estado de abertura da loja. Quem quiser saber se ela
está aberta **agora** pergunta pela `OperacaoDoEstabelecimentoPort`
(`estabelecimento.md` §3) — este evento diz que um expediente começou, não que a
loja segue aberta. Guardar o segundo como projeção seria manter, do lado de fora,
um campo que o `merchant` deliberadamente não guarda do lado de dentro.

**Quem consome.** Ninguém ainda. O primeiro consumidor é o `catalog-service`, no
marco 2, e é ele o único que reage à abertura com regra de domínio — reativa
`ESGOTADO_HOJE` (`catalogo.md` §3). O `order` e o `conversation` também têm
comportamento escrito, para qualquer `motivo`: invalidar a cache de operação da
loja (`pedido.md` §8) e responder aberto/fechado (`conversa.md` §14).

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
