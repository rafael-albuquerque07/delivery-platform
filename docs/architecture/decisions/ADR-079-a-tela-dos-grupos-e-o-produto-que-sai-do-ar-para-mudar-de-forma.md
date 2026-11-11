# ADR-079 — A tela dos grupos, e o produto que sai do ar para mudar de forma

- **Status:** aceita
- **Data:** 10/11/2026
- **Rodada:** W-J
- **Relacionada:** ADR-077 (o grupo de opções e a opção ganham rota), ADR-078 (o produto sai
  do cardápio), ADR-067 §4 e §6 (a `ordem` derivada; o publicado sem botão de volta — emendada
  por esta), ADR-055 (a tela mostra o `detail` como veio), ADR-068 §2 (o estado de publicação é
  lido) e o `docs/front/premissas-do-front.md` §3 (emendado por esta)

## Contexto

**A E-6 e a E-7 deram rota ao que nenhuma tela alcançava.** Criar grupo, criar opção e tirar
o produto do ar existiam por HTTP, e um comerciante não conseguia nenhum dos três. O tipo
gerado do front já trazia as três rotas, e a única chamada da escrita do cardápio era o
`publicarProduto`.

**A ação de publicar mora no cardápio por seção** (ADR-067 §6): um botão na linha do produto,
só no rascunho, com a recusa ao lado daquele produto. O publicado não tinha botão de volta,
porque não havia rota.

**E o servidor recusa grupo novo num produto publicado, sempre.** O `Produto.criarGrupo`
recobra as invariantes da publicação no `ATIVO`; o grupo nasce vazio, o teto dele é pelo menos
1, e a C4 o recusa (ADR-077). Não depende do que se digita. **Já a opção num grupo existente
o servidor aceita** — no `ATIVO`, só a C2 a recusaria.

**O `premissas-do-front.md` §3 manda o formulário não antecipar recusa de domínio**: *"Campo
obrigatório é forma, e a tela o marca. Invariante não."* Esconder o "criar grupo" no produto no
ar é saber, no front, que a C4 recobrada o recusa. A pergunta foi levada ao dono do produto
antes de escrever a tela.

## Decisão

### 1. Os grupos e as opções se montam no produto aberto, a partir do cardápio

A tela do produto aberto ganha, por permissão — `ALTERAR_PRODUTO`, a das rotas da E-6 —, o
formulário do grupo novo (nome, mínimo, máximo) e, em cada grupo, o da opção nova (nome,
acréscimo). Depois de cada escrita a tela relê o produto, como na marcação. A recusa é o
`detail` do servidor, como veio (ADR-055). A C3, a C4 e a C2 ficam no `catalog`.

**A montagem é do cardápio**, como a marcação é da disponibilidade: o produto aberto pela
seção "Disponibilidade" não monta. Nada entra no `secoes.ts`, e a `CONFIGURAR_LOJA` continua
com uma tela só.

### 2. Tirar do ar mora ao lado de publicar

Na linha do produto do cardápio, o publicado ganha o botão **Tirar do ar**, sob
`DESATIVAR_PRODUTO`, que chama o `DELETE .../publicacao` da E-7. O produto que volta substitui
o da lista, como na publicação, e aparece *"Fora do cardápio"*.

**E o botão de publicar passa a valer também para o `INATIVO`.** Sem isso, um produto tirado
do ar não tinha caminho de volta pela tela, e a saída deste §3 seria um beco.

### 3. A exceção: no produto no ar, a tela diz o estado e oferece a saída

Num produto `ATIVO`, no lugar do formulário do grupo novo, a tela diz **"Para mudar os grupos,
tire o produto do ar."**, com o botão de tirar do ar ao lado. Sem `DESATIVAR_PRODUTO` a frase
fica e o botão não: ela continua verdadeira.

**O critério, que é o que separa esta exceção da regra do §3 do `premissas-do-front.md`:** a
tela só deixa de oferecer uma ação quando o servidor a recusa **sempre, pelo estado do recurso,
e nunca pelo que a pessoa digitaria**. Validação de campo e invariante sobre o conteúdo
continuam do servidor: o formulário aparece e a recusa vem como veio.

**O formulário da opção fica no produto no ar**, porque o servidor a aceita.

**Estado de publicação desconhecido não abre escrita nenhuma** (premissas §4.1).

### 4. A `ordem` do grupo e da opção é derivada pela tela

O maior, mais um: dos grupos do produto, e das opções do grupo. É a mesma dívida da ADR-067
§4: a borda exige um campo que nenhuma pessoa fornece.

### 5. O que esta ADR não faz

**Não cria rota, contrato nem migração.** **Não remove grupo nem opção**: não há rota, e a
linha 107 do razão é o gatilho disso. **Não toca na tela da configuração da loja.**

## Consequências

**As rotas da E-6 e da E-7 ganham chamador.** O caminho que a E-7 mediu por HTTP — publicar,
tirar do ar, criar grupo, criar opção, publicar de novo — se faz numa tela, entre o cardápio e
o produto aberto.

**A tela passa a saber uma coisa do domínio:** que um produto no ar não ganha grupo. **Se o
servidor mudar, a tela fica mais restrita do que ele, e nunca menos**: ela esconderia uma ação
possível, e não ofereceria uma impossível. Nenhum teste fica vermelho nesse dia, e é por isso
que o primeiro gatilho abaixo existe.

**A opção nova num produto no ar pode mudar o que o cliente vê na hora.** É o comportamento do
servidor (ADR-077), e a tela não o esconde.

**A montagem fica em duas telas.** Publicar e tirar do ar moram no cardápio; grupo e opção, no
produto aberto. No produto aberto, tirar do ar aparece só como a saída do §3.

## Alternativas consideradas

**O formulário do grupo sempre visível**, e a recusa do servidor no produto no ar. Obedece ao
§3 do `premissas-do-front.md` sem exceção. **Recusada:** o formulário falharia sempre, depois
de a pessoa preencher três campos, e a frase da recusa fala da C4 em vez de dizer onde está a
saída.

**Tirar do ar num lugar novo, só na tela do produto.** **Recusada:** publicar mora na linha do
cardápio, e o gesto contrário mora no mesmo lugar.

**A frase no lugar dos dois formulários, grupo e opção.** **Recusada:** a opção o servidor
aceita no produto no ar, e esconder o formulário dela seria antecipar uma recusa que não
existe.

## Gatilho escrito

**A primeira rota que aceite grupo novo num produto publicado** — a alteração que se monta à
parte, da ADR-078. Aí a frase do §3 fica falsa e sai da tela, e a exceção desta ADR sai do
`premissas-do-front.md` com ela.

**A primeira pessoa que pedir para mudar a ordem de um grupo ou de uma opção.** Aí nasce a
rota de reordenar, e a `ordem` do grupo e da opção deixa de ser derivada pela tela.
