# ADR-065 — O produto nasce por rota, e o cardápio é publicado por quem o monta

- **Status:** aceita · **emendada em 08/11/2026**: a rota de tirar do ar nasce sem tela
- **Data:** 11/10/2026
- **Rodada:** E-3c
- **Relacionada:** ADR-053 (o contrato declara), ADR-055 (corpo de erro), ADR-056
  (`ProdutoNaoEncontrado` é 404), ADR-060 (a primeira rota de escrita), ADR-063 (o erro diz
  de quem é a culpa), ADR-064 (a categoria ganha persistência), ADR-066 (a referência do
  corpo que não resolve), as invariantes C1, C2, C4 e C5 do `catalogo.md` §9, e a coluna do
  marco 2 no `PRD.md` §10

## Contexto

**O `catalog` sabe tudo sobre produto e não recebe nenhum.** O agregado está escrito,
medido e coberto; a coleção existe com dois índices por Mongock; a listagem e a leitura têm
rota; a marcação de esgotado tem rota desde a W-D. **O que não existe é o caminho pelo qual
um produto entra no sistema** — e a única coisa que o cria hoje é o semeador.

A coluna do marco 2 no `PRD.md` §10 diz, com as palavras do próprio documento:
*"Publicar o cardápio e marcar o que acabou."* **A segunda metade está em `main` desde a
W-D, e com tela.** A primeira não tem rota.

E o `publicar()` é o caso mais extremo deste repositório: **ele cobra quatro invariantes —
C1, C5, C4 e C2, nessa ordem —, é idempotente, compõe mensagens com os nomes dos grupos, e
nunca foi chamado por ninguém que não fosse um teste.**

## Decisão

> **Duas rotas: o produto nasce rascunho por `POST`, e vai ao cardápio por uma segunda
> chamada. A publicação é um ato separado, e é de propósito.**

```
POST /api/v1/merchants/{estabelecimentoId}/catalog/produtos     201   CRIAR_PRODUTO
PUT  .../catalog/produtos/{produtoId}/publicacao                200   ALTERAR_PRODUTO
```

⚠ **A forma da segunda rota segue a da marcação de esgotado**, que já está em `main` desde
a W-D — a §2.1 do pacote manda colar os *mappings* do controlador antes de escrever, e
**vale o que estiver lá**, não o que esta ADR desenhou.

> **Medido na E-3c.** A marcação é `PUT .../produtos/{produtoId}/disponibilidade` — **`PUT`**,
> não `POST`, em sub-recurso. A publicação segue: `PUT .../produtos/{produtoId}/publicacao`,
> **sem corpo** — o único estado que ela pode pôr é `ATIVO`, e não há o que escolher. `PUT`
> também diz no verbo o que a §5 decide: repetir é inofensivo.

O gateway já roteia as duas: `Path=/api/v1/merchants/{merchantId}/catalog/**`, medido em
`backend/infra/gateway/src/main/resources/application.yml`:77.

### 1. Criar e publicar são dois atos, e o domínio já decidiu isso

`Produto.rascunho(...)` e `Produto.publicar()` são métodos distintos, e o estado de
publicação é um enum de três valores. **Uma rota que criasse já publicando teria de escolher
por conta própria o que fazer quando a publicação é recusada** — gravar o rascunho e devolver
erro, ou não gravar nada —, e as duas respostas mentem para quem lê: a primeira diz que
falhou tendo gravado, a segunda joga fora o trabalho de quem cadastrou.

**Separado, não há o que escolher:** o `POST` grava o rascunho, e a recusa da publicação é
uma segunda conversa, sobre um produto que já existe e que a pessoa pode consertar.

### 2. O corpo não carrega grupos nesta rodada

Grupos e opções ficam para a **E-3d**, e o motivo é de camada, não de tamanho:
`Produto.acrescentarGrupo` existe, **mas acrescentar uma opção a um grupo não tem método na
raiz** — o `GrupoDeOpcoes` é *record* imutável. **Rota antes do método seria desenhar a
borda de uma operação que o agregado não sabe fazer** — o trigésimo terceiro defeito.

**O que esta rodada entrega é o produto simples:** nome, seção, preço. *"Coca-Cola lata,
R$ 6,00, em Bebidas"* nasce, é publicada e aparece no cardápio — e é com produto simples que
um cardápio de bairro começa.

⚠ **Se `Produto.rascunho` exigir a lista de grupos**, o serviço passa a lista **vazia**. E
se o agregado **recusar** produto sem grupo, o corte desta rodada não se sustenta: **pare e
diga**, porque a decisão de criar produto sem grupo deixa de ser minha e passa a ser do
documento de domínio.

### 3. O 201 leva `Location` se houver para onde apontar

**As duas rotas de criação que já existem não levam `Location`** — a de loja (ADR-060) e a
de categoria (ADR-064). **E o motivo escrito da segunda é a ausência do destino:** *"Não há
`GET` de categoria por id, e esta rodada não cria um. Prometer `Location` para rota
inexistente foi o vigésimo sexto defeito."*

**Então o precedente não é "nunca `Location`": é "nunca prometer rota que não existe".** Se
a §2.1 mostrar que há `GET .../produtos/{produtoId}`, o 201 leva o `Location` dele. Se não
houver, não leva — e a ADR não ganha alternativa nova, porque é a mesma regra decidindo
diferente sobre fatos diferentes.

> **Medido na E-3c: há.** `GET .../produtos/{produtoId}` existe desde a G-F, e o 201 leva
> `Location: /api/v1/merchants/{estabelecimentoId}/catalog/produtos/{produtoId}` —
> caminho relativo, o mesmo que passa pelo gateway. O teste segue o `Location` e recebe 200.

### 4. As permissões, e uma delas é escolha declarada

| rota | permissão |
|---|---|
| criar produto | `CRIAR_PRODUTO` |
| publicar | `ALTERAR_PRODUTO` |

A primeira está no nome. **A segunda é escolha:** a `PermissaoDoCatalogo` tem quatro valores
— `VER_PRODUTO`, `CRIAR_PRODUTO`, `ALTERAR_PRODUTO`, `DESATIVAR_PRODUTO` — e **nenhum é de
publicar**. Publicar muda o estado de um produto que já existe, e é o mesmo verbo da
marcação de esgotado, que usa `ALTERAR_PRODUTO`.

⚠ **A §2.3 roda o `grep` antes**: se algum documento nomear permissão de publicação, **vale
o documento**, e esta tabela está errada. *Afirmar que nenhum documento nomeia, sem rodar o
comando, é o trigésimo defeito.*

### 5. A idempotência da publicação sai no código de resposta

`publicar()` é idempotente — **medido na E-3a**, não suposto. Publicar duas vezes não é erro
e não é criação: a rota responde **200** nas duas, e o corpo é o estado do produto.

**Não há 409 aqui**, e a razão é que não há conflito: o segundo pedido quer exatamente o
estado que já vale. O 409 deste repositório é do `@Version` (ADR-052), e é sobre escrita
concorrente.

### 6. O que esta ADR **não** faz

**Não dá tela ao cardápio** — é a W-F. **Nenhum texto desta rodada diz que o marco 2 está
cumprido**, e a coluna continua esperando a W-F e uma passagem humana no navegador.

**Não toca em grupos, opções nem no `substituirGrupos`.**

**Não cria `GET` novo.** Se o de produto por id não existir, ele nasce com a tela que
precisar dele.

## Consequências

**O `publicar()` passa a ter chamador, e as quatro invariantes passam a ser cobradas de uma
pessoa de verdade.** Até aqui C1, C2, C4 e C5 eram cobradas de testes — e a G-A provou que
teste pode afirmar o contrário do documento e ficar verde.

**E as frases da ADR-063 vão ao navegador pela primeira vez.** A recusa de publicação é o
caminho mais provável de um comerciante encontrar uma das frases novas — *"o grupo «Tamanho»
precisa de ao menos uma opção para o produto ir ao cardápio"*. Nesta rodada ela não aparece
em tela nenhuma; aparece no corpo da resposta, e a W-F a mostra.

**O gatilho escrito da ADR-063 vence aqui.** Com a primeira rota de escrita que monta um
agregado a partir de um corpo, **mede-se quais dos pontos de `INTEGRIDADE` passaram a ser
alcançáveis** — e um alcançável é `@NotNull` faltando no DTO, não motivo novo. É a §2.4.

## Alternativas consideradas

**Criar já publicando, com um `publicado: true` no corpo.** Uma chamada, uma tela. **Recusada
pela §1:** obriga a rota a decidir o que fazer com o rascunho quando a publicação é recusada,
e as duas respostas possíveis mentem.

**`PUT` no produto inteiro, com o estado de publicação como campo.** **Recusada:** transforma
quatro invariantes de publicação em efeito colateral de uma atualização de campo, e esconde
que `publicar()` é uma transição com regras próprias.

**Criar, publicar, grupos e opções na mesma rodada.** **Recusada pelo tamanho e pela camada:**
opção exige método novo na raiz, e a E-3b já mostrou o que acontece quando uma camada inteira
entra escondida dentro de uma lista de verbos — o trigésimo quarto defeito.

**`PATCH` para publicar.** **Recusada:** publicar não é alterar um campo; é uma transição
nomeada, e o repositório já escreve transições como `PUT` em sub-recurso (a marcação da W-D).

## Gatilho escrito

**A primeira tela que monte um produto com grupos** — é a E-3d, e ela arrasta o método novo
na raiz para acrescentar opção.

**O dia em que alguém pedir para despublicar pela tela.** O agregado tem o caminho; a rota
não nasce sem chamador.

~~**E o dia em que uma loja tiver mais produtos do que cabem numa resposta.** A listagem nasceu
sem paginação, como a de categorias e a de lojas.~~ **Retirado na E-3c, porque o fato não
confere:** a listagem de produtos **é paginada** desde a G-B3 (`PaginaResponse`,
`@PageableDefault(size = 20)`, e o teto em `spring.data.web.pageable.max-page-size`). Sem
paginação estão a de categorias (ADR-064) e a de lojas do `merchant`, e o gatilho da primeira
já está na ADR-064.

## Emenda de 08/11/2026 — a rota de tirar do ar nasce sem tela

A seção de gatilhos diz da despublicação: *"O agregado tem o caminho; a rota não nasce sem
chamador."* **A rota nasceu na E-7 sem tela que a chame**, e o corpo fica como está.

Ela nasceu pelo beco que a ADR-077 deixou: no produto publicado, grupo novo não entra, e a saída
do agregado — o `INATIVO`, que não é recobrado — não tinha rota. Quem a chama é o caminho da
composição: tirar do ar, montar, publicar. A decisão, a permissão e a forma estão na ADR-078; a
linha 62 do `dividas.md` registra o pagamento antes da condição.
