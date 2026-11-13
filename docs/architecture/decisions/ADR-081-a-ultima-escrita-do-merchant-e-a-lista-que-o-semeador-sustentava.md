# ADR-081 — A última escrita do merchant, e a lista que o semeador sustentava

- **Status:** aceita
- **Data:** 12/11/2026
- **Rodada:** E-10
- **Relacionada:** ADR-020 (as áreas de entrega — o modelo), ADR-073 e ADR-080 (os irmãos de
  `/settings` — o molde), ADR-059, emendas de 16 e 17/10/2026 (a condição de saída dos
  semeadores), ADR-034 §1 (o `order` não consome o evento das áreas), ADR-053 (o contrato
  declara as recusas) e o `docs/dominio/estabelecimento.md` §5 e §7

## Contexto

**A ADR-020 decide o modelo e não decide rota.** A área é do estabelecimento — nome, faixas de
CEP opcionais, taxa em que zero é válido, ativa ou não —, a cotação fica atrás da
`DeliveryQuotePort`, e o pedido **congela `nomeAreaSnapshot` e `taxaSnapshot`** no fechamento.
*"Taxa zero é distinta de área não atendida."*

**O agregado estava pronto.** O `AreaDeEntrega` normaliza o próprio nome — o
`identificadorNormalizado` é derivado, sem acento, sem espaço duplo, em maiúsculas — e cobra a
E3 e a faixa repetida; o construtor do `Estabelecimento` cobra a **M9** entre todas as áreas e a
**M10** entre as ativas; a `FaixaDeCep` cobra o CEP. As duas tabelas existem desde a `V3`, e a
chave primária da área é o identificador normalizado. Uma loja criada por rota nasce sem área, e
**não havia como sair disso** — só a fixture gravava uma.

**Ninguém guarda o identificador de uma área.** O pedido congela nome e taxa por valor
(`pedido.md`, §1 e a tabela dos snapshots), e nenhum contrato cita `areaId`.

**Mas um evento está declarado.** O `estabelecimento.md` §7 diz *"Todos com `correlationId`,
todos via outbox na mesma transação da alteração"*, e lista `AreasDeEntregaAlteradasV1` — *"Área
criada, alterada, desativada"* —, com o `conversation` como consumidor, para a lista de bairros.
O `conversation` não tem código, e o evento não tem esquema em `contracts/events/`. A pergunta
foi levada ao dono do produto antes da rota.

## Decisão

### 1. O quarto sub-recurso irmão, e a lista substituída inteira

`GET` e `PUT` em `/api/v1/merchants/{estabelecimentoId}/settings/areas-entrega`, sob
`CONFIGURAR_LOJA`. O corpo é a lista inteira, ativas e desativadas, e **substitui** a que havia.
Substituir é seguro **porque nenhuma área tem quem a cite por identificador** — campo não tem
identidade, coleção tem, e esta coleção só é lida por valor. **A lista vazia é aceita**: é o
estado de nascença da loja.

### 2. O caso de uso não confere regra nenhuma

`Estabelecimento.comAreasDeEntrega` passa pelo construtor, que recobra a M9 e a M10. O caso de
uso constrói a faixa, a área e a loja, e troca a `IllegalArgumentException` de cada construção
pela `AreasRecusadas`, com o motivo de **qual** construção recusou — `CEP_INVALIDO`,
`AREA_INVALIDA`, `AREAS_EM_CONFLITO` —, no molde do `HorarioRecusado`. O tratador escreve a frase,
e é 400: o corpo é o erro de quem pediu. A M9 e a M10 saem do mesmo construtor e dividem uma
frase, como as três recusas da M17 na ADR-080.

### 3. A rota não publica o evento, e o documento passa a dizer isso

O `AreasDeEntregaAlteradasV1` **continua declarado e não é produzido**. Publicar agora seria um
evento sem consumidor com código e sem esquema — a peça de marco distante que o `CLAUDE.md`
registra como defeito. O `estabelecimento.md` §7 passa a dizer, na linha do evento, que ele não é
produzido, no molde da linha do `ExpedienteAlteradoV1` (*"Hoje só a abertura é produzida"*).

### 4. O teto de contextos de teste vale para a suíte inteira

`spring.test.context.cache.maxSize=16`, que a ADR-080 pôs no `merchant`, vai para o `catalog`,
o `identity` e o `gateway` — os outros três módulos com classe `@SpringBootTest`. Os cinco
serviços sem nenhuma não ganham nada. **Quatro arquivos com a mesma linha**, em
`src/test/resources/spring.properties` de cada módulo.

### 5. O que esta ADR não faz

**Não cria tela, invariante, migração nem evento.** **Não mexe nos semeadores.**

## Consequências

**A lista do que a fixture grava e a API não escreve esvazia** (linha 34 do razão): no
`merchant`, nada; no `catalog`, nada. **A condição da ADR-059, emenda de 16/10/2026 — *"a lista
acima esvaziar"* —, está paga.** O que ela anunciava — a semeadura por `ApplicationRunner` sair
do repositório — **não aconteceu**: os três semeadores continuam na árvore, atrás de
`DELIVERY_SEMEADURA`. A saída deles é decisão própria, e a linha 34 fica em *"Disparou e não
produziu nada"*, com a lista vazia escrita.

**Um documento de domínio declara um evento que nenhum código produz**, e diz isso por escrito.
O primeiro gatilho abaixo o fecha.

**A configuração do teto fica em quatro cópias.** Mover para o `build-logic` é decisão de build,
e não foi tomada aqui.

## Alternativas consideradas

**Recorte por área** — `POST`, `PUT` e `DELETE` em `/areas-entrega/{identificador}`.
**Recusada:** ninguém cita a área por identificador, e o recorte por área traria um quinto desenho
para um sub-recurso que é lido inteiro.

**Publicar o `AreasDeEntregaAlteradasV1` pelo outbox**, como o §7 manda. **Recusada pelo §3.**

**Mover o teto para o `build-logic`.** **Recusada nesta rodada:** é onde a configuração de build
mora, e merece leitura própria.

## Gatilho escrito

**O primeiro consumidor de `AreasDeEntregaAlteradasV1` com código — o `conversation`, ao montar
a lista de bairros.** Aí o evento ganha esquema em `contracts/events/` e nasce pelo outbox, na
mesma transação da rota desta ADR, e a linha do `estabelecimento.md` §7 volta a dizer só quando
ele sai.

**A primeira tela que monte as áreas de entrega.** Aí a rota desta ADR ganha chamador, e a
`CONFIGURAR_LOJA` passa a ter uma segunda tela — o gatilho da linha 103 do razão.
