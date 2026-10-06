# ADR-052 — A versão no documento, e o 409 que ela cria

- **Status:** aceita
- **Data:** 06/10/2026
- **Rodada:** G-C3a
- **Relacionada:** ADR-008 (replica set de nó único), ADR-017 (MongoDB documental),
  ADR-049 (o carimbo com a loja fechada)

## Contexto

A rodada G-C2 deu ao `catalog` o primeiro caminho de escrita:
`MarcarDisponibilidadeService` lê o `Produto` inteiro, chama `marcar` ou
`marcarOpcao` no agregado e grava o documento inteiro. O `ProdutoDocumento` não
tinha campo de versão — nem ele, nem nenhuma outra coleção ou entidade deste
repositório.

Duas pessoas da mesma loja marcando **opções diferentes do mesmo produto** ao
mesmo tempo leem o mesmo documento e gravam o documento inteiro. O que acontece
depois **depende de a gravação estar numa transação**, e as duas respostas foram
medidas no `ReativacaoNoExpedienteIT`:

- **fora de transação, a segunda gravação apaga a primeira em silêncio.** Caso 2,
  rodado sem `@Version`: a opção que a Marli esgotou voltou a `DISPONIVEL`, sem
  erro em lugar nenhum;
- **dentro de transação — que é como a marcação roda —, o próprio MongoDB recusa
  a segunda.** Uma transação que grava um documento alterado depois do início dela
  falha com o erro 112, `WriteConflict`. Caso 2b. Mas o Spring Data entrega esse
  erro como `DataIntegrityViolationException`, que **não tinha tratador**: na
  marcação, a corrida saía como **500**. A exceção foi medida no repositório; o
  500 na rota é dedução — nenhum tratador a pegava —, não medida pela rota.

A versão do pacote desta rodada dizia o contrário — que a transação não resolvia
e que "a última vence". A medição a desmentiu: a transação detecta; o que faltava
era dar ao conflito um nome e uma resposta.

E chega um segundo escritor na G-C3b. A reativação varre a loja na abertura do
expediente e reescreve todo produto com `ESGOTADO_HOJE` carimbado antes. A
corrida tem nome e hora: a Marli marca "acabou a calabresa" às 18h01; o evento de
abertura das 18h chega atrasado; as duas operações leem o mesmo documento e as
duas gravam.

## Decisão

**O `ProdutoDocumento` ganha `@Version`, e é o primeiro deste repositório.**

**O conflito tem um nome só.** O adaptador `ProdutoRepositorioMongo.salvar`
traduz o `WriteConflict` (erro 112, vindo como `DataIntegrityViolationException`)
em `OptimisticLockingFailureException` — a mesma exceção que o `@Version` levanta
fora de transação. Os dois dizem a mesma coisa, *alguém gravou no meio*, e quem
chama precisa de uma resposta só. Qualquer outro `DataIntegrityViolationException`
passa intacto.

O número da versão **atravessa o agregado como valor opaco.** O `Produto` ganha
um campo `versao` que nenhum método de negócio lê, escreve ou compara: o mapeador
o recebe do documento ao reconstituir (`Produto.reconstituir`) e o devolve ao
documento ao gravar. O javadoc do campo diz isso, e diz que quem o usar numa
regra está errado.

**A razão de não haver alternativa barata:** o `ProdutoMapper` **reconstrói** o
documento a partir do agregado a cada gravação — ele não mescla num documento
lido. Um `@Version` que não atravesse o agregado chega nulo em toda gravação, e
versão nula, para o Spring Data, significa **documento novo**.

**Na marcação, o conflito é 409.** O `TratadorDeErros` responde *"o produto mudou
enquanto você marcava; recarregue e tente de novo"*. Não é 500, porque não é
defeito do servidor, e não é 400, porque o pedido estava correto quando foi
feito. O cliente que recarrega acerta.

**Na reativação, o conflito é repetição, e ela é por documento.** Cada produto é
lido, decidido e gravado na sua própria transação; o conflito refaz **aquele**
documento, até três vezes (`delivery.reativacao.tentativas`). Esgotadas, a
exceção sobe — e quem decide o que fazer com ela é o consumidor da G-C3b, que
tem fila morta.

**A repetição mora fora da transação.** Repetir dentro de uma transação que já
falhou não repete nada: ela está condenada. O laço chama um método
`@Transactional` **de outro bean**, porque autoinvocação não passa pelo proxy do
Spring e o `@Transactional` seria silenciosamente ignorado.

## Consequências

**Toda gravação de produto passa a poder falhar por conflito, com uma exceção
só.** Hoje são dois chamadores — as duas rotas da G-C2 — e a reativação. Cada um
tem resposta escrita; nenhum deixa o conflito vazar como 500.

**Dentro de transação, a versão é a segunda rede, não a primeira.** Nos dois
caminhos de hoje quem percebe o conflito primeiro é a transação do MongoDB. A
versão é o que protege qualquer gravação que **não** esteja numa — e é o que
continua protegendo se um `@Transactional` deixar de valer sem ninguém ver, que é
a armadilha que esta mesma rodada nomeia.

**Produto novo continua sendo inserção.** Versão nula é documento novo, e é
exatamente o que `ProdutoDeTeste` e a futura rota de criação produzem.

**Uma gravação concorrente deixa de ser silenciosa ou 500, e passa a ser 409.**

**O resto do repositório continua sem versão.** O `merchant` grava por agregado em
PostgreSQL com cadeado explícito onde precisa (`FOR UPDATE`, na equipe); o
`conversation` não tem código. Esta ADR decide para o `ProdutoDocumento`, com o
caso em cima da mesa, e não institui controle otimista como política geral.
**Gatilho escrito:** a segunda coleção com mais de um escritor concorrente.

**Nenhum teste percorre HTTP → 409.** A exceção está provada no IT; o tratador,
lido. **Gatilho escrito:** o primeiro relato de 409 que ninguém consegue
explicar.

## Alternativas consideradas

**Só traduzir o `WriteConflict`, sem versão.** A transação já detecta o conflito
nos dois caminhos de hoje; bastaria nomeá-lo. **Recusada** porque a proteção
passaria a depender de **toda** gravação de produto estar numa transação que
vale — e a forma de ela deixar de valer é exatamente a autoinvocação, que não
dá erro, não dá aviso e não deixa teste vermelho. Sem a versão, esse dia volta a
ser o caso 2: a última gravação vence em silêncio.

**Atualização condicional, sem versão.** Um `$set` no caminho exato filtrado pelo
estado e pelo carimbo atuais. Mais barata e mais precisa para a reativação.
**Recusada por dois motivos, e o segundo é decisivo:**

1. ela passa por fora do agregado, e este repositório tem `Produto.marcarOpcao`
   como único caminho para mexer numa opção exatamente para impedir isso;
2. **ela protege um dos dois escritores.** A marcação grava o documento inteiro.
   Se a reativação fizer um `$set` cirúrgico e a marcação salvar, logo depois, um
   documento que leu **antes** dela, a marcação ressuscita o `ESGOTADO_HOJE` nas
   opções que a reativação acabou de limpar. A versão protege os dois lados, e
   ainda fecha a corrida entre duas marcações.

Este argumento é do relatório do reconhecimento da G-C3.

**Versão no agregado, como conceito de domínio.** Recusada: a versão não é fato do
domínio do cardápio, é mecanismo de persistência. Opaca no agregado é o preço
mínimo que o mapeador impõe.

**Cadeado pessimista.** Recusado por tamanho e por forma: custa um campo de estado
que precisa de limpeza por tempo, e o conflito aqui é raro — duas pessoas na mesma
loja no mesmo produto no mesmo segundo.

**Não fazer nada agora, e tratar na G-C3b junto com o consumidor.** Recusada
porque o defeito está **em `main`** desde a G-C2 — como 500 na marcação
concorrente —, e porque a correção não depende do consumidor.
