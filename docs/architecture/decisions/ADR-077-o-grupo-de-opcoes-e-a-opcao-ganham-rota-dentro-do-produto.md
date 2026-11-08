# ADR-077 — O grupo de opções e a opção ganham rota, dentro do produto

- **Status:** aceita
- **Data:** 08/11/2026
- **Rodada:** E-6
- **Relacionada:** ADR-065 (o produto nasce por rota), ADR-056 (o id de outra loja é 404),
  ADR-063 (a frase do 400 e o 404 da opção), ADR-052 (a versão do produto), ADR-053 (o
  contrato declara as recusas), ADR-018 (as opções congeladas no item do pedido) e o
  `docs/dominio/catalogo.md`

## Contexto

**O modelo estava escrito, e ninguém o chamava.** O `catalogo.md` descreve o grupo de opções
(nome, `minEscolhas`, `maxEscolhas`, ordem) e a opção (nome, acréscimo, disponibilidade, ordem)
**dentro** do produto, com as invariantes C3, C4, C5 e C7, e diz na §2: *"Publicar é onde as
invariantes são cobradas — em rascunho o comerciante pode deixar o produto pela metade."* O
código seguia o documento desde a G-B: `GrupoDeOpcoes` e `Opcao` são *records* imutáveis, a C3
é cobrada na construção do grupo, e a C4, a C5 e a C2 na publicação.

**E faltavam duas coisas.** A rota: o `POST .../produtos` não carrega grupos (ADR-065 §2), e só
a semeadura punha grupo e opção num produto. E o método: o `Produto` tinha `acrescentarGrupo`,
mas acrescentar uma opção a um grupo existente não tinha caminho pela raiz.

**A marcação da opção já tinha rota** — `PUT .../grupos/{grupoId}/opcoes/{opcaoId}/disponibilidade`,
desde a G-C —, e nenhuma opção criada por rota chegava a ela.

## Decisão

**Duas rotas de escrita, sob o produto:**

```text
POST /api/v1/merchants/{estabelecimentoId}/catalog/produtos/{produtoId}/grupos
POST /api/v1/merchants/{estabelecimentoId}/catalog/produtos/{produtoId}/grupos/{grupoId}/opcoes
```

**O grupo nasce vazio, e a opção entra num grupo que já existe.** Cada escrita passa pela raiz:
`Produto.criarGrupo` e `Produto.criarOpcao`. O corpo do grupo é nome, mínimo, máximo e ordem; o
da opção, nome, acréscimo e ordem. A opção nasce disponível.

**As regras são as do domínio, e nenhuma é nova.** Em rascunho vale a C3, que o próprio grupo
cobra — mínimo de zero até o máximo, máximo pelo menos 1. **No produto `ATIVO`, a escrita
recobra as invariantes da publicação** — C1, C5, C4 e C2, na ordem do `publicar()`, extraídas
para um método só — e recusa com 400 se o resultado as violar. A recusa deixa a lista de grupos
como estava. Sem a recobrança, o produto publicado ganharia por rota o que a publicação recusa:
um grupo que pede mais do que tem, ou um acréscimo que deixa a pizza de graça.

**A permissão é `ALTERAR_PRODUTO`** — montar as perguntas é alterar o produto —, na ordem da
publicação: autoriza, busca o produto **desta** loja (de outra loja é o mesmo 404 de inexistente,
ADR-056), altera, grava.

**201 com o produto inteiro, e sem `Location`.** Não há `GET` de grupo nem de opção, e a regra é
*"nunca prometer rota que não existe"* (ADR-065 §3). O corpo traz o grupo e a opção novos com os
ids que a próxima escrita — e a marcação — vão citar.

**O grupo que não é deste produto é 404, com exceção própria**, `GrupoNaoEncontrado`, e frase
fixa: *"este grupo não existe mais neste produto; recarregue o produto"*. Não reaproveita a
`OpcaoNaoEncontrada`, cuja frase diria a quem acabou de digitar a opção que ela "não existe
mais".

**Sem 409 declarado**, como na publicação: a gravação concorrente ainda pode dar o do `@Version`
(ADR-052), e provocá-lo exigiria costura no código de produção.

## Consequências

**A opção criada pela rota é marcável pela rota.** É o que o `MontagemDeOpcoesIT` afirma de
ponta a ponta: cria o grupo, cria a opção, marca a opção.

**Num produto publicado, grupo novo não entra.** O grupo nasce vazio, e um grupo vazio não
alcança o próprio teto, que é pelo menos 1 — a C4 recobrada o recusa. É o custo da decisão, e
ele foi escolhido: o cardápio não exibe uma pergunta sem resposta. O agregado tem o caminho —
um produto `INATIVO` não é recobrado, e volta ao cardápio pela publicação, que cobra tudo —, e a
rota de tirar do ar é o gatilho da linha 62 do razão. **E a frase da C4 sugere *"acrescente
opções"***, que nesse caso não é alcançável; a frase é de publicação e não foi reescrita.

**Opção acrescentada a um produto publicado só pode derrubar a C2.** Mais uma opção nunca
derruba a C4 nem a C5, que comparam limites com a contagem; um acréscimo negativo grande pode
fazer existir uma combinação de graça.

**Nada se corrige nem se remove.** As duas rotas acrescentam. Nome, limites e acréscimo errados
ficam como foram escritos.

**Nenhum evento sai.** O `catalog` não tem outbox, e o consumidor do cardápio não tem código —
a mesma decisão da publicação e da marcação.

## Alternativas consideradas

**Grupos no corpo do `POST .../produtos`.** Recusada: a criação do produto já tem corpo e regras
próprias (ADR-065, ADR-066), e um corpo aninhado traria no mesmo 400 a categoria, a C3 de cada
grupo e o acréscimo de cada opção. Duas rotas pequenas recusam uma coisa de cada vez.

**Recusar a escrita no produto `ATIVO`.** Recusada: a opção nova num grupo publicado — o sabor
que entrou no cardápio — é o caso comum, e passa pelas invariantes.

**Aceitar no `ATIVO` sem recobrar.** Recusada: a publicação passaria a ser contornável por duas
rotas, e o produto publicado ficaria num estado que a publicação recusa.

**Grupo como recurso de primeira ordem, com rota própria fora do produto.** Recusada: o grupo
não existe fora do produto (`catalogo.md` §1), e o agregado é a unidade de gravação e de versão.

## Gatilho escrito

**A primeira pessoa que pedir para corrigir um grupo ou uma opção já criados** — o nome, os
limites, o acréscimo. Aí nascem as rotas de alterar, e a regra do que uma correção pode mudar
num produto publicado.

**A primeira pessoa que pedir para tirar uma opção do cardápio para sempre**, em vez de marcá-la
esgotada. Aí nasce a rota de remover, e a recobrança decide quando a remoção é recusada.
