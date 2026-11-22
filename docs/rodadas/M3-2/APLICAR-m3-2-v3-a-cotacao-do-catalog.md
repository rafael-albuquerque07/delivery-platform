# M3-2 v3 — a fatia 2: a cotação do `catalog`

**A v3 é a v2 com o que o revisor achou na segunda passada, antes do primeiro comando:**

- **o `README.md` das decisões** diz *"Oitenta e quatro escritas"*: passa a oitenta e cinco;
- **o cabeçalho do razão**, linhas 3 e 65: a v2 que você recebeu por último já os trocava; *a que o
  agente revisou era a anterior* — confira que `m32/` tem **este** arquivo, `APLICAR-m3-2-v3-…`;
- **"o cardápio de quem compra entra pela porta da ADR-018"**, em cinco lugares: a v2 escrevia que
  essa porta *"cota, não lista"*, e isso deixaria os cinco falsos. **O erro era meu: eu afirmei o que
  nenhum documento decidiu.** A v3 diz só o que a rodada mede — a cotação dessa porta ganhou código,
  e a leitura do cardápio para o canal segue sem rota —, e os cinco lugares **ficam como estão**;
- **a pipeline `frontend` está vermelha há sete dias, e não pelo motivo que eu previ.** O `gh` mostrou:
  ela falha no passo *"Conferir formatação"* — o Prettier acusa `src/api/configuracao.ts`,
  `src/painel/ConfiguracaoDaLoja.tsx` e `src/painel/ConfiguracaoDaLoja.test.tsx`, da rodada da
  ADR-082 —, **antes** de chegar ao passo dos tipos, que é o defeito da M3-1. São dois defeitos
  empilhados, e a v3 conserta os dois, e roda no seu computador os seis passos do `frontend.yml`;
- **a fronteira do 404**: a skill `novo-caso-de-uso` dizia, sem ressalva, *"recurso de outra loja é
  404"*. **O Rafael decidiu escrever a fronteira na skill e na ADR-088:** id na URL é 404 (ADR-056,
  linhas 91 e 92); id no corpo segue o documento do agregado.


**A v1 parou antes do primeiro comando**, com quatro grupos de achados do revisor e do agente. Os
achados eram meus:

| O achado | Onde a v1 errou | O que a v2 faz |
|---|---|---|
| **A ADR-024** pede para decidir *"antes de escrever o `cotar`"* se o preço varia por modalidade | a v1 não citava a ADR-024 | a decisão do Rafael — **o preço não varia** — entra por emenda na ADR-024 e como Decisão 3 da ADR-088 |
| **A opção repetida** | a v1 decidiu 400 sozinha, como "novo" | a decisão do Rafael — **400** —, com a razão escrita na ADR-088 |
| **A disponibilidade das opções** | a §3.1 dizia "cada opção", e a §3.2 lançava uma exceção com **uma** opção | o `cotar` devolve **todas** as opções indisponíveis do item, e um caso de teste e uma mutação provam |
| **Frases que a rodada deixava falsas** | a minha busca da regra 15 só procurou duas expressões | a busca foi feita num clone, e **todas as frases** estão nos dois scripts, testados: seis emendas de ADR, o `catalogo.md`, o `premissas-do-front.md`, três javadocs, as linhas 48 e 69 do razão |
| **A célula 48 do razão** | trocar o trecho duplicava o fim da frase | a célula **inteira**, no script |
| **As alternativas da ADR-088** | o pacote não dizia quais eram | **a ADR-088 vem escrita**, com as cinco alternativas recusadas |
| **A 4.1d na tabela do marco 3** | a ADR-086 põe a exibição para a produção na **fatia 5** | a 4.1d sai; a fatia 2 é peça em **dois** critérios |
| **A previsão do razão** | dizia segunda passada 29 | **28**: a frase do `premissas-do-front.md`:140 sai |

**O Rafael decidiu, em 18/11, 20/11 e 21/11:**

1. **A autorização é só o vínculo ativo**, sem permissão.
2. **A loja vai na URL**: `POST /internal/merchants/{estabelecimentoId}/catalog/quote`.
3. **O preço não varia por modalidade**, e o `cotar` não recebe modalidade — a ADR-024 confirmada.
4. **A mesma opção duas vezes no mesmo item é 400.**
5. **A fronteira do 404 entra na skill `novo-caso-de-uso` e na ADR-088:** id na URL é 404; id no
   corpo segue o documento do agregado.

⚠ **Se a resposta dada ao agente na v1 for diferente de alguma destas, pare** — o pacote foi
escrito com estas.

⚠ **Rodada de código.** ⚠ **A `metodo-delivery-platform` e a `novo-caso-de-uso` valem juntas**, e
o passo 3, pelo revisor, roda antes do primeiro comando.

**O que esta rodada não faz:** não chama a cotação de ninguém (o `order` é a fatia 4), não reserva
nada, não guarda cache da resposta, e não toca o canal sem portador.

---

## §0 · Antes de tudo

```bash
git log --oneline -1; git status --short
date +%d/%m/%Y
ls -A m32/ m32/adr/
echo "== controle (tem de achar, é da S-5):"
grep -c "^16\. " .claude/skills/metodo-delivery-platform/escrever-rodada.md
echo "== a última execução de cada workflow no main:"
gh run list --repo rafael-albuquerque07/delivery-platform --branch main --limit 15
echo "== os tipos do front contra os contratos, ANTES:"
(cd frontend && npm run tipos:conferir; echo "conferir=$?")
git checkout -- frontend/src/api/generated
git status --short
```

- O `main` tem de estar em **`b275220`**. O controle tem de dar **`1`**.
- `m32/` tem de ter **só este** `APLICAR-m3-2-v3-…md`, os dois scripts e `adr/`. *Se a v1 ou a v2
  ainda estiverem na pasta, mova a pasta inteira para `_to_delete/m32-v2` e extraia a v3 no lugar.*
- ⚠ **O `tipos:conferir` tem de FALHAR**, com diferença no `merchant.ts`: é o defeito da M3-1, que
  esta rodada conserta.
- No `gh run list`, o `frontend` tem de estar **vermelho** e os outros workflows, verdes. **Outro
  workflow vermelho: pare e diga qual.**
- O último `git status --short` só mostra pastas de pacote.

---

## §1 · As premissas

**Medidas num clone de `b275220`.** O script de documentos foi rodado nesse clone: 13 arquivos, 35
trocas, 6 emendas e a ADR-088; o recorte subiu **1** e a segunda passada caiu **1**, de 29 para 28. O
script dos javadocs: 3 arquivos, 4 trocas.

| # | O que este pacote supõe | Situação |
|---|---|---|
| P1 | A ADR-018 e o `catalogo.md` §5 fixam o comportamento: 400 para o erro de quem chama, 409 para o mundo que mudou, e o 409 *"com a lista do que caiu"* | lidos no clone |
| P2 | O `catalogo.md` §5 não diz nada do produto inexistente, e nenhum documento trata a opção repetida | lido no clone |
| P3 | O `Produto` tem `vendavel()` (C6), e `alterarGrupos` recobra as invariantes da publicação no produto `ATIVO` — `precoUnitario > 0` vale por construção | `Produto.java`:347 e :550 |
| P4 | O `TratadorDeErros` mapeia `RegraDoCatalogoViolada` em 400 por `Motivo`, e o 403 tem corpo único | lido no clone |
| P5 | `AutorizacaoComercialPort.contexto(estabelecimentoId)` é a porta de vínculo, e o `ConsultarProdutoService` é o modelo de *"a loja da URL, e só então o produto"* | lido no clone |
| P6 | O `ProdutoRepositorio` tem quatro métodos, nenhum por vários ids | lido no clone |
| P7 | O `ContratoDeErrosIT` exige provocação para todo código de erro declarado, e erro sem corpo declarado | lido no clone |
| P8 | As frases que a cotação deixa falsas são as treze do script, e o javadoc do `ContextoDeAcessoController`:33 **fica**: ele conta a história da rodada dele, no passado (*"já fixava"*) | `grep -rn "catalog/quote"` em `docs`, `contracts`, `CLAUDE.md`, `.claude` e `backend`, no clone |
| P9 | A cotação com o token do atendente não dispara as linhas 20 e 48 do razão | ADR-054:48 e :51 |

---

## §2 · As medições

```bash
c=backend/services/catalog-service/src/main/java/com/deliveryplatform/catalog
grep -n "public boolean vendavel\|private void alterarGrupos" $c/domain/model/Produto.java
grep -n "^    [A-Za-z<].*(" $c/application/port/out/ProdutoRepositorio.java
grep -n "static HttpStatus statusDe" -A4 $c/api/error/TratadorDeErros.java
grep -rn 'Mapping("/internal' backend/services/*/src/main | cut -c1-140
echo "== controle da forma (tem de achar, no merchant):"; grep -rln 'Tag(name = "interno"' backend/services/*/src/main
sed -n '/^## Emenda de 07\/10\/2026/,/^## Emenda de 16/p' docs/architecture/decisions/ADR-024-*.md
```

- As rotas `/internal/`: **quatro, todas no `merchant`**. O `Tag(name = "interno"` em arquivos do
  `merchant` e em nenhum do `catalog`.

---

## §3 · O código

### §3.1 · O comportamento

**É a tabela da ADR-088, em `m32/adr/`.** Leia a Decisão inteira antes de escrever código. Em
resumo, a ordem:

1. Autoriza pela loja da URL (403).
2. Para cada item, na ordem do pedido:
   - produto inexistente, de outra loja ou não vendável **vai para a lista** e o laço segue;
   - produto vendável é cotado: **primeiro a estrutura** — pertence, repetida, mínimo e máximo de
     cada grupo, quantidade; o 400 **interrompe tudo** —, **depois a disponibilidade de cada
     opção escolhida**, e **cada** opção indisponível vai para a lista.
3. Lista não vazia → **409 com a lista inteira**. Vazia → 200.

O corpo do 200:

```json
{ "linhas": [ { "produtoId": "…", "nomeDoProduto": "Pizza de calabresa", "precoBase": 40.00,
                "opcoes": [ { "opcaoId": "…", "nomeDoGrupo": "Tamanho", "nomeDaOpcao": "Grande",
                              "acrescimo": 15.00 } ],
                "precoUnitario": 55.00, "quantidade": 2, "subtotal": 110.00,
                "modoDeControle": "QUALITATIVO" } ],
  "subtotalDosItens": 110.00, "cotadoEm": "2026-11-21T17:00:00Z" }
```

- Uma linha por item do pedido, na ordem do pedido.
- `opcoes` na ordem dos grupos e, dentro do grupo, na ordem das opções.
- `precoUnitario = precoBase + Σ acrescimo`; `subtotal = precoUnitario × quantidade`;
  `subtotalDosItens = Σ subtotal`. Dinheiro como **número**.
- `cotadoEm` pelo relógio do serviço (`RelogioConfig`).

O corpo do 409 — `ProblemDetail` com `indisponiveis`, uma entrada por coisa caída, **sem nome** (ADR-088, Decisão 7):

```json
{ "status": 409, "detail": "há itens que não estão à venda agora; confira a lista",
  "indisponiveis": [ { "item": 1, "produtoId": "…", "opcaoId": null },
                     { "item": 2, "produtoId": "…", "opcaoId": "…" } ] }
```

`item` é a posição do item no pedido, a partir de zero: o mesmo produto em dois itens são duas
entradas, e a lista não tem repetição. O `detail` não conta. O corpo da requisição: `{ "itens": [ { "produtoId", "quantidade",
"opcoes": [uuid] } ] }`.

### §3.2 · O domínio

- **`Produto.cotar(int quantidade, List<UUID> opcoesEscolhidas)`** devolve o record de domínio
  **`LinhaCotada`** (com **`OpcaoCotada`**). A estrutura lança `RegraDoCatalogoViolada` com quatro
  `Motivo` novos — **`QUANTIDADE_INVALIDA`**, **`OPCAO_FORA_DO_PRODUTO`**, **`OPCAO_REPETIDA`**,
  **`ESCOLHAS_FORA_DOS_LIMITES`** (dados: o nome do grupo, o mínimo, o máximo, o que veio) —, que o
  `statusDe` já manda para 400.
- **Depois da estrutura, o `cotar` junta todas as opções escolhidas indisponíveis** e, se houver
  alguma, lança **`OpcoesIndisponiveis`** — exceção de domínio nova, com **a lista** dos `opcaoId`,
  na ordem dos grupos. *O nome é plural de propósito: a v1 tinha uma exceção com uma opção só.*
- O `cotar` não confere `vendavel()`: quem chama confere antes.
- As frases do `fraseDe`, no tom das que existem, **sem identificador nenhum**. Exemplos da forma:
  *"o grupo «Tamanho» pede 1 escolha e veio 0"*; *"a mesma opção veio duas vezes em «Pizza de
  calabresa»"*; *"uma das opções escolhidas não é deste produto; recarregue o cardápio"*; *"a
  quantidade de cada item é pelo menos 1"*.

### §3.3 · A aplicação

- **`ProdutoRepositorio.buscarPorIds(Collection<UUID> ids)`** → `List<Produto>`, pelo `findAllById`.
- **`CotarItens`** e **`CotarItensService`**: `contexto(estabelecimentoId)
  .orElseThrow(AcessoNegado::new)`, **e nenhum `pode(...)` depois**, com um comentário de uma linha
  apontando a ADR-088. Depois, a ordem da §3.1, capturando `OpcoesIndisponiveis` por item. Ao fim,
  lista não vazia → **`ItensIndisponiveis`**, em `application/exception/`.
- Records de aplicação para o que entra e o que sai (`ItemACotar`, `Cotacao`).

### §3.4 · A API

- **`CotacaoController`**: `@RequestMapping("/internal/merchants/{estabelecimentoId}/catalog")`,
  `@PostMapping("/quote")`, **`@Tag(name = "interno", …)`**. A forma do identificador e dos
  `@ApiResponse` copiada do `ConsultarProduto` no `ProdutoController` — **todo erro com
  `content = @Content`**.
- DTOs `CotacaoRequest` (`@NotEmpty` na lista, `@Min(1)` na quantidade, `@NotNull` nos ids) e
  `CotacaoResponse`.
- No `TratadorDeErros`: `ItensIndisponiveis` → 409 com `indisponiveis`. `OpcoesIndisponiveis` **não
  sai do caso de uso**, e o tratador não a mapeia.
- ⚠ **Não mexa à mão no javadoc do `TratadorDeErros`, do `SecurityConfig` nem do
  `ProdutoRepositorio`**: o script da §3.7 faz isso, com o texto exato.
- ⚠ **Nenhum log com o corpo da requisição.**

### §3.5 · Os testes — **com previsão**

**`CotacaoControllerIT`**, na forma do `ConsultaDeProdutoIT`:

| # | Caso | Afirma |
|---|---|---|
| 1 | pizza (base 40) com Tamanho «Grande» (+15) e Borda «Catupiry» (+8), quantidade 2; refrigerante (base 6) sem opções, quantidade 1 | 200; `precoUnitario` 63 e 6; `subtotal` 126 e 6; `subtotalDosItens` 132; os nomes do grupo e da opção; `modoDeControle`; `cotadoEm` |
| 2 | acréscimo negativo (−2) num produto de base 30 | `precoUnitario` 28 |
| 3 | o mesmo produto duas vezes com opções diferentes, e as opções chegando fora de ordem | duas linhas, na ordem do pedido; `opcoes` na ordem dos grupos |
| 4 | opção de outro produto | 400; a frase sem identificador |
| 5 | grupo obrigatório sem escolha | 400; a frase com o nome do grupo |
| 6 | grupo acima do máximo | 400; a frase com o nome do grupo |
| 7 | a mesma opção duas vezes | 400 |
| 8 | quantidade 0; e lista vazia | 400 nos dois |
| 9 | produto em `RASCUNHO` | 409; `indisponiveis` com ele, `item` 0, `opcaoId` nulo |
| 10 | produto `ESGOTADO_HOJE` | 409; idem |
| 11 | produto de outra loja; e produto inexistente | 409 nos dois; **os corpos iguais, salvo o `produtoId`** |
| 12 | uma opção escolhida esgotada | 409; `indisponiveis` com o produto **e** o `opcaoId` |
| 13 | **duas** opções escolhidas esgotadas no mesmo item | 409; **duas** entradas, o mesmo `item` e o mesmo `produtoId`, os dois `opcaoId` |
| 14 | um bom, um esgotado, um com opção esgotada | 409; **duas** entradas, `item` 1 e 2; o bom, `item` 0, não aparece |
| 15 | um esgotado e um com opção de outro produto | **400** |
| 16 | vínculo ativo **sem nenhuma permissão** | 200 |
| 17 | sem vínculo; e o `merchant` fora do ar | 403 nos dois, o mesmo corpo |
| 18 | sem token | 401 |
| 19 | cota, marca o produto esgotado, cota de novo | 200, depois 409 — **sem cache** |

⚠ *Nos casos 12, 13 e 14, cada opção esgotada fica num grupo que ainda tem outra opção disponível.
Se ela for a única de um grupo obrigatório, o produto deixa de ser vendável (C6) e cai como produto,
com `opcaoId` nulo.*

**`CotacaoDoProdutoTest`**, sem Spring, para a aritmética e a ordem das checagens no agregado.

**O `ContratoDeErrosIT`**: uma provocação por código da rota nova — 400 (lista vazia), 401, 403 (sem
vínculo) e 409 (produto em rascunho).

**O contrato:** regrave com `-Dopenapi.atualizar=true`, e confira que o `catalog-service.json` ganhou
**um caminho** e nada mais mudou de forma.

**As cinco mutações — regra 16.** ⚠ **Antes de escrever cada previsão, abra as asserções dos casos
que você escreveu**; se diferirem da tabela, vale a sua, escrita antes de rodar, e o relatório mostra
as duas. Para cada uma: copie o arquivo para fora da árvore, mute, rode só o `CotacaoControllerIT`,
**restaure da cópia** e confira com `diff` vazio.

| | A mutação | A minha previsão |
|---|---|---|
| M1 | no `Produto.cotar`, apague a checagem do máximo | cai o **6**, e só ele |
| M2 | no `CotarItensService`, não confira `vendavel()` antes de cotar | caem o **9**, o **10**, o **14** e o **19** |
| M3 | trate o produto inexistente como `ProdutoNaoEncontrado` (404) | cai o **11**, e só ele |
| M4 | no `Produto.cotar`, junte só a **primeira** opção indisponível | cai o **13**, e só ele — o 12 e o 14 têm uma opção esgotada por item |
| M5 | no `CotarItensService`, guarde num `Map` do próprio serviço o produto lido, por id, e leia dele antes do banco | cai o **19**, e só ele — é o caso que prova a falta de cache; a M2 prova o `vendavel()`, não o cache |

### §3.6 · O front: a formatação, os tipos, e os seis passos do `frontend.yml`

```bash
cd frontend
npx prettier --check src/api/configuracao.ts src/painel/ConfiguracaoDaLoja.tsx src/painel/ConfiguracaoDaLoja.test.tsx
npx prettier --write src/api/configuracao.ts src/painel/ConfiguracaoDaLoja.tsx src/painel/ConfiguracaoDaLoja.test.tsx
npm run tipos
git add src/api/generated
npm run format && npm run lint && npm run tipos:conferir && npm run typecheck && npm test -- --maxWorkers=2 && npm run build
cd ..
git diff --stat frontend/
```

- O primeiro `--check` tem de acusar **os três arquivos**, como a pipeline acusou. O `--write` só
  reformata: **o `git diff` desses três tem de ser de espaço e quebra de linha, nenhum token
  mudado** — confira com `git diff -w --stat frontend/src/painel frontend/src/api/configuracao.ts`.
- O `merchant.ts` ganha as duas rotas da M3-1 e o `catalog.ts`, a cotação.
- **Os seis passos, na ordem do `frontend.yml`, têm de passar todos.** O `tipos:conferir` regrava
  os tipos e compara com o índice: por isso o `git add src/api/generated` vem antes dele. Se
  qualquer passo falhar, pare e cole.

### §3.7 · Os três javadocs — pelo script, depois do código

```bash
node m32/aplica-m32-javadoc.js
git diff -- backend/services/catalog-service/src/main/java/com/deliveryplatform/catalog/config/SecurityConfig.java
```

`SecurityConfig` (*"não expõe rota interna nenhuma"*), `ProdutoRepositorio` (*"Quatro métodos"*) e
`TratadorDeErros` (*"Nove tratadores"*). **Rode-o depois de o `buscarPorIds` existir** — o javadoc
o cita com `{@link}`. E rode o `catalog` de novo, inteiro, depois dele.

---

## §4 · Os documentos — pelo script, depois dos testes verdes

```bash
node m32/aplica-m32-docs.js "$(date +%d/%m/%Y)"
git status --short
```

**Leia o script e a ADR-088 antes de rodar.** O script escreve a ADR-088 a partir de `m32/adr/`, e
faz 35 trocas e 6 emendas, cada trecho antigo casando uma vez só, ou nada é escrito:

| Arquivo | O que muda |
|---|---|
| ADRs 018, 024, 035, 054, 068 e 086 | status e emenda, cada uma respondendo a frase que a cotação deixa falsa |
| `README.md` das decisões | as seis linhas emendadas, e a da 088 |
| `catalogo.md` | §5: a ADR-088 ao lado da 018, a opção repetida (400), o produto inexistente (409); §9: a C10 cobrada na cotação; a §3: a cotação da porta da ADR-018 tem código, e a leitura do cardápio para o canal segue sem rota |
| `README.md` das decisões, o parágrafo da contagem | *"Oitenta e quatro escritas"* → *"Oitenta e cinco"* |
| `.claude/skills/novo-caso-de-uso/SKILL.md` | a fronteira do 404: id na URL é 404; id no corpo segue o documento do agregado (ADR-056:91-92) — **decisão do Rafael** |
| `premissas-do-front.md`:140 | a cotação é a da ADR-088; sai o *"ainda não existe em código"* |
| `docs/dividas.md` | a linha 3 (*"…e na M3-2, sobre `b275220` mais a ADR-088"*); a linha 65 (*"A conta, em …, na M3-2"*); o cabeçalho da segunda passada (28); o recorte (112); a menção que nega (−15); a célula **inteira** da linha 48; a célula da linha 69 |
| `docs/marcos/marco-3.md` | a fatia 2 como peça na **4.5a** (casos 9 a 14) e na **4.5d** (casos 1 a 3); a linha Estado e o parágrafo da fatia |

⚠ **A célula da linha 69 afirma *"medido na M3-2: só o `CotacaoController`"*.** Rode o comando
dela depois do script, e confira: `grep -rn 'Mapping("/internal' backend/services/catalog-service/src/main`.

⚠ **A regra 15, sobre esta rodada:**

```bash
grep -rn -e "internal/catalog/quote" -e "a rodada da cotação" docs contracts CLAUDE.md .claude backend --include=*.md --include=*.java | cut -c1-160
```

*A frase "o cardápio de quem compra entra pela porta da ADR-018" — `ProdutoRepositorio.java`:42,
`ProdutoController.java`:304, `ListaDeProdutos.tsx`:19 e as linhas 67 e 69 do razão — **continua
verdadeira e fica**: a v3 não afirma que a porta não lista.*

Só pode achar: o corpo das ADRs emendadas (018, 035, 054, 068, 086), as emendas novas, a ADR-088, o
índice das decisões, e o javadoc do `ContextoDeAcessoController`:33 (P8). **Outra: pare e diga.**

⚠ **Se o script parar**, cole o erro e pare.

---

## §5 · O razão — **previsão**

| | antes | depois | por quê |
|---|---|---|---|
| o recorte estreito devolve | 111 | **112** | a seção de gatilhos da ADR-088 |
| — menção que nega | −14 | **−15** | a ADR-088, *"Nenhum."* |
| linhas vindas do recorte | 57 | **57** | |
| linhas que contam | 129 | **129** | |
| linhas numeradas | 132 | **132** | |
| a segunda passada | 29 | **28** | o `premissas-do-front.md`:140 |

*Medido no clone: 108 → 109 e 29 → 28.* ⚠ **Se qualquer número se mover diferente disso, pare.**

---

## §6 · O relatório e os commits

**O relatório:** o §0, com o `tipos:conferir` de antes; as saídas da §2; a tabela da §3.5 com uma
coluna **resultado**; as cinco mutações — a previsão, o que caiu, e o `diff` vazio da restauração;
o `catalog` e o back antes e depois, em testes; o `git diff --stat` do contrato e dos tipos; o diff
dos três javadocs; a saída do script de documentos; a busca da regra 15; a conta do razão; e o
`tipos:conferir` **depois**, que tem de passar.

**Dois commits:**

```
feat(catalog): a cotação valida a seleção e precifica os itens, e o 409 traz tudo o que caiu (ADR-088)
```

com o código, os testes, os três javadocs, o `contracts/openapi/catalog-service.json`, os dois
arquivos de `frontend/src/api/generated/` e os três arquivos reformatados do front; e

```
docs: ADR-088, as emendas que a cotação pede, e a fatia 2 na tabela do marco 3
```

com o resto. E `git push`. **Depois do push, espere a pipeline e confira:**

```bash
gh run list --repo rafael-albuquerque07/delivery-platform --branch main --limit 8
```

O `frontend` tem de voltar ao **verde**, e o `catalog-service`, rodar verde. Se não voltar, cole o
`gh run view <número> --log-failed` no relatório. A pasta `m32/` vai para `_to_delete/m32-aplicada`.

*⚠ **A aposta.** A M4 e a M5 são as que mais ensinam: se o caso 13 não cair com a M4, o "todas" da
ADR-088 não tem teste; se o 19 não cair com a M5, a falta de cache também não.*
