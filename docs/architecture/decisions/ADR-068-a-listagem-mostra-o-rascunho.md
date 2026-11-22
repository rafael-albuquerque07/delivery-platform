# ADR-068 — A listagem mostra o rascunho, porque quem a chama é o painel

- **Status:** aceita · **emendada em 22/11/2026**: a cotação da porta da ADR-018 tem código (M3-2)
- **Data:** 11/10/2026
- **Rodada:** E-3d
- **Relacionada:** ADR-018 (a porta de cotação do catálogo), ADR-053 (o contrato declara),
  ADR-056 (`ProdutoNaoEncontrado`), ADR-064 (porta com método que ninguém chama é
  superfície), ADR-065 (o produto nasce rascunho), ADR-067 (o cardápio ganha escrita), a
  §5 e a §7 do `catalogo.md`, e a C10

## Contexto

**A E-3c fez o produto nascer rascunho, e o rascunho é o único estado que a única listagem
esconde.** Medido na W-F, antes de qualquer linha de tela:

- `ProdutoRepositorioMongo`:101 filtra `EstadoDePublicacao.ATIVO.name()`;
- a rota de listar **não aceita parâmetro de estado**;
- o `ProdutoResumoResponse` **não carrega `estadoDePublicacao`**.

**A W-F parou aí, e parou certo.** Uma tela que cria rascunho e publica, montada sobre essa
rota, entrega o produto que **desaparece ao recarregar**: a Pizza doce recusada na publicação
fica no banco sem nenhum caminho até ela, porque só o `GET` por id a alcança e ninguém tem o
id. É a figura que a ADR-067 §2 recusou por escrito — *"produto invisível não se conserta"* —
e ela nasceria da própria rodada que a escreveu.

**O `catalogo.md` §5 escreveu a separação, com razão:**

> *"Ela devolve qualquer estado de publicação, e a listagem só os publicados. **A listagem é
> a vitrine do cardápio**, e esta é a tela de agir sobre um item."*

**E a razão tem premissa falsa.** Aquela rota vive sob
`/api/v1/merchants/{estabelecimentoId}/catalog/produtos`, **exige `VER_PRODUTO`**, e a
`PermissaoDoCatalogo` só tem permissões de comerciante. **Nenhum cliente a chama**, e nenhum
vai: o cardápio de quem compra é do `conversation`, pela porta da ADR-018
(`POST /internal/catalog/quote`), que **não existe em código** — medido em
`premissas-do-front.md`:122.

**Então o filtro não protege ninguém.** O único chamador é o painel do comerciante, e ele
está escondendo do comerciante o que o comerciante acabou de cadastrar.

## Decisão

> **A listagem deixa de filtrar por estado de publicação, e o resumo passa a carregar
> `estadoDePublicacao`.**

### 1. Sem parâmetro, e o motivo é o que cortou a porta da categoria

Nada de `?estado=`. **Parâmetro sem chamador é superfície** — é o argumento escrito da
ADR-064, do outro lado do mesmo serviço: *"porta com método que ninguém chama é
superfície"*.

**E o chamador que precisaria dele provavelmente não é esta rota.** Quem um dia quiser *"só
os publicados"* é o cardápio de quem compra, e esse tem porta decidida há dois meses
(ADR-018). Nascer um filtro aqui para servir um consumidor que vai entrar por outra porta é
desenhar para o lugar errado.

**Gatilho escrito:** o primeiro chamador que precise de um subconjunto. Aí o parâmetro nasce
**com ele**, e não antes.

### 2. O resumo carrega o estado, e isso não é cosmético

O `ProdutoResumoResponse` tem `vendavel`, e enquanto **tudo na lista era `ATIVO`** o
`vendavel: false` era legível: queria dizer *"cadastro perfeito num dia ruim"* — a pizza
está no cardápio e todos os tamanhos acabaram. É o que o javadoc da tela diz, e é verdade.

> **Com rascunho na lista, `vendavel: false` passa a confundir duas coisas opostas:**
> *"nunca foi publicado"* e *"acabou tudo"*. A primeira pede **publicar**; a segunda pede
> **repor**. Sem o estado, a tela não sabe qual botão oferecer.

**O `vendavel` continua derivado no servidor** (a decisão da W-C), e o estado **não o
substitui**: um produto pode ser `ATIVO` e não vendável, e é justamente esse caso que a lista
existe para mostrar.

### 3. O nome do método deixa de mentir

A porta chama-se `publicadosDe`. **Um método com esse nome devolvendo rascunho é exatamente
o tipo de frase que este repositório caça** — e a E-3b já escreveu o vizinho certo: a porta
da categoria usa `daLoja`.

⚠ **Cole a assinatura do `CategoriaRepositorio` e siga o nome dele.** *Prescrever nome sem
ler o vizinho é o nono defeito, e ele voltou duas vezes na E-3c.*

### 4. A ordem é da lista, não do banco por acaso

A tela da W-F agrupa por seção e ordena por `ordem` dentro dela. Uma lista que troca de ordem
entre recargas faz a pessoa clicar na linha errada — o argumento está escrito no seletor de
loja do `merchant` e no caso D14 da E-3b.

⚠ **A §2.1 mede se já há ordenação padrão.** Se houver, **ela fica**. Se não houver, a
consulta ganha **`ordem` e, para desempatar, `id`** — e vai escrito que o desempate existe
para ser determinístico, não por significado.

### 5. O índice não muda, e a §7 pode precisar de outra frase

O índice `(estabelecimentoId, estadoDePublicacao)` nasceu no `V001CriaIndicesDoProduto` para
servir a consulta filtrada. **Sem o filtro, o prefixo `estabelecimentoId` continua
servindo** — índice composto serve a consulta mais estreita.

**Esta rodada não cria nem derruba índice:** trocar índice é migração, com rodada própria e
medição de plano de consulta. ⚠ **Mas se a §7 do `catalogo.md` justificar aquele índice pela
filtragem**, a frase fica errada e a emenda a corrige. *Está no bloco 3.*

### 6. O que esta ADR **não** faz

**Não cria rota.** **Não toca no `GET` por id**, que já devolve qualquer estado, nem na
marcação, nem no `ProdutoResponse` inteiro, que já carrega o estado.

**Não muda uma linha de comportamento do front.** O javadoc do `ListaDeProdutos` diz
*"o cardápio publicado"* e passa a estar errado: **esta rodada corrige o comentário, e só o
comentário** — nenhuma string visível, nenhum teste. **A tela é a W-F**, e a frase do estado
vazio é dela.

**E não diz que o marco 2 está cumprido.** Isto é uma rota consertada; a coluna espera a W-F
e uma pessoa num navegador.

## Consequências

**O painel passa a ver o que ele mesmo criou**, que é a condição para a W-F existir.

**A W-F ganha o campo de que precisa** e perde a premissa P9 na forma em que eu a escrevi: a
listagem não é "a listagem dos publicados, paginada" — é a listagem do painel.

**E a C10 não é tocada.** *"`RASCUNHO` e `INATIVO` nunca cotizam"* é sobre **cotação**, e
cotação não passa por aqui: passa pela porta da ADR-018. Listar não é oferecer.

## Alternativas consideradas

**`?estado=` opcional, com padrão "todos".** Deixaria o filtro pronto. **Recusada pela §1:**
parâmetro sem chamador é superfície, e o chamador imaginado entra por outra porta.

**Uma rota nova para o painel, mantendo a atual.** Nada mudaria para quem já chama — e **só
quem já chama é a tela que a W-F vai reescrever**. **Recusada:** duas rotas que diferem por
um `where`, e a próxima pessoa tem de descobrir qual é a certa.

**Guardar o rascunho recém-criado no estado da tela, sem mexer no back.** **Recusada:**
criaria de propósito o produto que existe no banco e não tem caminho — o órfão que a ADR-066
§4 deixou vivo por herança, agora fabricado por nós.

**Partir a W-F: ler por seção agora, escrever depois.** **Recusada:** entrega a metade que
escreve sabidamente quebrada, e a rodada que a conserta seria esta mesma.

## Gatilho escrito

**O primeiro chamador que precise de um subconjunto por estado.** Aí nasce o parâmetro, com
ele.

**O dia em que a lista do painel não couber na tela.** A rota pagina desde a G-B3; **a tela
não** — ela mostra a primeira página e avisa. A composição por seção da W-F torna isso mais
visível.

**E o dia em que o cardápio de quem compra existir.** Ele entra pela ADR-018, e se alguém o
fizer passar por esta rota, **esta ADR é a que está errada** — e o conserto é a porta, não o
filtro.

## Emenda de 22/11/2026, M3-2 — a cotação da porta da ADR-018 tem código

O texto acima diz que o cardápio de quem compra entra *"pela porta da ADR-018
(`POST /internal/catalog/quote`), que não existe em código"*. A cotação dessa porta ganhou código
na M3-2 (ADR-088), em `/internal/merchants/{estabelecimentoId}/catalog/quote`. A leitura do
cardápio para o canal segue sem rota, e a linha 69 do razão segue esperando por ela. O corpo fica
como está.
