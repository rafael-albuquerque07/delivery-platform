# ADR-067 — O cardápio ganha escrita, e a seção passa a significar alguma coisa

- **Status:** aceita · **emendada em 10/11/2026**: o publicado ganha botão de volta
- **Data:** 11/10/2026
- **Rodada:** W-F
- **Relacionada:** ADR-016 (front mínimo), ADR-047 (a sessão), ADR-053 (o contrato declara),
  ADR-055 (corpo de erro), ADR-061 (a loja escolhida mora na URL), ADR-063 (o erro diz de
  quem é a culpa), ADR-064 (a categoria ganha persistência), ADR-065 (o produto nasce por
  rota), ADR-066 (a referência do corpo que não resolve), e a coluna do marco 2 no
  `PRD.md` §10

## Contexto

**As três rotas existem e ninguém as chama.** A E-3b deu rota a criar e listar seção; a
E-3c, a criar e publicar produto. Medido no relatório da E-3c: **o front não mudou uma
linha que não fosse o arquivo gerado do contrato.** São rotas sem chamador de tela — a
figura que o vigésimo nono defeito nomeou, e que a E-1 já pagou uma vez.

**E a tela do cardápio não é nova: ela já lê.** A lista de produtos é da W-B/W-C, o detalhe
abre grupos, opções e acréscimo, e a marcação de esgotado é da W-D, com tela. **O que falta
é escrita.**

**A seção, por enquanto, não significa nada para quem olha.** A `Categoria` virou gravável
anteontem, e nenhuma tela a mostra. Um comerciante que criasse uma seção hoje **não veria
diferença nenhuma** — e a próxima pessoa a abrir o projeto perguntaria para que ela serve.

## Decisão

> **O cardápio passa a ser lido por seção e escrito pela tela: criar seção, criar produto,
> publicar. Nenhuma rota nova no back.**

### 1. A tela compõe duas rotas, e o agrupamento é do cliente

A tela busca **as seções** (`GET .../catalog/categorias`) e **os produtos**
(`GET .../catalog/produtos`) e os mostra agrupados: seções pela `ordem` delas, produtos
pela `ordem` dentro de cada uma.

**É a primeira vista deste projeto montada com duas rotas do mesmo serviço**, e a ADR-066
previu que ela chegaria. **O back não ganha rota de "cardápio agrupado"**: juntar duas
raízes numa resposta é modelo de leitura, com dono e invalidação, e isto é uma tela de
administração com dezenas de itens, não o cardápio do cliente.

⚠ **Vale o que a §2.1 medir.** Se a tela de hoje já busca os produtos de um jeito próprio
— paginado, com filtro, com estado —, **a forma dela é a forma**, e esta ADR descreve o
agrupamento, não o mecanismo de busca.

> **Medido na W-F.** A tela busca com o gancho próprio, `useRecurso`, e mostrava a primeira
> página de vinte. O cardápio passa a pedir **a página do teto da rota** (`max-page-size`,
> 100) e avisa quando há mais. **E a listagem só passou a trazer o rascunho na E-3d**
> (ADR-068): antes dela esta tela era impossível, e a W-F parou por isso na primeira vez.

### 2. O produto sem seção aparece, e aparece por último

A ADR-066 decidiu que **ninguém conserta a referência órfã que já existe**: a regra vale na
escrita, e a leitura não valida. Então **é possível** haver produto cujo `categoriaId` não
está na lista de seções da loja.

> **Ele vai para um grupo no fim, chamado "Sem seção".**

**Esconder um produto que o comerciante cadastrou é pior do que mostrar um grupo feio.** Um
produto invisível não pode ser consertado, e um produto num grupo estranho pede conserto
sozinho. *É o caso que a ADR-066 deixou vivo de propósito, e a tela é onde ele aparece.*

⚠ **Se a §2 não encontrar nenhum produto assim**, o grupo não aparece — e o teste o monta,
porque caso que só existe em dado velho é caso que só um teste consegue montar.

### 3. O formulário do produto mora numa rota; a seção nasce em linha

```
/painel/lojas/:estabelecimentoId/produtos/novo      o formulário do produto
```

A seção **não ganha rota**: ela nasce de um campo na própria tela do cardápio.

**O critério, e ele vale para as próximas:** ganha URL o formulário com **mais de um
campo**, ou aquele cujo preenchimento alguém pode querer **retomar, recarregar ou mandar
por link** — é o precedente da ADR-061, e o cadastro de loja é o caso. **Fica em linha** o
que só **dá nome a uma coisa**, ao lado da coisa.

**Rota própria para digitar a palavra "Bebidas" é cerimônia**, e troca de tela por um
campo. Cinco campos sem URL não sobrevivem a um recarregamento.

### 4. O `ordem` é derivado pela tela — e isso é dívida nossa, escrita hoje

O `ProdutoRequest` exige `ordem`, com `@NotNull` (E-3c). **Nenhum comerciante tem opinião
sobre "ordem 0".** A tela calcula: **o maior `ordem` da seção escolhida, mais um** — e
**não mostra o campo**. A seção nova nasce com o maior `ordem` das seções, mais um.

> **Isto é um defeito do contrato, não um recurso da tela.** Um campo que a borda exige e
> que nenhuma pessoa fornece é um campo que a borda deveria preencher: a regra *"o produto
> entra no fim da seção"* é de domínio, e está vivendo no cliente.

**Não se conserta nesta rodada**, e o motivo é de camada: a W-F é uma rodada de front, e
emendar contrato, DTO, caso de uso e testes dentro dela faria a camada entrar escondida —
o trigésimo quarto defeito. **Gatilho escrito:** o **segundo** cliente desta rota, que vai
ter de reinventar a mesma conta. *Está no `dividas.md`.*

### 5. A recusa é a frase do servidor, e ela sai quando o campo muda

A tela **nunca compõe a frase de uma recusa de regra** — precedente da W-C e da W-E, e
razão de ser da ADR-063. O 400 do servidor traz o `detail`, e o `detail` vai à tela como
está.

**E a metade nova:** a mensagem **desaparece quando o campo a que ela se refere muda**, ou
no envio seguinte. Isto paga um achado da passagem humana de 10/10: *o banner da recusa
ficava na tela depois de o comerciante corrigir o campo* — e um erro que não sai diz que o
conserto não funcionou.

⚠ **A §2.2 mede como a tela da loja faz isso hoje.** Se ela já limpa, **a forma dela é a
forma**, e esta seção é só o registro da regra. Se não limpa, as duas telas passam a
limpar — a do cadastro de loja **está no escopo desta rodada por mecanismo**, não por
vontade de melhorar o que não é assunto.

### 6. Publicar é um botão no produto, e a recusa aparece onde o produto está

A recusa de publicação carrega **o nome do produto ou dos grupos** (ADR-063), e seria
absurdo mostrá-la num aviso no topo da página quando ela fala de um item específico.

**O produto publicado mostra o estado e não tem botão de volta:** não há rota de
despublicar, e inventar um botão que não tem rota é prometer o que não existe. *A ausência
está no `dividas.md`, com gatilho — a primeira tela que peça para tirar um item do
cardápio.*

### 7. O que esta ADR **não** faz

**Não cria rota no back.** Nenhuma. Se a tela precisar de uma, **pare e diga**: é decisão
de duas camadas, e não se toma de passagem.

**Não toca em grupos e opções** — é a rodada dos grupos, e o `GrupoDeOpcoes` espera um método novo na
raiz.

**Não mexe na tela de disponibilidade** (W-D) nem no parágrafo do convite no painel — o
segundo é achado registrado da passagem humana, e **não é assunto desta rodada**.

**E não diz que o marco 2 está cumprido.** A coluna se afirma depois de **uma pessoa** fazer
o caminho num navegador, com os semeadores desligados — é a §7 do pacote, e o resultado dela
não está escrito em lugar nenhum antes de acontecer.

## Consequências

**As frases da ADR-063 vão à tela pela primeira vez.** *"«Pizza doce» está com preço zero e
não vai ao cardápio; ponha o preço e publique de novo"* deixa de ser corpo de resposta num
teste e passa a ser o que uma pessoa lê. **É a primeira prova de que a taxonomia de erro
serve para alguém.**

**A seção passa a ter função visível**, e com isso a pergunta *"para que serve a
`Categoria`?"* fica respondida na tela, não só no `catalogo.md`.

**O `publicar()` ganha chamador humano.** Ele cobra C1, C5, C4 e C2 desde a G-B; a E-3c lhe
deu rota, e esta rodada lhe dá dedo.

**E a coluna do marco 2 fica a uma passagem humana de distância** — não cumprida por esta
rodada, e sim por quem a percorrer.

## Alternativas consideradas

**Lista plana, e a seção só no formulário.** Rodada menor. **Recusada:** entregaria um
cardápio em que criar seção não muda nada na tela, e a pergunta *"para que serve?"* ficaria
de pé com a resposta pior possível — *"para preencher um campo obrigatório"*.

**Uma rota de cardápio agrupado no back.** Uma chamada, nada de composição no cliente.
**Recusada pela §1:** é modelo de leitura sobre duas raízes, e nasce com dono e invalidação
ou nasce errado. A tela de administração não precisa.

**Esconder o produto sem seção.** Tela mais limpa. **Recusada pela §2:** produto invisível
não se conserta.

**Formulários os dois em rota própria.** Uniforme. **Recusada pela §3:** troca de tela para
digitar uma palavra.

**Consertar o `ordem` no back agora.** Correto no lugar certo. **Recusada pela §4** —
camada escondida dentro de rodada de front —, e **registrada com gatilho**, não esquecida.

## Gatilho escrito

**O segundo cliente da rota de criar produto.** Aí o `ordem` derivado vira regra duplicada
em dois lugares, e o conserto é no contrato.

**A primeira tela que peça para renomear, reordenar, ativar ou desativar uma seção.** As
três operações existem no agregado e não têm rota, e `reordenar` arrasta o outbox do
`catalog` (ADR-064).

**A primeira tela que peça para tirar um item do cardápio** — a rota de despublicar.

**E o dia em que uma loja tiver mais produtos do que cabem na tela.** A listagem de produtos
**é paginada** desde a G-B3; o que não existe é a paginação da **tela**, e a composição por
seção a torna mais visível.

## Emenda de 10/11/2026 — o publicado ganha botão de volta

**O §6 dizia que o produto publicado não tem botão de volta, porque não havia rota de
despublicar.** A rota nasceu na E-7 (ADR-078), e a W-J lhe deu tela (ADR-079 §2): o publicado
mostra **Tirar do ar**, sob `DESATIVAR_PRODUTO`, no mesmo lugar onde o rascunho mostra
**Publicar** — e o botão de publicar passa a valer também para o produto fora do ar.

**O terceiro gatilho desta ADR — a primeira tela que peça para tirar um item do cardápio —
disparou com a W-J.** A dívida dele tinha sido paga antes, pela rota (linha 62 do razão).

**A `ordem` derivada do §4 ganha dois irmãos**: a do grupo e a da opção (ADR-079 §4).

