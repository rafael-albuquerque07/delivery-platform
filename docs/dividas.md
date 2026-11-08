# O razão das dívidas

**Gerado em 10/10/2026 na rodada R-1, sobre o commit `6fb73e6`; refeito na E-1, sobre `d7dbcc4` mais a ADR-060, na W-E, sobre `398d828` mais a ADR-061, na E-3b, sobre `9fbcee1` mais a ADR-064, na E-3c, sobre `cbd633c` mais as ADRs 065 e 066 e o `PublicarProdutoService`, na E-3d, sobre `a3ffe07` mais a ADR-068, na W-F, sobre `ec89666` mais a ADR-067, na E-2, sobre `ef82bc9` mais a ADR-069, na W-G, sobre `92e8099` mais a ADR-070, na R-2, sobre `e43d227` mais a emenda de 16/10/2026 à ADR-012, na R-3, sobre `0e545c0` mais as emendas às ADRs 024 e 038 e as frases trocadas no lugar, na R-4, sobre `4904159` mais a ADR-072 e a emenda de 16/10/2026 à ADR-071, na R-5, sobre `ab18956` mais as emendas de 16/10/2026 às ADRs 071 e 072, na R-6, sobre `2b57cc9` mais a emenda de 16/10/2026 à ADR-059, na R-7, sobre `60eb66b`, com a terceira passada, na E-4, sobre `23e99a4` mais a ADR-073 e as emendas de 17/10/2026 às ADRs 011, 027 e 059, na E-5, sobre `c384446` mais a emenda de 17/10/2026 à ADR-073, na W-H, sobre `d61668f` mais a ADR-074, na R-8, sobre `10bc48f` mais a armadilha estendida do `CLAUDE.md`, e na P-1, sobre `09e08ae` mais a ADR-075, e na W-I, sobre `ebc0793` mais a ADR-076, e na E-6, sobre `3e4254c` mais a ADR-077, e na E-7, sobre `2b6e909` mais a ADR-078 e as emendas de 08/11/2026 às ADRs 065 e 077. Este arquivo é
derivado e envelhece:** toda ADR, documento ou javadoc que escrever um gatilho novo o
torna incompleto. Refaça-o com os comandos abaixo antes de confiar nele.

```bash
# os candidatos — toda menção a gatilho, em documento e em código
grep -rn -i "gatilho" docs/ contracts/ CLAUDE.md README.md frontend/README.md --include=*.md --exclude=dividas.md
grep -rn -i "gatilho" backend/ frontend/src/ --include=*.java --include=*.ts --include=*.tsx | grep -v "/build/\|/node_modules/\|\.tsbuild"

# o recorte estreito, que é o que a R-1 mandou contar
grep -rn "Gatilho escrito\|gatilho escrito" docs/ contracts/ CLAUDE.md backend/ frontend/ --include=*.md --include=*.java --include=*.ts --include=*.tsx --exclude=dividas.md | grep -v "/build/\|/node_modules/" | wc -l
```

**Segunda passada, pelas formas em prosa.** O comando acima só acha o que usa a fórmula.
Condição escrita como *"nesta altura"*, *"por enquanto"*, *"ainda não"*, *"quando houver"*
é gatilho também, e some deste arquivo — a da `RegraDoCatalogoViolada` venceu na G-E e
nunca apareceu aqui:

```bash
grep -rn -i "nesta altura\|por enquanto\|ainda não existe\|quando houver\|até que haja" \
  docs/ backend/*/*/src/main --include=*.md --include=*.java --exclude=dividas.md \
  | grep -v "/build/"
```

**O `--exclude=dividas.md` não é detalhe.** Sem ele a varredura conta **este arquivo**: o
cabeçalho que a descreve e as linhas que copiam as fórmulas viram ocorrências, e o número
cresce quando o razão cresce. Medido na W-F — uma das ocorrências novas era a frase escrita
no cabeçalho na mesma rodada. **Instrumento que se mede a si mesmo não está medindo.**

Medido na E-3b: **32 linhas**; na E-3c, **36** — que já envelheceu na própria rodada: a frase abaixo, escrita depois da medição, levou o número a **37**, e a E-3d mediu 37, com a mesma contagem por arquivo; na W-F, **38**, e a nova é a ADR-067:23 — *"a seção, por enquanto, não significa nada"* —, condição que a própria rodada desfez ao dar tela à seção — e que também envelheceu na própria rodada: a frase desta linha, ao citar *"por enquanto"*, virou ocorrência, e o número real da W-F é **39**; a E-2 mediu **39**, com a mesma contagem por arquivo — mais duas da ADR-063 (que cita a frase vencida), uma da ADR-066 e três deste próprio razão, que cita as fórmulas; menos a da `RegraDoCatalogoViolada`, cuja frase vencida saiu na E-3b. Nenhuma é sobre a publicação. **Até a W-G o comando não excluía este arquivo**, e os números acima o contam. Na R-2, sobre `e43d227`: **39** sem a exclusão, **32** com ela — sete eram este razão. Das 32, três já tinham linha (43, 44 e 63); vinte foram triadas, e **as vinte eram peça, nenhuma estado de dado**: seis venceram, onze não, três não dá para dizer. Doze delas viraram as linhas 79 a 86. **Nove ficaram por triar**, pelo teto de vinte: `estabelecimento.md`:441 e :467, `premissas-do-front.md`:140, `mensagem-na-fila-morta.md`:124, `reconciliacao-de-pagamento.md`:161, `recuperacao-de-acesso.md`:135, `ConfiguracaoDaEscuta.java`:23, `ExpedienteCorrenteResponse.java`:15 e `Equipe.java`:313. **Na R-3 as nove foram triadas**, e a segunda passada passou a **31**: saíram `catalogo.md`:279 e `estabelecimento.md`:441, frases falsas trocadas no lugar, e entrou a ADR-038:133, que cita na emenda a frase do corpo. O que a segunda passada
achar entra na tabela **com a fórmula**, não com a paráfrase.

**O que a segunda passada acha, e o que disso é dívida.** O comando colhe de propósito mais do
que precisa, e colhe **duas espécies diferentes**: *condição que espera* e *frase que já está
errada*. A triagem separa, nesta ordem:

1. **A condição já venceu e a frase ainda a afirma?** → **não é dívida: é frase falsa.** Ela
   não espera nada; está errada agora. **Conserta-se o texto** — no lugar, se for descrição;
   por emenda datada, se for ADR. *Vem ao razão como linha paga, para a história; mas a entrega
   é o texto consertado, e a linha sozinha não basta. Aprendido na R-3.*
2. **O sujeito da frase é uma peça do sistema** — uma rota, uma porta, um consumidor, um
   índice: **é gatilho.** *"O catálogo ainda não tem rota."*
3. **O sujeito é o estado de um dado:** **não é dívida, e nunca será.** *"Esta loja ainda não
   tem produto."* A loja pode ficar vazia para sempre, e isso não é débito de ninguém.
4. **A frase descreve uma consequência, não uma pendência** — ela explica o efeito de uma
   decisão e não pede nada: **fica fora.** *Foi o ramo que o uso achou, e o que mais cortou.*
5. **Não dá para dizer qual dos quatro:** fica **fora**, e volta quando alguém souber. Linha
   que entra sem se saber o que a dispara é ruído dentro do instrumento que existe para não ter
   ruído.

Os ramos 3, 4 e 5 vão à seção *"Da segunda passada, e deliberadamente fora do razão"*, no fim
deste arquivo, com `arquivo:linha` — não só ao relatório da rodada.

**Os tetos, e o que cada um limita.** A **triagem** para em vinte linhas por rodada: lista
longa demais vira carimbo. A **correção pelo ramo 1 não tem teto quando a medição já está
feita** — *deixar de pé uma frase que se sabe falsa, por economia de rodada, é o quadragésimo
sétimo defeito; e arquivá-la como dívida para depois é o quinquagésimo primeiro.*

Triado na R-2 e na R-3.

**Os comandos produzem candidatos; a tabela é a leitura deles.** Uma menção a
"gatilho" pode ser a definição de um, a citação de um que já está na tabela, ou o
relato de um que disparou. A conta, em 08/11/2026, na E-7:

| | linhas |
| --- | --- |
| o recorte estreito devolve | **100** |
| — cópias em `frontend/.tsbuild/`, saída de build que o filtro não exclui | −3 |
| — citação de um gatilho que já tem linha própria (ADR-045:17, ADR-054:15, ADR-055:12, ADR-059:21, `pedido.md`:397; e, na W-E, `PainelPage.tsx`:28 e as linhas 8, 18, 20 e 142 da ADR-061; e, na E-3b, `RegraDoCatalogoViolada`:25, que cita a linha 55; e, na E-3c, ADR-065:138, que relata o disparo da linha 56; e, na E-2, `estabelecimento.md`:234, que cita a linha 71) | −13 |
| — o mesmo gatilho repetido em outro lugar, ou substituído por outro (ADR-045:123, que a ADR-054 substituiu; ADR-043:210, ADR-057:101, ADR-058:91, `ProdutoResponse`:30, `MarcarDisponibilidadeService`:64, `ContratoDeErrosIT`:73 e :105, `ReativacaoNoExpedienteIT`:58; e, na E-3d, ADR-068:57, a §1 repetindo o primeiro gatilho da seção; e, na W-F, ADR-067:95, a §4 repetindo o primeiro da seção; e, na R-6, a ADR-059, emenda de 16/10/2026, que substitui a condição da linha 34 — a cópia do `como-subir-local.md`:480 virou ponteiro e saiu do comando) | −12 |
| — menção que nega um gatilho, e não o escreve (ADR-064:26, *"não havia gatilho escrito"*; e, na R-3, a ADR-012, emenda de 16/10 (2), *"Gatilho escrito: nenhum"*; e, na R-4, a ADR-071, emenda de 16/10, e a ADR-072, seção de gatilhos, *"Nenhum."*; e, na R-5, as emendas de 16/10/2026 (2) à ADR-071 e de 16/10/2026 à ADR-072, *"Gatilho escrito: nenhum."*; e, na E-4, as emendas de 17/10/2026 às ADRs 011, 027 e 059; e, na E-5, a emenda de 17/10/2026 à ADR-073; e, na P-1, a ADR-075, *"Nenhum."*) | −11 |
| — menção ao título da seção, que fala dela e não escreve gatilho (R-4: ADR-072:13, :16, :27, :42, :65, :81 e :86; ADR-071:214, o título da emenda de 16/10; e o `README.md` das decisões :115 e :118; e, na R-8, o `CLAUDE.md`:347, a armadilha que estende a regra da coluna à seção). *A decisão sobre o título acrescentou dez linhas ao comando que lê o título* | −11 |
| **= linhas da tabela vindas do recorte estreito** | **50** |
| + entraram por outra redação — *"com gatilho:"* (linhas 1 e 2), *"Gatilho para trazê-lo de volta"* (4), *"Quando o segundo chegar"* (6), *"O gatilho para mudar"* (11), *"O gatilho para reabrir"* (19), *"O gatilho, escrito"* (22), *"Gatilho:"* (40 e 42), *"O gatilho para a função mudar de lugar"* (41), *"volta quando houver"* (43), *"Decidir antes do marco 5"* (44), *"Gatilho novo, e são dois"* (20 e 48); e, na R-3, *"Isso é linha de razão, com gatilho:"* (93) | **+15** |
| + segundos gatilhos dentro de uma seção "## Gatilho escrito", que o comando conta uma vez só, pela linha do título (ADR-057, ADR-058, ADR-059 — linhas 45 a 47; ADR-060 — linhas 50 e 51; ADR-061 — linha 54; ADR-063 — linha 56; ADR-064 — linhas 58, a outra metade do primeiro, 59 e 60; ADR-065 — linha 62; ADR-066 — linhas 63 e 65; ADR-068 — linhas 68 e 69; ADR-069 — linhas 74 e 75; ADR-070 — linha 77; ADR-071 — linhas 95 e 96; ADR-073 — linhas 99 a 101; ADR-074 — linha 103; ADR-076 — linha 105; ADR-077 — linha 107; ADR-078 — linha 109) | **+27** |
| + escrito neste razão, porque a condição não tem outro lugar onde morar (linhas 52, 72 e 78) | **+3** |
| + entraram pela segunda passada, triada na R-2 — doze linhas em oito: ADR-024:140 (79), ADR-063:29 e :30 (80), ADR-067:23 (81), `catalogo.md`:279 (82), ADR-011:114 e ADR-048:247 (83), ADR-013:232 e ADR-031:173 (84), ADR-021:76 e `conversa.md`:291 (85), ADR-045:142 (86) | **+8** |
| + entraram pela segunda passada, triada na R-3 — `estabelecimento.md`:441 (88), `mensagem-na-fila-morta.md`:124 (89), `reconciliacao-de-pagamento.md`:161 (90), `recuperacao-de-acesso.md`:135 (91), `Equipe.java`:313 (92) | **+5** |
| + entrou pela segunda passada, reclassificada na R-4 — `mensagem-na-fila-morta.md`:124, que a R-3 tinha dado como a condição da 43 (97) | **+1** |
| − item que uma emenda declarou não ser gatilho — a 96, estornada pela emenda de 16/10/2026 à ADR-071 (R-4); e a 94 e a 95, estornadas pela emenda de 16/10/2026 (2) à ADR-071 (R-5) | **−3** |
| **= linhas da tabela que contam** | **106** |

**A tabela tem 109 linhas numeradas:** as 106 que contam e as três estornadas — 94, 95 e 96 —, que ficam e não contam. O razão não apaga linha (ADR-072 §3).

**A coluna "disparou?" tem quatro respostas.** *sim* — disparou e a dívida foi paga;
*não* — e a coluna seguinte diz o que se procurou; **disparou e não produziu nada** —
a condição chegou e ninguém decidiu o que ela mandava decidir; e *rearmado* —
disparou, foi examinado, e a dívida continua com outro gatilho. **A terceira é a que
este arquivo existe para achar.**

---

## Disparou e não produziu nada

| # | Onde | O gatilho, copiado | O que mostra que disparou | O que o fecha |
| --- | --- | --- | --- | --- |
| 1 | ADR-044:97 (e `gateway/config/JwtProperties.java`:12) | *"Fica registrado como duplicação conhecida, com gatilho: o terceiro módulo que precisar do mesmo decoder."* | o `catalog` montou o terceiro decoder com validador de `iss`/`aud` em 28/09 (`e1e78fa`); hoje são quatro — `gateway`, `catalog`, `identity`, `merchant`. Nenhuma ADR decidiu: a ADR-071 §4 e a emenda de 16/10/2026 à ADR-038 voltam ao assunto só para dizer que ele continua aberto. **Reverifica com:** `grep -rln "JwtIssuerValidator\|audience" backend/*/*/src/main --include=SecurityConfig.java` e `grep -ln "validador de .iss" docs/architecture/decisions/ADR-0[4-7][0-9]-*.md` — medido em 16/10/2026, `60eb66b`. | uma decisão: módulo comum de segurança (emenda à ADR-001) ou a duplicação assumida sem gatilho |
| 2 | `gateway/src/test/.../IdentityDeMentira.java`:36 | *"Fica registrado na ADR-044 como duplicação conhecida, com gatilho: o terceiro módulo que precisar do mesmo dublê."* | há três cópias: `gateway`, `merchant` e `catalog` têm `support/IdentityDeMentira.java`. A ADR-044 **não** registra esta duplicação — `grep -n "dublê\|IdentityDeMentira" docs/architecture/decisions/ADR-044-*.md` volta vazio. **Reverifica com:** `ls backend/*/*/src/test/java/com/deliveryplatform/*/support/IdentityDeMentira.java` e `grep -c "dublê\|IdentityDeMentira" docs/architecture/decisions/ADR-044-a-cadeia-de-filtros-do-gateway.md` — medido em 16/10/2026, `60eb66b`. | a mesma decisão da linha 1, para teste |
| 4 | `frontend/README.md`:32 | *"**Gatilho para trazê-lo de volta:** a primeira escrita que precise invalidar leitura de **outra** tela."* | **leitura, não medida:** na W-D, marcar uma opção na tela do produto muda o `vendavel` que a lista mostra; a lista é relida por remontagem ao voltar (`ListaDeProdutos.tsx`, `aoVoltar`). Se isso é "invalidar leitura de outra tela", disparou e foi resolvido sem o TanStack Query nem decisão escrita | decidir se a remontagem é a resposta, e escrevê-lo |
| 52 | este razão (W-E) | *"a primeira tela que mostre um desses três ao comerciante"* — o `TratadorDeErros` do `catalog` repassava `excecao.getMessage()` no `detail` em três tratadores: `ProdutoNaoEncontrado` (404), `LojaSemExpediente` (409), `RegraDoCatalogoViolada` (400), contra a regra do `CLAUDE.md` escrita na W-E | **nasceu disparado** (W-C) e **pago em um terço na E-3b**: o `RegraDoCatalogoViolada` passou a carregar motivo com dados, o tratador escreve a frase, e os pontos de integridade saem **500** (ADR-063). **Continuam** o `ProdutoNaoEncontrado` (404) e o `LojaSemExpediente` (409). O censo, em 11/10/2026: `grep -rn "getMessage()" backend/services/*/src/main/java/com/deliveryplatform/*/api/error/TratadorDeErros.java` devolve as linhas 91 e 101 do `catalog` fora de `log.warn` | a frase escrita por tratador nos dois que faltam |
| 97 | `mensagem-na-fila-morta.md`:124 (e ADR-026 §7) | *"**A métrica de profundidade ainda não existe.** … Requisito do marco 3, junto do primeiro consumidor"* | o primeiro consumidor com fila morta chegou em 10/10/2026, no marco 2: `ConfiguracaoDoConsumoDeExpediente` (`7b1c888`), *"a primeira fila durável e a primeira fila morta deste repositório"*. A métrica não veio, e não pode vir como a ADR-026 §7 a decidiu — profundidade exposta ao Prometheus, alerta em zero —, porque a ADR-041 tirou o Prometheus por decisão e aceitou *"o actuator fica sem métrica nenhuma"*, sem citar a ADR-026. Hoje a fila morta só se vê pela interface de management, à mão **Reverifica com:** `grep -rln -i "prometheus\|micrometer" backend --include=*.kts --include=*.yml --exclude-dir=build` — medido em 18/10/2026, `432c8e9`: vazio. | uma decisão: quem olha a fila morta enquanto a ADR-041 vale, e a ADR-026 §7 emendada com ela. **Não é a linha 43:** aquela espera o stack voltar; esta já disparou |
| 34 | ADR-059, emenda de 16/10/2026 (a condição antiga: ADR-059:138; as cópias do `como-subir-local.md`, :480 e :494, viraram ponteiro na R-6) | *"a rodada que der ao `merchant` as rotas de escrita troca esta fixture por uma sequência de chamadas HTTP, e **a semeadura por `ApplicationRunner` sai do repositório nessa rodada**."* | **até a R-5 esta coluna dizia *"o `merchant` não tem `@PostMapping`, `@PutMapping` nem `@PatchMapping`"*.** A condição disparou em 10/10/2026 com o `POST /api/v1/me/estabelecimentos` (`31a5958`), e o `merchant` ganhou mais dois em 15/10 — `POST .../team/convites` (`ef82bc9`) e `POST /api/v1/convites/{token}/aceite` (`5889a4c`). Os três semeadores continuam na árvore — `SemeaduraDoIdentity`, `SemeaduraDoMerchant`, `SemeaduraDoCatalogo` —, todos atrás de `DELIVERY_SEMEADURA`, falsa por padrão. **Medido na R-5:** 36 campos, 26 que a API escreve, 10 que não. *O corpo do commit `2b57cc9` traz três totais diferentes destes — 23, 14 e 9 —, escritos antes da contagem, e a mensagem de commit é o único texto que não se emenda. A tabela do relatório da R-5 é a certa, e é esta.* E a condição foi conferida no dia em que disparou: a ADR-060 (`398d828`, 10/10) diz *"o gatilho não está pago"*; o que ficou falso foi esta coluna, escrita às 09:22 do mesmo dia (`d7dbcc4`), quatro horas antes da primeira rota. **Reverifica com:** `grep -rln "@PostMapping" backend/services/merchant-service/src/main/java` e `git ls-files "*Semeadura*.java"` — medido em 16/10/2026, `60eb66b`. | **A lista, medida na R-5 e mantida aqui desde a E-4** (ADR-059, emenda de 17/10/2026) — o que a fixture grava e a API não escreve: no `merchant`, as **áreas de entrega**, o **desconto de retirada** e o **pedido mínimo por modalidade**; no `catalog`, nada. **Saíram na E-6** (ADR-077): o **grupo de opções**, as **opções** e, com elas, o **`ESGOTADO_HOJE` da opção** — a marcação da opção tem rota desde a G-C, e a opção criada por rota é marcada pela rota no `MontagemDeOpcoesIT`. **Saíram na E-4:** o **horário de funcionamento** (ADR-073) e, com ele, o **`ESGOTADO_HOJE` do produto** — a marcação já tem rota (`PUT .../produtos/{produtoId}/disponibilidade`) e recusava só porque a loja criada por rota não tinha horário; ficou alcançável, e ninguém a percorreu de ponta a ponta. Os ids fixos dos semeadores não entram: numa sequência de chamadas, cada uma usa o id que a anterior devolveu. **Reverifica com (parcial):** `grep -rn "@PostMapping\|@PutMapping" backend/services/merchant-service/src/main/java backend/services/catalog-service/src/main/java` — lista as rotas de escrita, e não diz que campo cada uma escreve; a lista se confere lendo o corpo de cada rota contra ela — medido em 17/10/2026, `23e99a4`. |

## Rearmado

| # | Onde | O gatilho, copiado | O que mostra que disparou | O que o fecha |
| --- | --- | --- | --- | --- |
| 5 | ADR-048:262 → ADR-057:101 | *"o primeiro consumidor que **escreva em banco** — hoje previsto para o `order`, no marco 3."* Rearmado como: *"**O primeiro consumidor cujo efeito não seja uma comparação** — um que acrescente, some, registre ou publique para fora."* | o `OuvinteDeExpedienteAlterado` (G-C3b, `7b1c888`) escreve em banco e foi coberto pela exceção (b) da invariante 7. `grep -rn "@RabbitListener" backend --include=*.java` devolve dois ouvintes, os dois nas exceções | o `processed_messages`, quando o primeiro consumidor que some ou registre chegar — previsto para o `order` |
| 6 | ADR-037:210 → ADR-044:97 | *"**Não se decide agora porque só existe um serviço validando de verdade.** Quando o segundo chegar, a pergunta chega junto, com evidência."* | o segundo chegou (o `merchant`, `a5994c0`, 15/09/2026) e a pergunta foi respondida com adiamento e um gatilho novo, o da linha 1 — que disparou sem resposta | a linha 1. A ADR-038:124 afirmava o mesmo — *"Ele ainda não existe."* — até a emenda de 16/10/2026 (R-3) |

## Sim — disparou, e a dívida foi paga

| # | Onde | O gatilho, copiado | O que mostra | Pago por |
| --- | --- | --- | --- | --- |
| 7 | ADR-026:226 | *"o primeiro consumidor que escreva em banco. É ele que precisa desta política de verdade"* | `ConfiguracaoDoConsumoDeExpediente` com a política 1/4/16 s (G-C3b, `7b1c888`) | emenda de 10/10 à ADR-026 |
| 8 | ADR-048:117 | *"o primeiro consumidor que escreva em banco — ele vai precisar da política da ADR-026 de verdade"* | a mesma; a ADR-048 registra *"Disparou em 10/10/2026 (G-C3b)"* | o mesmo; o bloco `retry` do YAML do `catalog` saiu na I-B |
| 9 | ADR-043:193 e :210 | *"o primeiro serviço que precisar autorizar uma requisição própria contra um vínculo do `merchant`"* | o `catalog` (G-B3, `e1e78fa`) | ADR-045 |
| 10 | ADR-053:123 (e `ContratoDeErrosIT`:73) | *"a primeira tela que mostre `detail` a alguém — a W-C."* | a W-C | ADR-055 |
| 11 | ADR-040:212 (e `Permissao.java`:9) | *"O gatilho para mudar é o segundo serviço que precise nomear uma permissão"* | o `catalog` passou a nomear `VER_PRODUTO` (G-B3) | respondido com "não" — `PermissaoDoCatalogo`, recorte próprio |
| 12 | `frontend/src/api/useRecurso.ts`:47 (o da W-A) | o gatilho da W-A para o TanStack Query, *"para esta rodada decidir"* | a W-B | decidido: saiu (`main.tsx`:9). O de volta é a linha 4 |
| 3 | `frontend/src/painel/MenuDoPainel.tsx:34` (hoje citado na linha 32 do mesmo arquivo) | *"**Gatilho escrito:** a segunda seção com tela. Aí a URL passa a precisar dizer onde a pessoa está — para recarregar, para voltar e para mandar link a alguém —, e isso é rota, não estado."* | venceu na **W-C**; a **W-D** o agravou pondo a terceira tela pelo mesmo precedente, sem o reler; a **R-1** o nomeou; pago na **W-E**: `App.tsx` tem `/painel/lojas/:estabelecimentoId/{produtos,disponibilidade}[/:produtoId]`, e o estado que escolhia a seção saiu do `PainelPage` | ADR-061 — três rodadas entre vencer e ser lido |
| 13 | ADR-044:157 | *"sai da lista de exposição antes de qualquer ambiente exposto, junto com a decisão do limite de taxa."* | **pago antes de disparar**: o `/actuator/gateway` saiu na G-B5; `include: health,info` no `application.yml` do gateway | emenda de 30/09 à ADR-044 |
| 56 | ADR-063, seção "Gatilho escrito" | *"**E a primeira rota de escrita do `catalog`** — a E-3c. Ela vai gerar corpos malformados de verdade, e aí se mede quais dos trinta pontos passaram a ser alcançáveis."* | a E-3c: `POST .../catalog/produtos` monta o `Produto` a partir de um corpo. Medido: `categoriaId`, `precoBase` e `modoDeControle` ausentes chegavam ao construtor como nulo (`INTEGRIDADE`, 500), e `ordem` ausente estourava no desempacotamento | os quatro `@NotNull` do `ProdutoRequest`, com o `CriacaoDeProdutoIT` afirmando 400 para cada campo ausente (E-3c) |
| 79 | ADR-024:140 | *"**Pendência nomeada, sem prazo** — vale quando houver comerciante real reclamando, não antes."* (o pedido mínimo) | **pago antes de disparar**: a ADR-028 fecha a consequência que a ADR-024 nomeou, e a `Operacao` do `merchant` tem `pedidoMinimoPorModalidade` | ADR-028; a ADR-024 ganhou emenda datada na R-3, porque o corpo continuava afirmando a pendência |
| 80 | ADR-063:29 e :30, citando o javadoc antigo da `RegraDoCatalogoViolada` | *"Uma exceção só para toda a rodada, de propósito: **nesta altura o catálogo não tem rota**, e quem traduz regra violada em código HTTP é a camada que ainda não existe."* | o tradutor existe desde a G-E; a frase saiu do javadoc na E-3b. É o gatilho em prosa que fez nascer a segunda passada | ADR-063 (E-3b, `9fbcee1`) |
| 81 | ADR-067:23 | *"**A seção, por enquanto, não significa nada para quem olha.**"* | a W-F deu tela à seção: `CardapioPorSecao` (`ec89666`) | ADR-067 (W-F) |
| 82 | `catalogo.md`:279 | *"**Quem o chama ainda não existe** — o consumidor do evento é a G-C3b; hoje o único chamador é o teste de integração."* | o `OuvinteDeExpedienteAlterado` chama `ReativarNoExpediente` desde a G-C3b (`7b1c888`). A frase de origem dizia o contrário até a R-3, que a trocou no lugar | a G-C3b; a frase, na R-3 |
| 87 | ADR-012, emenda de 16/10/2026 | *"**Gatilho escrito:** a próxima rota que o gateway ganhar. Quem a acrescenta acrescenta a linha aqui — ou tira a tabela, e deixa o `application.yml` ser a única lista."* | **pago antes de disparar**, no mesmo dia: a tabela saiu de normativa, e o `RoteamentoIT` compara os ids dos casos com as rotas que o gateway carregou (R-3, `0e545c0`) | ADR-071; ADR-012, emenda de 16/10 (2) |
| 88 | `estabelecimento.md`:441 | *"**A porta pela qual os outros serviços perguntam ainda não existe**, e é deliberado: nenhum dos seis tem código"* | `GET /internal/merchants/{estabelecimentoId}/me/contexto-de-acesso` (`100f71f`, 28/09/2026), consumida pelo `AutorizacaoComercialHttp` do `catalog` (`e1e78fa`, 28/09/2026). **Frase falsa, ramo 1** | `100f71f` e `e1e78fa`; a frase trocada no lugar na R-3 |
| 89 | `mensagem-na-fila-morta.md`:124 (a segunda frase do item) | *"o `prometheus.yml` só aponta para o `/actuator` dos serviços"* | o `prometheus.yml` não está na árvore: saiu com o stack de observabilidade (ADR-041). **Frase falsa, ramo 1.** A primeira frase do mesmo item — a métrica de profundidade — tem linha própria desde a R-4, a 97 | a ADR-041; a frase trocada no lugar na R-3 |
| 27 | ADR-052:101 | *"a segunda coleção com mais de um escritor concorrente."* | **pago antes de disparar.** A segunda coleção existe — o `CategoriaDocumento` (`cbd633c`, 11/10/2026) — e nasceu com `@Version`, pela ADR-064 §4: *"a rodada de reordenar vai precisar, e acrescentar depois é migração"*. Hoje só a rota de criar escreve nela. **Reverifica com:** `grep -rln "^@Document" backend/services/*/src/main/java` e `grep -n "@Version" backend/services/catalog-service/src/main/java/com/deliveryplatform/catalog/infrastructure/persistence/entity/CategoriaDocumento.java` — medido em 16/10/2026, `60eb66b`. | ADR-064 §4 (`cbd633c`) |
| 98 | ADR-073, seção "Gatilho escrito" | *"**A primeira tela que monte o horário da semana.** Aí a rota ganha chamador, e a coluna do marco 1 ganha a ponta que falta."* | a W-H: o `HorarioDaSemana` lê com `GET` e grava com `PUT .../settings/horario` (`d61668f`). **Reverifica com:** `grep -rn "settings/horario" frontend/src --include=*.ts --include=*.tsx --exclude-dir=generated` — medido em 18/10/2026. *A segunda metade da frase copiada dá a coluna do marco 1 a uma tela; o que a W-H fez foi tornar o caminho percorrível, não percorrê-lo (ADR-074 §5)* | ADR-074 (W-H) |
| 62 | ADR-065, seção "Gatilho escrito" (e ADR-067:183, o mesmo) | *"**O dia em que alguém pedir para despublicar pela tela.** O agregado tem o caminho; a rota não nasce sem chamador."* | **pago antes de disparar**: nenhuma tela pediu. A rota nasceu pelo beco da E-6 — no produto publicado, grupo novo não entra, e a saída do agregado, o `INATIVO`, não tinha rota: `DELETE .../produtos/{produtoId}/publicacao`, sob `DESATIVAR_PRODUTO`. **Reverifica com:** `grep -n "@DeleteMapping" backend/services/catalog-service/src/main/java/com/deliveryplatform/catalog/api/controller/ProdutoController.java` — medido em 08/11/2026, na E-7: uma linha, a da publicação. *Até a E-7 esta linha estava em "Não", com o comando que procurava a rota e voltava vazio.* | ADR-078 (E-7); a ADR-065 ganhou emenda datada, porque a seção afirmava que a rota não nasceria sem chamador |

## Não

| # | Onde | O gatilho, copiado | O que se procurou | O que o fecha |
| --- | --- | --- | --- | --- |
| 14 | ADR-001:227 | *"o dia em que houver uma segunda regra de build."* | `grep -rn "tasks.register" backend/build-logic` — uma só, `verificarDependenciaEntreModulos`. **Reverifica com:** `grep -rn "tasks.register" backend/build-logic/src` — medido em 16/10/2026, `60eb66b`. | suíte própria do `build-logic` |
| 15 | ADR-009:299 | *"quando alguém precisar ordenar o cardápio por preço no banco"* | nenhuma ordenação por `precoBase` no `catalog`. **Reverifica com:** `grep -rni "sort.*precoBase\|order.*precoBase\|direction.*precoBase\|precoBase.*sort\|precoBase.*order\|precoBase.*direction" backend/services/catalog-service/src/main/java` — medido em 16/10/2026, `60eb66b`. | `changeUnit` para `Decimal128` |
| 16 | ADR-012:95 | *"**Gatilho escrito, para os dois:** o primeiro ambiente exposto à internet."* (limite de taxa e `correlationId`) | nenhum workflow implanta: `grep -lni "deploy\|environment:" .github/workflows/*` vazio. **Reverifica com:** `grep -lni "deploy\|environment:" .github/workflows/*` — medido em 16/10/2026, `60eb66b`. | as duas peças, com ADR |
| 17 | ADR-044:139 | *"o primeiro ambiente exposto à internet."* (limite de taxa) | o mesmo da 16 | a mesma |
| 18 | ADR-047:123 | *"o primeiro ambiente alcançável de fora da máquina de desenvolvimento. Aí a CSP entra com ADR própria"* | o mesmo da 16 | CSP |
| 19 | ADR-044:226 | *"**O gatilho para reabrir:** no dia em que houver rota demais para conferir de cabeça."* (o `/actuator/gateway`) | é julgamento, não fato. A contagem existe: o `application.yml` do gateway tem 16 rotas, e o `RoteamentoIT` compara as rotas que o gateway carrega com os casos dele desde `0e545c0` — a conferência deixou de ser de cabeça, e o `/actuator/gateway` continua fora da exposição. **Reverifica com:** `grep -c "^            - id:" backend/infra/gateway/src/main/resources/application.yml` e `grep -n "void toda_rota_do_gateway" backend/infra/gateway/src/test/java/com/deliveryplatform/gateway/integration/RoteamentoIT.java` — medido em 16/10/2026, `60eb66b`. | — |
| 20 | ADR-054:48 (substitui o da ADR-045:123) | *"**a primeira chamada de serviço para serviço em que o serviço chamado precise saber quem chamou** para decidir o que responder."* | as duas chamadas que existem (`catalog` → `merchant`, `AutorizacaoComercialHttp` e `ExpedienteCorrenteHttp`) mandam o token do portador, e o `merchant` responde sobre ele | identidade de serviço |
| 21 | ADR-046:179 | *"o dia em que uma passada da varredura levar mais que o intervalo entre passadas."* | **não há medida** da duração de uma passada; o log só conta aberturas publicadas. Não disparou até onde se vê, e não se vê muito. **Reverifica com:** `grep -n "log\." backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/infrastructure/agenda/VarreduraDeAbertura.java` — medido em 16/10/2026, `60eb66b`. | uma consulta restrita, já desenhada |
| 22 | ADR-046:284 (e `pedido.md`:397) | *"**O gatilho, escrito:** quando o `order` ganhar código — marco 3 —"* (emitir fechamento, pausa e retomada) | `order-service` tem 1 arquivo Java, a `*Application`. **Reverifica com:** `find backend/services/order-service/src/main -name "*.java"` — medido em 16/10/2026, `60eb66b`. | decisão (a) ou (b) da ADR-046 |
| 23 | ADR-049:150 | *"se algum dia o horário deixar de ser semanal (feriado, exceção por data), esta busca muda junto."* | `Disponibilidade` continua `Map<DayOfWeek, List<Faixa>>` | a busca da próxima abertura |
| 24 | ADR-050:42 | *"quem abrir o `SecurityConfig` do `identity` para qualquer outra coisa acrescenta a propriedade na mesma passada."* | nenhum commit no `SecurityConfig` do `identity` desde a ADR-050 (30/09). **Reverifica com:** `git log --oneline --since=2026-09-30 -- backend/services/identity-service/src/main/java/com/deliveryplatform/identity/config/SecurityConfig.java` — medido em 16/10/2026, `60eb66b`. | a documentação aberta no `identity` |
| 25 | ADR-050:128 | *"o dia em que a cadeia de filtros virar módulo comum"* | não virou | — |
| 26 | ADR-051:128 | *"O dia em que as imagens tiverem de ser construídas num lugar que **não tem o build do Gradle disponível**"* | nenhum workflow constrói imagem. **Reverifica com:** `grep -lni "docker build\|docker/build-push\|bootBuildImage\|compose build" .github/workflows/*` — medido em 16/10/2026, `60eb66b`. | a imagem construtora |
| 28 | ADR-052:104 (e `ContratoDeErrosIT`:105, `ReativacaoNoExpedienteIT`:58) | *"o primeiro relato de 409 que ninguém consegue explicar."* | nada no repositório registra relato de uso | teste HTTP → 409 |
| 29 | ADR-053:151 | *"O primeiro serviço **sem** rota autenticada, ou com um esquema de erro diferente — o `conversation`"* | `conversation-service` tem 1 arquivo Java. **Reverifica com:** `find backend/services/conversation-service/src/main -name "*.java"` — medido em 16/10/2026, `60eb66b`. | o contrato de erro do webhook |
| 30 | ADR-055:56 | *"a primeira tela que precise de **outro** campo do `ProblemDetail` além do `detail`"* | nenhum `.type` lido em `frontend/src`. **Reverifica com:** `grep -rn "\.type\b" frontend/src --include=*.ts --include=*.tsx --exclude=*.test.ts --exclude=*.test.tsx` — medido em 16/10/2026, `60eb66b`. | o esquema de erro no contrato |
| 31 | ADR-055:72 | *"a primeira que precisar — e aí o servidor ganha um discriminador legível por máquina, que é o campo `type`"* | o mesmo da 30 | o `type` nos tratadores |
| 32 | ADR-056:119 | *"A primeira rota cujo identificador na URL **não** pertença a uma loja"* | todas as rotas com id estão sob `/merchants/{estabelecimentoId}` | a regra para recurso global |
| 33 | ADR-058:114 (e :91) | *"**A primeira rodada que precise de um dos cinco esqueletos de pé.**"* | as rodadas I-B a W-D subiram só o `marco2`. **Reverifica com:** `grep -n "PERFIL=" scripts/subir-local.sh` e `find backend/services/order-service/src/main backend/services/payment-service/src/main backend/services/delivery-service/src/main backend/services/settlement-service/src/main backend/services/conversation-service/src/main -name "*.java"` — medido em 16/10/2026, `60eb66b`. | `mem_limit` e `on-failure:3` nos cinco |
| 35 | `catalogo.md`:192 (e `MarcarDisponibilidadeService`:64) | *"o `conversation-service` ganhar código, que é quem o consome."* (o `DisponibilidadeAlteradaV1`) | 1 arquivo Java no `conversation`. **Reverifica com:** `find backend/services/conversation-service/src/main -name "*.java"` — medido em 16/10/2026, `60eb66b`. | outbox no `catalog` e o evento |
| 36 | `catalogo.md`:243 (e `ProdutoResponse`:30) | *"a primeira tela que mostre "a partir de"."* | nenhum "a partir de" em `frontend/src`. **Reverifica com:** `grep -rni "a partir de" frontend/src --include=*.ts --include=*.tsx` — medido em 16/10/2026, `60eb66b`. | `precoMinimoPossivel` na resposta |
| 37 | `catalogo.md`:44 (e `ModoDeControle`:32) | *"o valor nasce junto com a primeira baixa de estoque, no marco 10."* | o enum tem `SEM_CONTROLE` e `QUALITATIVO` | marco 10 |
| 38 | `ProdutoDocumento`:40 | *"quando alguém precisar consultar `marcadoEm` por intervalo"* | nenhuma consulta por `marcadoEm` no repositório Mongo. **Reverifica com:** `grep -rni "criteria.*marcadoEm\|query.*marcadoEm\|where.*marcadoEm\|between.*marcadoEm\|find.*marcadoEm\|marcadoEm.*between\|marcadoEm.*gte\|marcadoEm.*lte" backend/services/catalog-service/src/main/java/com/deliveryplatform/catalog/infrastructure/persistence` — medido em 16/10/2026, `60eb66b`. | `Date` com truncamento explícito |
| 39 | `ProdutoRepositorioMongo`:129 | *"a primeira loja com mais de mil produtos, ou a primeira varredura que apareça em log de consulta lenta"* | nenhuma loja com mais de mil produtos: no banco de desenvolvimento são duas lojas, com um produto cada — a da fixture e outra, de id sorteado, criada por rota. Nenhum log de consulta lenta medido. **Reverifica com:** `docker exec delivery-platform-mongodb-1 mongosh catalog_db --quiet --eval 'db.produtos.aggregate([{$group:{_id:"$estabelecimentoId",n:{$sum:1}}},{$sort:{n:-1}}]).toArray()'`, de dentro do WSL — medido em 16/10/2026, `60eb66b`. | o índice `002` |
| 40 | ADR-021:221 | *"**Gatilho:** o starter volta com o primeiro leitor do cardápio em cache, no marco 7"* | o build do `catalog` cita Redis só em comentário. **Reverifica com:** `grep -n -i "redis" backend/services/catalog-service/build.gradle.kts` — medido em 16/10/2026, `60eb66b`. | marco 7 |
| 41 | `DiaOperacional.java`:34 | *"O gatilho para a função mudar de lugar continua sendo o segundo serviço que precise **calcular**"* | `DiaOperacional` não aparece fora do `merchant` **Reverifica com:** `grep -rln "DiaOperacional" backend/services --include=*.java --exclude-dir=build` — medido em 18/10/2026, `432c8e9`: só arquivos do `merchant`. | o `settlement` ou o `order` |
| 42 | `CLAUDE.md`:413 | *"**Gatilho:** o primeiro `bootRun` de `catalog` ou `conversation` que alguém precise fazer"* | nada registra um; as rodadas I-B a W-D usaram contêiner | medir o `bootRun` sem `?replicaSet` |
| 43 | ADR-041:300 | *"Observabilidade volta quando houver **um comerciante real usando o sistema**"* | não há **Sem comando:** a condição é do mundo — um comerciante real usando o sistema —, e o repositório não tem onde a registrar; relido em 18/10/2026. | o stack de observabilidade |
| 44 | `usuario.md`:177 | *"**Decidir antes do marco 5**, quando houver dinheiro na conta."* (número de telefone reciclado) | é prazo, não condição; o marco 5 não começou | a política |
| 45 | ADR-057, seção "Gatilho escrito" | *"**E um segundo, menor:** a primeira mensagem cuja chave de domínio **não venha no corpo** e precise ser buscada."* | o `ExpedienteAlteradoV1` traz o `expedienteDeReferencia` no corpo; é o único consumido com a exceção (b) **Reverifica com:** `grep -rn "@RabbitListener" backend/services/*/src/main --include=*.java` — medido em 18/10/2026, `432c8e9`: dois ouvintes, o do `VinculoAlteradoV1` e o do `ExpedienteAlteradoV1`. | o `processed_messages` para esse consumidor |
| 46 | ADR-058, seção "Gatilho escrito" | *"**E um segundo:** o primeiro serviço cuja ocupação passar de 80% dos 512 MiB."* | `docker stats` em 10/10/2026, com a pilha de pé: o maior é o `merchant`, 302,2 MiB — 59% | reescolher o teto |
| 47 | ADR-059, seção "Gatilho escrito" | *"**E um segundo, que mede o andaime:** a primeira vez que alguém quiser semear algo que o agregado não deixa construir."* | os três semeadores constroem pelo agregado; o `identity` usa `reconstituir` para fixar o id, e o agregado aceita | descobrir por que o domínio recusa |
| 48 | ADR-054:51 | *"**a primeira rota `/internal/` que não receba o token de um portador.**"* | as duas rotas `/internal/` do `merchant` caem no `anyRequest().authenticated()` do `SecurityConfig` dele | identidade de serviço |
| 49 | ADR-060, seção "Gatilho escrito" | *"**A primeira rota que crie um recurso cujo dono ainda não exista** — hoje só esta."* | só o `POST /api/v1/me/estabelecimentos` cria sem confrontar vínculo **Reverifica com:** `grep -rn "@PostMapping" backend/services/*/src/main --include=*.java` — medido em 18/10/2026, `432c8e9`: oito; os do `catalog` e o do convite exigem vínculo, o aceite cria vínculo numa loja que já existe, e o `/signup` do `identity` é anterior à ADR-060 e cria o próprio titular. | a regra *"escrita sem vínculo só quando o vínculo é o produto"* vira linha do `CLAUDE.md` |
| 50 | ADR-060, seção "Gatilho escrito" | *"**O dia em que o `merchant` precisar de um GET de uma loja por id.**"* | não há `GET /api/v1/me/estabelecimentos/{id}` nem outro GET de loja por id; o predicado do gateway para a coleção é exato (G-B5) — e por isso a criação responde 201 **sem `Location`** **Reverifica com:** `grep -rn "estabelecimentos/{" backend/services/merchant-service/src/main --include=*.java` — medido em 18/10/2026, `432c8e9`: vazio. | o predicado prefixado e o `Location`, com ADR própria |
| 51 | ADR-060, seção "Gatilho escrito" | *"**O dia em que uma pessoa física precisar ser impedida de criar lojas sem limite.**"* | não há limite nem unicidade de documento, e ninguém pediu **Reverifica com:** `grep -rln -i "unique.*documento\|documento.*unique" backend/services/merchant-service/src/main/resources/db/migration` — medido em 18/10/2026, `432c8e9`: vazio. *"Ninguém pediu" não tem comando.* | decisão de produto |
| 53 | ADR-061, seção "Gatilho escrito" | *"**A primeira tela do painel que não pertença a uma loja** — relatório que cruze duas lojas, conta da pessoa, preferências."* | toda rota sob `/painel` em `App.tsx` é o seletor, o cadastro ou `lojas/:estabelecimentoId/…` | um nível acima de `/painel/lojas/:id` |
| 55 | ADR-063, seção "Gatilho escrito" | *"**O dia em que alguém for ler o `catalog` e se confundir com uma `RegraDoCatalogoViolada` que é 500.**"* — a classe virou duas espécies com um tipo só, separadas por motivo, e **o nome dela mente para metade** | é julgamento, não fato medível; nenhum relato **Sem comando:** a condição é do mundo — a confusão é de quem lê o código —, e o repositório não tem onde a registrar; relido em 18/10/2026. | a classe partida em duas, com nomes honestos |
| 57 | ADR-064, seção "Gatilho escrito" | *"**A primeira tela que renomeie, reordene, ative ou desative uma seção.** Aí as três sem evento viram rotas — e **`reordenar` arrasta o outbox do `catalog` junto**."* — o `catalog` **não tem outbox**, e o `CategoriasReordenadasV1` está declarado com o `conversation` como consumidor; enquanto não houver, **reordenar categoria não vira rota** (invariante 7) | a tela do cardápio (W-F) cria e lista seções, e não renomeia, reordena, ativa nem desativa nenhuma; na E-3b, nenhuma tela tocava em seção. Medido na E-3a, decidido na E-3b **Reverifica com:** `grep -n "metodo:" frontend/src/api/cardapio.ts` — medido em 18/10/2026, `432c8e9`: `POST`, `POST` e `PUT` — criar seção, criar produto, publicar. | o outbox do `catalog`, e a rota |
| 58 | ADR-064, seção "Gatilho escrito" (o mesmo gatilho, a outra metade) | `renomear`, `ativar` e `desativar` categoria existem no agregado e não têm rota. *"Entram com a tela, e o gatilho está escrito."* | o mesmo da 57 | as três rotas, sem evento |
| 59 | ADR-064, seção "Gatilho escrito" | *"**O dia em que alguém quiser separar quem cria produto de quem mexe nas seções.**"* | criar categoria exige `CRIAR_PRODUTO`; ninguém pediu a separação | `GERENCIAR_CARDAPIO`, com o pedido na mão |
| 60 | ADR-064, seção "Gatilho escrito" | *"**E o dia em que uma loja tiver mais seções do que cabem numa resposta.**"* | não há medida de quantas seções uma loja tem; a fixture tem uma **Sem comando:** a condição é do mundo — quantas seções uma loja tem é dado de produção —, e o repositório não tem onde a registrar; relido em 18/10/2026. | a listagem paginada |
| 61 | ADR-065, seção "Gatilho escrito" | *"**A primeira tela que monte um produto com grupos** — é a E-3d, e ela arrasta o método novo na raiz para acrescentar opção."* — até a E-6, `acrescentarGrupo` tinha método na raiz e não tinha rota, e acrescentar opção não tinha método; **desde a E-6 os dois têm método e rota** (`Produto.criarGrupo` e `Produto.criarOpcao`, ADR-077) | o corpo do `POST .../produtos` não carrega grupos (E-3c). Medido na E-3a, decidido na E-3c. As rotas nasceram na E-6; a tela que monta, não. **Reverifica com:** `grep -rn "/grupos" frontend/src --include=*.ts --include=*.tsx --exclude-dir=generated --exclude=disponibilidade.ts --exclude=*.test.tsx` — medido em 08/11/2026, na E-7: vazio. *Até a E-7 o comando tinha um pipe dentro da célula, que partia a tabela.* | a primeira tela que monte um produto com grupos |
| 63 | ADR-066, seção "Gatilho escrito" | *"**E o dia em que alguém apagar uma categoria que tem produtos.**"* — não há rota de apagar categoria, e quando houver, a regra da ADR-066 fica do lado errado do tempo: o produto passaria a apontar para coisa que existiu | não há rota nem tela que apague seção **Reverifica com:** `grep -rn "@DeleteMapping" backend/services/*/src/main --include=*.java` — medido em 08/11/2026, na E-7: uma linha, o `DELETE .../produtos/{produtoId}/publicacao` (ADR-078), que tira produto do ar e não apaga seção; vazio até a E-7. | a decisão do que acontece com os produtos da seção |
| 64 | ADR-066, seção "Gatilho escrito" | *"**A primeira outra referência de corpo que atravesse raízes** — o `entregadorId` de um pedido, o `estabelecimentoId` de uma conversa."* | a `categoriaId` do `ProdutoRequest` é a única (E-3c) **Reverifica com:** `grep -rn "UUID [a-zA-Z]*Id" backend/services/*/src/main/java --include=*Request.java` — medido em 18/10/2026, `432c8e9`: só o `categoriaId`. | a ADR-066 vira regra da casa, com outro nome |
| 65 | ADR-066, seção "Gatilho escrito" | *"**O dia em que o front precisar distinguir um campo inválido de outro na mesma resposta.**"* | o formulário do produto (W-F) mostra o `detail` inteiro num aviso, sem destacar campo — e ninguém pediu o destaque | um `campo` no corpo de erro, e a ADR-055 emendada |
| 66 | `PublicarProdutoService`:30 | *"**Gatilho escrito:** o `conversation` ganhar código, que é quem o consome."* — o `catalogo.md` §8 declara `ProdutoPublicadoV1` na transição `RASCUNHO → ATIVO`, e a rota de publicar (E-3c) não o emite: o `catalog` não tem outbox. A mesma decisão da linha 35, para o outro evento | `conversation-service` sem código de domínio | o outbox do `catalog` e o evento |
| 67 | ADR-068, seção "Gatilho escrito" (e §1) | *"**O primeiro chamador que precise de um subconjunto por estado.** Aí nasce o parâmetro, com ele."* — a listagem do painel devolve **todos os estados** e **não tem filtro** (ADR-068 §1): parâmetro sem chamador é superfície, e quem precisaria de *"só os publicados"* entra pela porta da ADR-018 | a listagem tem **três** chamadores no painel — o `ListaDeProdutos.tsx`, o `CardapioPorSecao.tsx` e o `NovoProduto.tsx` (`ec89666`, `86e3024`) —, e **nenhum pede subconjunto por estado**: os três leem todos, e a tela mostra o estado que veio. O `SemeaduraDoCatalogo` a usa só como teste de idempotência (E-3d). *Até 18/10/2026 esta coluna dizia "o único chamador da listagem é o `ListaDeProdutos.tsx`" — falsa, achada na terceira passada.* **Reverifica com:** `grep -rln "caminhoDosProdutos(" frontend/src/painel --include=*.tsx --exclude=*.test.tsx` — medido em 18/10/2026, `432c8e9`: os três. | o parâmetro, com o chamador |
| 68 | ADR-068, seção "Gatilho escrito" | *"**O dia em que a lista do painel não couber na tela.** A rota pagina desde a G-B3; a tela não"* | a tela mostra a primeira página, de 20, e avisa | a paginação da tela |
| 69 | ADR-068, seção "Gatilho escrito" | *"**E o dia em que o cardápio de quem compra existir.** Ele entra pela ADR-018, e se alguém o fizer passar por esta rota, esta ADR é a que está errada"* | `POST /internal/catalog/quote` não existe em código (E-3d) **Reverifica com:** `grep -rn "catalog/quote" backend --include=*.java` — medido em 18/10/2026, `432c8e9`: só um javadoc do `merchant` a cita. | a porta da ADR-018 |
| 70 | ADR-067, seção "Gatilho escrito" (e §4) | *"**O segundo cliente da rota de criar produto.** Aí o `ordem` derivado vira regra duplicada em dois lugares, e o conserto é no contrato."* — o `ProdutoRequest` exige `ordem` com `@NotNull`, e **nenhum comerciante tem opinião sobre "ordem 0"**. A tela calcula o maior da seção mais um e não mostra o campo; **a regra "o produto entra no fim da seção" é de domínio e está vivendo no cliente** | o único cliente é o `NovoProduto.tsx` do painel (W-F); a seção nova tem a mesma conta, no `CardapioPorSecao.tsx` **Reverifica com:** `grep -rln "criarProduto(" frontend/src --include=*.tsx --exclude=*.test.tsx` — medido em 18/10/2026, `432c8e9`: só o `NovoProduto.tsx`. | `ordem` opcional no contrato, e o fim da seção decidido no servidor |
| 71 | ADR-069 §2 (e `estabelecimento.md`:234) | *"**Gatilho escrito:** o dia em que a rota do usuário do token existir. Aí a página diz *'a Marli te convidou'*, e **só aí**."* — a página pública do convite **não diz quem convidou**: o `merchant` guarda o vínculo de quem convidou, o nome mora no `identity`, e a rota *"o usuário do token"* não existe | a rota não existe no `identity` (`grep @RequestMapping` no `identity-service`: `/api/v1/auth` e o JWKS); na tabela do `premissas-do-front.md` desde a G-B5, sem rodada **Reverifica com:** `grep -rn "@RequestMapping\|@GetMapping" backend/services/identity-service/src/main --include=*.java` — medido em 18/10/2026, `432c8e9`: `/api/v1/auth` e o JWKS. | a página diz quem convidou |
| 72 | este razão (E-2) | *"o primeiro administrador que peça para cancelar um convite"* — `Equipe.cancelarConvite` existe desde a B2, confere A1 e só cancela o `PENDENTE`, e **não tem rota**: criar outro convite não cancela o primeiro | nenhuma rota de cancelar no `merchant` (E-2) **Reverifica com:** `grep -rln -i "cancelar" backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/api` — medido em 18/10/2026, `432c8e9`: vazio. | a rota de cancelar convite |
| 73 | ADR-069, seção "Gatilho escrito" | *"**A primeira tela que peça para promover, remover, suspender ou sair.** Aí as cinco viram rotas, e o `GerenciarEquipeService` já as tem."* — medido na E-2, são **sete** as operações do `GerenciarEquipeService` sem borda: promover, rebaixar, suspender, reativar, remover, sair e alterar permissões | a única escrita da equipe com rota é convidar, e o aceite (E-2) **Reverifica com:** `grep -rln -i "promover\|rebaixar\|suspender\|reativar\|remover\|alterarPermiss" backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/api` — medido em 18/10/2026, `432c8e9`: vazio. | as rotas, com a tela que as peça |
| 74 | ADR-069, seção "Gatilho escrito" | *"**O dia em que alguém perder o link e reclamar.** Aí se decide entre revogar-e-recriar e mostrar o token na lista, **com o caso real na mão**"* | há tela de equipe desde a W-G, em `/painel/lojas/:id/equipe`, e ela mostra o link uma vez; **nada no repositório registra alguém que o perdeu**, e isso não tem comando. *Até 18/10/2026 esta coluna dizia "não há tela de equipe" — escrita na E-2, antes da W-G, e achada falsa na terceira passada.* **Reverifica com:** `grep -n 'path="equipe"' frontend/src/App.tsx` — medido em 18/10/2026, `432c8e9`: a rota da seção. | a decisão, com o caso |
| 75 | ADR-069, seção "Gatilho escrito" | *"**E o dia em que houver `CanalPort`.** O transporte deixa de ser humano, e esta ADR vira sobre o canal"* | `CanalPort` não existe em código **Reverifica com:** `grep -rn "interface CanalPort\|class CanalPort" backend --include=*.java` — medido em 18/10/2026, `432c8e9`: vazio — o nome só aparece em comentário. | o convite enviado pelo canal |
| 76 | ADR-070, seção "Gatilho escrito" | *"**O primeiro administrador que perguntar quem está pendente.** Aí nasce a listagem de convites — rota e tela."* — **não há lista de convites pendentes**: quem convida copia o link na hora, e depois não sabe quem está pendente nem reencontra o link (ADR-069 §1, ADR-070 §1). A tela diz isso a quem convida | não há rota de listar convites no `merchant` (E-2), nem tela (W-G) **Reverifica com:** `grep -n "@GetMapping\|@RequestMapping" backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/api/controller/ConviteController.java` — medido em 18/10/2026, `432c8e9`: um `GET` só, o da página pública do convite; nenhuma listagem. | a listagem de convites, rota e tela |
| 77 | ADR-070, seção "Gatilho escrito" | *"**O dia em que existir a rota do usuário do token.** Aí a lista de membros tem o que escrever"* — **não há lista de membros na tela de equipe**, porque não há nome a mostrar. A outra metade do mesmo gatilho é a linha 71, a página do convite que não diz quem convidou | o `GET .../team` existe desde a C-A e devolve `usuarioId`; a rota do nome não existe **Reverifica com:** `grep -n "lista de membros" frontend/src/painel/ConvidarParaEquipe.tsx` — medido em 18/10/2026, `432c8e9`: a tela escreve por que não lista. A metade do `identity` é o comando da linha 71. | a lista de membros, com nome |
| 78 | este razão (W-G; achado na E-2) | *"o primeiro log de acesso, no gateway ou no `merchant`"* — **o token do convite vai na URL** do `GET` público e do aceite, como a ADR-069 desenhou; hoje nenhum dos dois serviços grava log de acesso, e se um dia gravar, grava o token. No front ele fica também no `state.de` do roteador, na mesma entrada do histórico que já tem a URL | nenhuma configuração de log de acesso no `merchant` nem no `gateway` (E-2) **Reverifica com:** `grep -rn -i "accesslog\|access-log\|access_log" backend/services/*/src/main/resources backend/infra/gateway/src/main/resources docker-compose.yml` — medido em 18/10/2026, `432c8e9`: vazio. | o caminho mascarado no log, ou o token fora do caminho |
| 54 | ADR-061, seção "Gatilho escrito" | *"**O dia em que o seletor tiver mais de uma dúzia de lojas.**"* | não há medida de quantas lojas alguém tem; a fixture e a prova da W-E têm uma cada **Sem comando:** a condição é do mundo — quantas lojas uma pessoa tem é dado de produção —, e o repositório não tem onde a registrar; relido em 18/10/2026. | busca no servidor, e paginação no `GET /api/v1/me/estabelecimentos` |
| 99 | ADR-073, seção "Gatilho escrito" | *"**O primeiro comerciante que pedir para fechar a loja agora, sem mexer no horário.** Aí nasce a pausa manual, com a pergunta de quando ela expira."* | nada no repositório registra o pedido | a pausa manual, e a decisão de quando ela expira |
| 100 | ADR-073, seção "Gatilho escrito" | *"**E o primeiro pedido para delegar um campo de configuração sem delegar os outros.** Aí o `CONFIGURAR_LOJA` se parte."* | nada no repositório registra o pedido | a permissão partida, com emenda à ADR-011 |
| 101 | ADR-073, seção "Gatilho escrito" | *"**E a primeira tela de convite que ofereça `CONFIGURAR_LOJA`.** Hoje a rota a aceita e a tela não a mostra."* | o `PermissaoDoConvite` exclui a permissão por tipo. **Reverifica com:** `grep -n "PermissaoDoConvite =" frontend/src/api/convites.ts` — medido em 17/10/2026, `23e99a4` | o rótulo no `ConvidarParaEquipe.tsx`, e a exclusão sai do tipo |
| 102 | ADR-074, seção "Gatilho escrito" | *"**A primeira pessoa que pedir para repetir o horário de um dia nos outros.** Aí nasce a ajuda de copiar."* | nada no repositório registra o pedido; a tela edita as faixas dia por dia | a ajuda de copiar o horário de um dia para os outros |
| 103 | ADR-074, seção "Gatilho escrito" | *"**A segunda tela sob o `CONFIGURAR_LOJA`** — o desconto, o pedido mínimo ou as áreas. Aí o item do menu deixa de se chamar "Horário" e passa a nomear a permissão, com o horário dentro."* | a permissão tem uma seção só. **Reverifica com:** `grep -n "CONFIGURAR_LOJA" frontend/src/painel/secoes.ts` — medido em 18/10/2026, `d61668f` | o rótulo do item no `secoes.ts`, e o horário dentro da seção da configuração |
| 90 | `reconciliacao-de-pagamento.md`:161 | *"**Se a reconciliação vira automática** quando houver volume. Hoje é console e leitura"* | o `payment-service` tem 1 arquivo Java; não há pagamento a reconciliar **Reverifica com:** `find backend/services/payment-service/src/main -name "*.java"` — medido em 18/10/2026, `432c8e9`: um arquivo, a classe de aplicação. | a reconciliação automática |
| 91 | `recuperacao-de-acesso.md`:135 | *"Fluxo no painel, quando houver volume. Hoje é console e julgamento"* | nenhuma rota nem tela de recuperação de acesso no `merchant` ou em `frontend/src` **Reverifica com:** `grep -rln -i "recuperacao\|recuperação" backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/api frontend/src/paginas` — medido em 18/10/2026, `432c8e9`: vazio. | o fluxo no painel |
| 92 | `Equipe.java`:313 | *"O outro lugar que a quebrará é a automação da ADR-029, que cria ou promove membro sem passar por A1 — e que ainda não existe."* (a A3) | nenhum código de recuperação de estabelecimento no `merchant` **Reverifica com:** `grep -rln -i "recupera" backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/application` — medido em 18/10/2026, `432c8e9`: vazio. | a A3 verificada também na automação |
| 93 | ADR-071 §2 (e o javadoc do `RoteamentoIT`) | *"**um caso pode afirmar o `id` errado e passar.** Isso **é** linha de razão, com gatilho: *o dia em que o teste resolver o caminho pelo próprio roteador em vez de pelo `id` declarado.*"* | o `RoteamentoIT` liga cada caso à rota por um `id` escrito à mão (R-3) | o caso resolvido pelo roteador |
| 94 | ADR-071, seção "Gatilho escrito" | *"**A próxima regra que alguém quiser escrever num segundo lugar.** Ela não se escreve: escreve-se o ponteiro, ou o comando que a deriva."* | **não era dívida: é instrução de permanência.** Ela não tem um dia em que deixa de valer. Passou para o `README.md` das decisões. **Estornada pela emenda de 16/10/2026 (2) à ADR-071** (R-5) | — |
| 95 | ADR-071, seção "Gatilho escrito" | *"**A próxima lista que aparecer transcrita num teste.**"* | **não era dívida: é instrução de permanência.** Ela não tem um dia em que deixa de valer. Passou para o `README.md` das decisões. **Estornada pela emenda de 16/10/2026 (2) à ADR-071** (R-5) | — |
| 96 | ADR-071, seção "Gatilho escrito" | *"**E o dia em que existir comando que enumere regra duplicada.**"* | **não era gatilho.** A condição dependia de nós decidirmos escrever o comando, e a §4 da própria ADR-071 recusa esta linha. **Estornada pela emenda de 16/10/2026 à ADR-071** (R-4) | — |
| 83 | ADR-011:114 (e ADR-048:247, que o cita) | *"No MVP há uma instância por serviço e isso é invisível. Está escrito aqui porque é a primeira coisa a quebrar quando houver duas, e a falha é silenciosa."* | o compose sobe uma instância de cada serviço; a fila exclusiva por instância já existe no `catalog` (`AnonymousQueue`, `ConfiguracaoDaEscuta`), e nunca rodou com duas **Reverifica com:** `grep -n "replicas:\|scale:" docker-compose.yml` — medido em 18/10/2026, `432c8e9`: vazio. | a primeira execução com duas instâncias, medida |
| 84 | ADR-013:232 (e ADR-031:173, o evento da mesma saga) | *"é o desenho certo, e entra quando houver titular para exercer o direito."* (a saga de exclusão; o `TitularSolicitouExclusaoV1` é *"um nome já escolhido para um evento que ainda não existe, e quando existir passa por esta regra"*) | a exclusão é manual (`docs/operacao/exclusao-de-titular.md`); nada no repositório registra pedido de titular | a saga de exclusão, e o evento |
| 85 | ADR-021:76 (e `conversa.md`:291) | *"Volta a ser serviço quando houver aplicativo nativo e push, não antes."* (a notificação) | aplicativo nativo de consumidor está fora do escopo (`CLAUDE.md`); o painel avisa por consulta periódica | o serviço de notificação |
| 86 | ADR-045:142 | *"Continua sendo a resposta certa quando houver malha de serviço."* (mTLS) | nenhuma malha de serviço no compose nem em workflow **Reverifica com:** `grep -rn -i "istio\|linkerd\|envoy" docker-compose.yml .github` — medido em 18/10/2026, `432c8e9`: vazio. | mTLS entre serviços |
| 104 | ADR-076, seção "Gatilho escrito" | *"**A primeira pessoa que pedir o reenvio de um convite.** Aí nascem a lista de pendentes e o link que não morre no primeiro uso, no `merchant`."* | nada no repositório registra o pedido; o item 5 da passagem de 19/10/2026 (`passagem-humana.md`) registra o convite perdido e a falta da lista, e não pede reenvio. **Sem comando:** a condição é do mundo — uma pessoa pedindo —, e o repositório não tem onde a registrar; relido em 19/10/2026. A listagem de convites pendentes também fecha a linha 76 | o reenvio, com a lista de pendentes e o link que vale mais de uma vez |
| 105 | ADR-076, seção "Gatilho escrito" | *"**A primeira pessoa que quiser recusar um convite**, em vez de só não aceitá-lo. Aí a tela deixa de ter uma ação única e a recusa precisa de rota."* | o convite tem duas rotas, a leitura pública e o aceite. **Reverifica com:** `grep -n "@GetMapping\|@PostMapping\|@DeleteMapping" backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/api/controller/ConviteController.java` — medido em 19/10/2026, `ebc0793`: um `GET` e o `POST .../aceite` | a recusa do convite, rota e tela |
| 106 | ADR-077, seção "Gatilho escrito" | *"**A primeira pessoa que pedir para corrigir um grupo ou uma opção já criados** — o nome, os limites, o acréscimo. Aí nascem as rotas de alterar, e a regra do que uma correção pode mudar num produto publicado."* | nada no repositório registra o pedido; as rotas do grupo e da opção acrescentam e não alteram. **Sem comando:** a condição é do mundo — uma pessoa pedindo —, e o repositório não tem onde a registrar; relido em 08/11/2026. | as rotas de alterar grupo e opção |
| 107 | ADR-077, seção "Gatilho escrito" | *"**A primeira pessoa que pedir para tirar uma opção do cardápio para sempre**, em vez de marcá-la esgotada. Aí nasce a rota de remover, e a recobrança decide quando a remoção é recusada."* | nada no repositório registra o pedido; o `catalog` não tem rota de apagar. **Reverifica com:** `grep -rn "@DeleteMapping" backend/services/catalog-service/src/main --include=*.java` — medido em 08/11/2026, na E-7: uma linha, o `DELETE .../produtos/{produtoId}/publicacao` (ADR-078), que tira produto do ar e não remove opção; vazio na E-6. | a rota de remover opção |
| 108 | ADR-078, seção "Gatilho escrito" | *"**A primeira pessoa que reclamar de o produto sumir do cardápio enquanto ganha um grupo.** Aí a montagem fora do ar deixa de bastar, e nasce a alteração que se monta à parte e substitui a publicada de uma vez."* | nada no repositório registra a reclamação; montar um grupo num produto publicado é tirar do ar, montar e publicar. **Sem comando:** a condição é do mundo — uma pessoa reclamando —, e o repositório não tem onde a registrar; relido em 08/11/2026. | a alteração montada à parte |
| 109 | ADR-078, seção "Gatilho escrito" | *"**A primeira pergunta de por que um produto tirado do ar voltou com uma marcação que ninguém lembra.** Aí o `catalogo.md` escreve o que tirar do ar faz com a disponibilidade, e esta ADR ganha emenda."* | o `catalogo.md` não escreve o que tirar do ar faz com a disponibilidade; o `inativar()` não a toca. **Sem comando:** a condição é do mundo — uma pessoa perguntando —, e o repositório não tem onde a registrar; relido em 08/11/2026. | a regra no `catalogo.md`, e a emenda à ADR-078 |

**Fora da tabela, de propósito:** a ADR-037:203 adia o refresh token *"para ADR
própria"* e não escreve condição — o motivo dado, *"um marco que ainda não tem
painel para exercitar sessão longa"*, deixou de valer com a W-A, mas sem condição
escrita não há o que disparar. **É uma dívida sem gatilho**, e é a seção seguinte.

---

## Dívidas sem gatilho escrito

Achadas nas rodadas, sem condição que as reabra. **Sem gatilho, ninguém olha.**

| Dívida | De onde |
| --- | --- |
| O refresh token, com a baixa do TTL de 30 min | ADR-037:203 |
| O login com hash sem prefixo de cifrador responde **500**, não 401 (`IllegalArgumentException` do `DelegatingPasswordEncoder`) | medido na W-D |
| A rota que marca a opção devolve o resumo, sem os grupos | W-D; `CLAUDE.md`, armadilha "Vai mexer numa opção" |
| A falha de busca do JWKS responde 500 com o corpo do `/error`, e não `ProblemDetail` | I-B; ADR-055, emenda de 10/10 |
| Os seis blocos `listener.simple.retry` com `max-attempts`, que o Boot 4.1.1 não lê | ADR-048, nota de 10/10 |
| O par de chaves na raiz do repositório, que não é o de `secrets/` | I-B |
| `JWT_PUBLIC_KEY_PATH` no `.env.example`, que nada lê | I-B |
| A ADR-041 atribui à ADR-037 uma frase entre aspas que ela não tem — *"decidir quando o segundo consumidor existir"*; a ADR-037:210 diz *"Quando o segundo chegar, a pergunta chega junto, com evidência"* | medido na R-1 |
| As modalidades não estão no contrato: o mapa `metodosPorModalidade` é declarado com chave `string`, e o formulário da W-E nomeia `ENTREGA` e `RETIRADA` numa constante — a única lista dele sem o contrato atrás. Uma divergência com o domínio vira 400 sem `detail` | medido na W-E |
| A correção do Mongo no `conversation`, que nenhum teste exerce | `CLAUDE.md`, armadilha do MongoDB no Boot 4 |

---

## A pergunta de produto que precede o marco 3

**De onde nasce o pedido nos marcos 3 a 6?**

O PRD tem 29 histórias, e a única que cria pedido é a **H4.2** — *"Como cliente, quero
montar meu pedido e saber o total antes de confirmar"* —, que nasce na conversa. O canal
é o **marco 7**. O painel de pedidos é o **marco 3**, quatro marcos antes.

O perfil do estabelecimento (PRD §3.1) descreve a origem como *"WhatsApp
majoritariamente; telefone e balcão em menor volume"*, e **nenhuma história trata do
atendente registrando o pedido que chegou por telefone ou pelo balcão**.

Os caminhos, e nenhum é óbvio: uma história nova de lançamento manual; adiantar o mínimo
do canal para antes do marco 3; ou reordenar os marcos. **É decisão de produto, e ela
precede o código do marco 3.**

**Gatilho escrito:** o primeiro pacote do marco 3. Medido na R-1. *(Fora da tabela de
cima porque não é dívida de código — e fora da conta do cabeçalho, porque este arquivo
se exclui dos próprios comandos.)*

**Respondida em 18/10/2026, na P-1 (ADR-075):** o 7a do marco 7 vem antes do marco 3, e o pedido
que chega por telefone ou no balcão é a **H4.5**, que entra no marco 6. *E o número desta seção
estava errado desde a R-1: o PRD tinha 28 histórias, não 29 — `grep -c "^\*\*H[0-9]*\.[0-9]*\*\*" docs/PRD.md`
em `09e08ae` imprime 28. Com a H4.5, são 29.*

---

## Da segunda passada, e deliberadamente fora do razão

**Isto não é dívida: é o resto da varredura, nomeado para não ser triado outra vez.** Sem esta
lista a varredura seguinte reencontra cada uma destas linhas e refaz o trabalho — foi o que
custou a R-3, e o que as quatro da R-2 custaram, que só existiam no relatório e na mensagem do
commit.

| arquivo:linha | ramo | por quê |
|---|---|---|
| ADR-027:142 | 5 | *"A verificação de build ainda não existe."* Registra uma ausência sem condição que a dispare (R-2) |
| ADR-038:133 | 4 | a emenda de 16/10 cita a frase do corpo para dizer que ela envelheceu; a condição é a das linhas 1 e 6 |
| ADR-047:78 | 4 | *"o que vai importar quando houver troca de loja"* — descreve um benefício da decisão, que vale desde o seletor da W-E (R-2) |
| ADR-056:91 | 5 | *"uma regra para as rotas que ainda não existem"* — é regra, não condição (R-2) |
| `estabelecimento.md`:292 | 4 | por que a disponibilidade não responde se a loja está aberta. A R-2 a pôs no ramo 5, que era o que havia; com o ramo 4 escrito, é ele, como a `ExpedienteCorrenteResponse.java`:15 |
| `estabelecimento.md`:471 | **3** | *"as M9 e M10 passam a valer quando houver duas"* — duas áreas de entrega de uma loja é estado de dado. **A primeira vez que o ramo 3 separou alguma coisa**, na vigésima nona linha triada |
| `ConfiguracaoDaEscuta.java`:23 | 4 | explica por que o consumidor declara a exchange de novo: ligar fila a uma exchange inexistente falha |
| `ExpedienteCorrenteResponse.java`:15 | 4 | explica por que a resposta não tem `aberto` |

**O marco da varredura.** As ocorrências da segunda passada no commit que acrescentou esta
seção (R-3) foram **todas** triadas: **31** no total — 23 com a condição no razão, 8 nesta
tabela. O commit sai de `git log --format=%h -S"O marco da varredura" -- docs/dividas.md | tail -1`,
e não está escrito aqui porque o arquivo não sabe o hash do commit que o grava. A varredura
seguinte **compara o número com este e olha só a diferença**.

**A comparação é pela condição, não pelo endereço.** O razão cita a frase, e a mesma condição
aparece em `arquivo:linha` diferentes conforme o arquivo cresce — comparar por endereço deu
**17** falsos "sem linha" na R-3, contra **9** reais. *E se o número tiver caído sem rodada que
o explique, alguém apagou uma frase sem triá-la: isso é para se dizer, não para se somar.*

---

## A terceira passada — as colunas que afirmam uma ausência

**As duas primeiras passadas olham para fora: os arquivos do projeto.** Esta olha **para
dentro**, para as colunas deste arquivo — e existe porque a **linha 34** passou seis dias
afirmando que o `merchant` não tinha `@PostMapping`, quando ele tinha desde 13:24 do dia em que
a linha foi escrita.

**O cabeçalho deste arquivo é derivado; as colunas das linhas não são derivadas de nada.** A
coluna da evidência é um comando congelado em prosa, com a data de quem o rodou, e **nada o
re-roda.**

O comando, em Node — da raiz do repositório, `node terceira.js ausencias`. A tabela tem cinco
células por linha, a evidência é a quarta, e 57 linhas têm `|` dentro de código, por isso a
divisão respeita as crases; e o `\b` do JavaScript não reconhece letra acentuada, por isso a
fronteira das palavras é escrita por Unicode:

```js
// A terceira passada: as colunas da evidência do razão que afirmam uma ausência no presente.
// Uso, da raiz do repositório: node terceira.js [forma|ausencias]
const fs=require('fs');
const s=fs.readFileSync('docs/dividas.md','utf8').split('\n');
function celulas(l){ // parte em "|" fora de `código`
  const out=[];let cur='',cod=false;
  for(const ch of l){ if(ch==='`')cod=!cod; if(ch==='|'&&!cod){out.push(cur.trim());cur='';} else cur+=ch; }
  out.push(cur.trim()); return out.slice(1,-1);
}
let secao='',cab=null;const linhas=[];
s.forEach((l,i)=>{
  if(/^## /.test(l)){secao=l.slice(3);cab=null;}
  else if(/^\| # \|/.test(l)) cab=celulas(l);
  else if(/^\| \d+ \|/.test(l)) linhas.push({n:+celulas(l)[0],arq:i+1,secao,cab,c:celulas(l)});
});
const modo=process.argv[2]||'forma';
if(modo==='forma'){
  const formas={};for(const x of linhas){const k=x.secao+' :: '+x.c.length+' células :: '+(x.cab||[]).join(' | ');formas[k]=(formas[k]||0)+1;}
  console.log(formas);
  console.log('linhas com | dentro de código:',s.filter(l=>/^\| \d+ \|/.test(l)&&/`[^`]*\|[^`]*`/.test(l)).length);
} else {
  // "\b" do JavaScript não vê letra acentuada: a fronteira é escrita por Unicode.
  const B='(?<![\\p{L}\\p{N}_])', E='(?![\\p{L}\\p{N}_])';
  const formas=['não tem','não existe','não há','nenhum','nenhuma','sem rota','ainda não','nunca',
    'não é lido','não lê','não declara','não constrói','não implanta','continua sem','não aparece',
    'não registra','vazio','não emite','não foi medid\\p{L}*','só','1 arquivo','únic[ao]'];
  const re=new RegExp(formas.map(x=>B+x+E).join('|'),'iu');
  const alvo=linhas.filter(x=>x.c.length===5&&!/[Ee]stornada/.test(x.c[3])&&re.test(x.c[3]));
  alvo.sort((a,b)=>a.n-b.n);
  console.log('linhas de 5 células:',linhas.filter(x=>x.c.length===5).length,'| estornadas:',linhas.filter(x=>/[Ee]stornada/.test(x.c[3])).length,'| afirmam ausência:',alvo.length);
  for(const x of alvo)console.log(`\n#${x.n} [${x.secao}] (arquivo:${x.arq})\n  ${x.c[3]}`);
}
```

**Medido em `60eb66b`:** **50** das 94 linhas que contam afirmam uma ausência no presente. **20**
foram verificadas nesta rodada, as mais antigas primeiro; **4** estavam **falsas** e foram
consertadas — a 1, a 19, a 27 e a 39 —, e uma delas, a 27, mudou de seção: a condição inteira
estava paga. **Faltam 30.**

**E a correção não é só a frase.** Cada linha que esta passada toca passa a **carregar o comando
que a reverifica**, do lado da evidência. **20 das 50 linhas já o carregam** — e esse número é
para crescer até a terceira passada deixar de precisar de olhos.

**As trinta, em 18/10/2026, sobre `432c8e9`.** **26** ganharam o comando que as reverifica, e
**24** delas se confirmaram. **4** não têm comando possível — a 43, a 54, a 55 e a 60, cuja
condição é do mundo —, e a célula diz isso. **2** estavam **falsas** e foram consertadas: a
**67**, que contava um chamador onde há três, e a **74**, que dizia não haver tela de equipe
quando a W-G já a tinha dado. Nenhuma mudou de seção: nas duas, a evidência estava errada e o
gatilho continua sem disparar. **46 das 50 carregam o comando, e as 4 restantes dizem por que
não. A terceira passada está completa** — e envelhece como as outras duas: cada linha nova que
afirmar uma ausência nasce com o comando dela.

⚠ **Os comandos destas células escapam o `|` como `\|`**, porque `|` cru quebra a célula. **Isso
é escape de GFM:** a página renderizada mostra `|`, e **quem copiar de lá leva um comando que
procura o caractere literal.** Copie do arquivo cru. *Medido na R-7, e vale para todas as células
que carregam comando.*

**Isto não é o instrumento se medindo a si mesmo.** Nas duas primeiras passadas o
`--exclude=dividas.md` existe porque o arquivo era contado como **fonte** de dívida, e aí ele
inflava o próprio número. Aqui o arquivo é o **objeto auditado**, e o número que sai não entra na
contagem de linhas. *São coisas diferentes, e a confusão entre as duas é fácil.*

---

## O censo das frases que prometem coluna

**A regra é do `CLAUDE.md`**, na armadilha *"Vai escrever 'o marco N ganha X'"* (R-8): nenhum
texto promete coluna de marco a código, e a coluna só se afirma depois de uma pessoa diante da
tela. **Isto é o censo que a aplica ao que já estava escrito**, feito em 18/10/2026 sobre
`067155e`, e não é dívida: nenhuma linha da tabela nasce dele.

A população, em dois comandos:

```bash
grep -rn -i "coluna" docs CLAUDE.md README.md frontend/src backend contracts --include=*.md --include=*.java --include=*.ts --include=*.tsx --exclude-dir=node_modules --exclude-dir=build --exclude=dividas.md
grep -rn -i "marco [0-9]\+[^0-9].\{0,40\}\(cumprid\|conclu\|fechad\|✅\|pronto\)" docs CLAUDE.md README.md frontend/src frontend/README.md backend contracts --include=*.md --include=*.java --include=*.ts --include=*.tsx --exclude-dir=node_modules --exclude-dir=build --exclude=dividas.md
```

**89** linhas no primeiro e **11** no segundo. Lidas uma a uma, quase todas **negam** a promessa
(*"não diz que o marco 2 está cumprido"*), **citam** a coluna, ou **registram uma passagem feita
por gente** — o `README.md` e o `passagem-humana.md` sobre 15/10. **Duas prometem coluna a
código, e as duas estão em corpo de ADR:**

| arquivo:linha | a frase | o que se faz |
| --- | --- | --- |
| ADR-070:152 | *"Recusada pelo corte: atravessa duas camadas, e esta rodada é a que fecha a coluna."* | **nada.** A frase é incidental: a recusa se sustenta pelo corte, e o corte continua valendo. Corpo de ADR é datado, e só se emenda quando o que a frase sustenta deixa de valer |
| ADR-073:213 | o gatilho da tela do horário, que termina em *"e a coluna do marco 1 ganha a ponta que falta"* | **nada além do que já foi feito:** a linha 98 deste razão o copia com a ressalva de que o caminho ficou percorrível, não percorrido (W-H) |

**Fora de código, nenhuma.** Se o número de qualquer dos dois comandos mudar, só a diferença
precisa ser lida.
