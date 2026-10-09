# ADR-056 — Recurso de outra loja é 404, e o 403 fica com um significado só

- **Status:** aceita
- **Data:** 09/10/2026
- **Rodada:** G-G
- **Relacionada:** M7 (`estabelecimento.md`), invariante 9 (`CLAUDE.md`),
  ADR-053 (o contrato declara o que a rota recusa), ADR-055 (o que o cliente mostra)

## Contexto

Em 09/10/2026 o `catalog` tinha **duas respostas para a mesma situação**, conforme o
método:

| Rota | Produto de outra loja |
| --- | --- |
| `PUT …/produtos/{id}/disponibilidade` (G-C2) | **403** |
| `GET …/produtos/{id}` (G-F) | **404** |

Nenhuma das duas vaza: cada uma responde igual para *"não existe"* e *"é de outra
loja"*, que é o que a M7 exige. A incoerência é entre elas.

**E ela nasceu de um defeito de método, não de uma discussão.** A G-C2 decidiu 403 e
escreveu a razão no javadoc do caso de uso: *"produto de outra loja é 403, não 404 —
a diferença entre os dois códigos é um varredor de identificadores"*. A G-F escreveu
404 afirmando que isso era *"a M7 um nível abaixo"* — e não procurou se a pergunta já
havia sido respondida.

## Decisão

**Recurso que não está na loja da URL é 404, nas duas rotas — e em qualquer rota
futura.** A marcação se alinha à leitura.

**403 passa a ter um significado só:** *você não tem acesso a esta loja, ou não tem a
permissão exigida.* Nada mais.

> **Autoriza pela loja da URL. Se a autorização passar, o recurso não estar naquela
> loja é ausência, não recusa — e ausência é 404, com o mesmo corpo de "não
> existe".**

### Por que a razão da G-C2 estava certa e a conclusão não

O argumento era: *a diferença entre os dois códigos é um varredor de
identificadores*. **Verdade** — se as duas situações respondessem diferente. Mas a
regra que isso exige é **"as duas respondem igual"**, e tanto 403 para as duas quanto
404 para as duas a cumprem. O argumento escolhe entre *distinguir* e *não
distinguir*; ele não escolhe **qual** dos dois códigos.

O que decide, então, é outra coisa: **o que o cliente consegue fazer com a resposta.**

### O argumento que decide, e ele vem do front

A ADR-055 e a W-C traduzem **403 como "o contexto está velho"**, e a tela oferece
recarregar o painel — porque o menu é construído das permissões do vínculo, e um 403
só pode significar que elas mudaram.

Com o 403 da marcação, **um identificador de produto errado ou velho pediria à pessoa
que recarregasse o painel inteiro**, em vez de dizer que aquele produto não é daquela
loja. Dois significados no mesmo código tornam a resposta inacionável: o cliente não
tem como saber qual dos dois recebeu.
**Esta interação foi medida pela metade.** O caso 5 do `MarcarDisponibilidade.test.tsx`
(W-C) afirma que um 403 na marcação oferece recarregar o painel; o caminho de um
identificador errado até ali não foi percorrido de ponta a ponta. O que torna isso
irrelevante é esta decisão: com 404, o 403 volta a ter uma leitura só, e o cliente
volta a poder agir sobre ele.
ter uma leitura só, e o cliente volta a poder agir sobre ele.

## Consequências

**A marcação muda de comportamento, e é mudança em `main`.** Produto de outra loja
deixa de ser 403 e passa a ser 404, com a mensagem fixa de
`ProdutoNaoEncontrado`. É a primeira vez que este projeto desfaz uma resposta já
commitada.

**As duas rotas de `PUT` ganham 404 no contrato**, e com ele duas provocações novas no
`ContratoDeErrosIT`. **É a segunda vez que aquele teste cobra algo** — na G-F e aqui —, e, como na
anterior, quem lembra é ele e não quem escreve.

**O front ganha uma mensagem para o 404** e oferece recarregar a lista: o produto pode
ter sido despublicado ou movido, e a lista é onde isso se vê. O 403 continua pedindo
o painel.

**A ordem das etapas vale para a marcação também**, e ela é mais estrita ali: a
conferência de loja vem **antes** de perguntar o expediente ao `merchant`. Sem isso,
um identificador de outra loja poderia receber 409 ou 503 — respostas sobre uma loja
que não é a dele.

**O que não muda:** opção que não pertence ao produto continua **400**. Não é caso de
loja: é seleção malformada dentro de um produto que o chamador pode ver, e a §5 do
`catalogo.md` já dá 400 ao pedido malformado.

**E fica uma regra para as rotas que ainda não existem:** `estabelecimentoId` na URL
autoriza; qualquer outro identificador na URL, se não pertencer àquela loja, é 404.
O `CLAUDE.md` passa a dizer isso.

## Alternativas consideradas

**403 nas duas — a leitura se alinha à marcação.** Mantém o precedente escrito e um
código só para toda recusa. **Recusada porque destrói a ação do cliente:** 403
passaria a significar *"a sua permissão mudou"* **e** *"esse id não é daqui"*, e
nenhum cliente consegue distinguir. A W-C já trata 403 como a primeira das duas, e
teria de parar de tratar — perdendo um comportamento correto para preservar um código.

**Manter a divergência e documentá-la.** Foi o estado entre 07/10 e 09/10, e o
`CLAUDE.md` chegou a instruir *"siga a rota vizinha da que você escreve, e não invente
o terceiro"*. **Recusada:** uma API em que o mesmo fato tem dois códigos conforme o
verbo é uma API que ninguém consegue programar contra, e a instrução de contorno é a
confissão disso.

**404 para tudo, inclusive para a falta de permissão.** Seria o extremo da M7:
nenhuma resposta distingue nada. **Recusada** porque a falta de permissão é
informação que o portador **tem direito a receber** — ele está autenticado e tem
vínculo com a loja; esconder que lhe falta `ALTERAR_PRODUTO` o deixaria sem saber o
que pedir a quem administra.

**Um corpo que distinga, mantendo os status iguais.** Recusada pela própria M7: a
distinção volta pela porta do corpo depois de ter sido fechada na do status. É o caso
6 do teste unitário da G-F, que existe exatamente para impedir isso.

## Gatilho escrito

A primeira rota cujo identificador na URL **não** pertença a uma loja — algo global,
ou de um usuário em vez de um estabelecimento. A regra acima fala de recurso dentro de
loja, e aquele caso é outro.
