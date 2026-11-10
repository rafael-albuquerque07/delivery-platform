# ADR-078 — O produto sai do cardápio, sob a permissão que já existia

- **Status:** aceita · **emendada em 10/11/2026**: o rascunho responde 409, e a atribuição estava errada
- **Data:** 08/11/2026
- **Rodada:** E-7
- **Relacionada:** ADR-077 (o grupo de opções ganha rota — emendada por esta), ADR-065 (o
  produto nasce por rota — emendada por esta), ADR-063 (a recusa do rascunho é regra), ADR-056
  (o id de outra loja é 404), ADR-053 (o contrato declara as recusas), ADR-067 §6 (o publicado
  sem botão de volta) e o `docs/dominio/catalogo.md`

## Contexto

**A E-6 deixou um beco.** No produto publicado a composição recobra as invariantes da
publicação, e um grupo novo nasce vazio — a C4 o recusa. O agregado tem a saída desde a G-B: um
produto `INATIVO` não é recobrado, e volta ao cardápio pelo `publicar()`, que cobra tudo. **Não
havia rota que levasse até lá.**

**O domínio já diz para onde o produto vai.** O `catalogo.md` §2 tem três estados, e o
`INATIVO` é *"Saiu do cardápio, histórico preservado"*; a §8 nomeia a transição `ATIVO →
INATIVO` ao declarar o `ProdutoDespublicadoV1`; e a C9 diz que o produto nunca é apagado.
`Produto.inativar()` faz a transição e recusa o rascunho, que nunca apareceu.

**E a permissão já existia.** `DESATIVAR_PRODUTO` está no enum do `merchant` e na
`PermissaoDoCatalogo` desde o primeiro recorte, no contrato do `merchant`, nos tipos do front e
no rótulo do convite — *"Tirar produto do cardápio"* —, e **não guardava rota nenhuma**: no
`catalog`, a única ocorrência era a declaração do enum. O `ADMINISTRADOR` a recebe com todas as
outras.

## Decisão

**`DELETE .../produtos/{produtoId}/publicacao`, o par do `PUT` que publica.** A publicação é
transição em sub-recurso (ADR-065); tirar do ar é retirar esse sub-recurso. **O produto não é
apagado**: fica `INATIVO`, e a resposta é 200 com ele inteiro.

**A permissão é `DESATIVAR_PRODUTO`.** Quem pode alterar e publicar o produto não o tira do ar
por isso, e o `IT` o afirma com um vínculo que tem `ALTERAR_PRODUTO` e não tem esta.

**A ordem é a da publicação:** autoriza, busca o produto **desta** loja — de outra loja é o
mesmo 404 de inexistente (ADR-056) —, inativa, grava.

**Tirar do ar o que já saiu é 200**, pela razão da publicação repetida (ADR-065 §5): repetir não
é conflito, e o cliente não precisa saber o estado antes de agir.

**O rascunho é 400**, com a frase que o tratador já tinha: *"este produto ainda é rascunho e
nunca apareceu no cardápio; não há o que tirar do ar"*. A ADR-063 conta o rascunho que não se
inativa entre as regras que o comerciante pode causar, e regra é 400 no `catalog`. **O
`CLAUDE.md` diz que transição fora da tabela é 409**; a ADR-063 nomeou este caso, e o
`ErroDoCatalogoTest` o afirma desde a E-3b. Esta ADR segue a ADR-063 e registra a diferença.

**Sem 409 declarado**, como na publicação: a gravação concorrente pode dar o do `@Version`
(ADR-052), e provocá-lo exigiria costura no código de produção.

## Consequências

**O beco fecha pela rota.** Publicado → grupo recusado → tirar do ar → grupo → publicar ainda
recusado pela C4 → opção → publicar. O `DespublicacaoDeProdutoIT` faz isso por HTTP, e cada
passo responde o que a regra diz.

**Montar um grupo novo tira o produto do cardápio enquanto se monta.** Entre tirar do ar e
publicar de novo, quem compra não o encontra. É o custo, e é o mesmo da E-6: o cardápio não
exibe uma pergunta sem resposta.

**A disponibilidade não muda, e o domínio não diz se devia.** `inativar()` não toca a
disponibilidade; a marcação não olha o estado de publicação; e a consulta da reativação
(`idsParaReativar`) não filtra por ele — um `ESGOTADO_HOJE` fora do ar volta a `DISPONIVEL` na
abertura seguinte, como volta no ar. **O `catalogo.md` não escreve o que tirar do ar faz com o
que o dia marcou**, e esta ADR não inventa: deixa o que o código já faz, e escreve a pergunta.

**Nenhum evento sai.** O `catalogo.md` §8 declara o `ProdutoDespublicadoV1` por outbox, e o
`catalog` não tem outbox nem o consumidor tem código — a mesma situação do `ProdutoPublicadoV1`.

**A rota nasce sem tela**, como as duas da ADR-077, e contra a frase da ADR-065 que dizia que ela
não nasceria sem chamador. Quem a chama é o caminho da composição; a ADR-065 ganha emenda.

**É a primeira `@DeleteMapping` do repositório.** Dois comandos de reverificação do razão
procuravam a anotação para provar ausência de outra coisa — apagar seção, remover opção — e
passam a filtrar o caminho da publicação.

## Alternativas consideradas

**Voltar a `RASCUNHO`.** Recusada pelo domínio: apagaria a diferença entre *"nunca foi
publicado"* e *"saiu do ar"*, que a §2 do `catalogo.md` e o `EstadoDePublicacao` mantêm de
propósito.

**`PUT .../produtos/{produtoId}/inativacao`.** Recusada: um sub-recurso novo para o inverso de
um que já existe; o `DELETE` no mesmo caminho diz o mesmo sem nome novo.

**`ALTERAR_PRODUTO`, como a publicação.** Recusada: a `DESATIVAR_PRODUTO` existe para isto, está
publicada em quatro lugares, e o convite já a oferece com este nome.

**Recobrar as invariantes também no `INATIVO`.** Recusada: fecharia a saída que esta rota abre,
e o `publicar()` já cobra tudo na volta.

## Gatilho escrito

**A primeira pessoa que reclamar de o produto sumir do cardápio enquanto ganha um grupo.** Aí a
montagem fora do ar deixa de bastar, e nasce a alteração que se monta à parte e substitui a
publicada de uma vez.

**A primeira pergunta de por que um produto tirado do ar voltou com uma marcação que ninguém
lembra.** Aí o `catalogo.md` escreve o que tirar do ar faz com a disponibilidade, e esta ADR
ganha emenda.

## Emenda de 10/11/2026 — o rascunho responde 409, e a atribuição estava errada

Esta ADR dizia que *"a ADR-063 conta o rascunho que não se inativa entre as regras que o
comerciante pode causar"*. **A ADR-063 não decidiu isso:** ela cita o rascunho no Contexto,
numa lista, e a Decisão dela não trata de transição. **Quem levou o rascunho a 400 foi a ADR-053
§2, pelo mapeamento da classe inteira** — sem régua sobre o caso. E a frase *"regra é 400 no
`catalog`"* desta ADR não tinha outro apoio.

**Medido na R-10 e de novo na E-8:** das recusas puras de estado deste repositório, 5 respondem
409 e só esta respondia 400. **O rascunho passa a 409** (E-8), pela §3 da ADR-053, pela
convenção do `CLAUDE.md` e pela linha *"produto não `ATIVO`"* da §5 do `catalogo.md`. A frase
do tratador não mudou, e o 400 desta rota fica só para identificador malformado.
