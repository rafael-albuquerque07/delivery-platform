# ADR-053 — O contrato declara o que a rota recusa, e um teste prova

- **Status:** aceita
- **Data:** 06/10/2026
- **Rodada:** G-E
- **Emenda a:** ADR-039 (o contrato é gerado do código e congelado)
- **Relacionada:** ADR-011, ADR-045, ADR-049, ADR-052

## Contexto

Em 06/10/2026, aplicando a G-C3a, mediu-se o contrato congelado do `catalog`, e
na G-E o do `merchant`:

```
catalog   PUT …/produtos/{produtoId}/grupos/{grupoId}/opcoes/{opcaoId}/disponibilidade  200
catalog   PUT …/produtos/{produtoId}/disponibilidade                                    200
catalog   GET …/produtos                                                                200
merchant  GET /internal/merchants/{estabelecimentoId}/me/contexto-de-acesso             200
merchant  GET /internal/merchants/{estabelecimentoId}/expediente-corrente               200
merchant  GET /api/v1/merchants/{estabelecimentoId}/team                                200
merchant  GET /api/v1/me/estabelecimentos                                               200
```

**Sete rotas, e só 200.** Nenhum 400, 401, 403, 409 ou 503 — e todos existem em
código, nos `TratadorDeErros`, na cadeia de segurança e na conversão de
parâmetros do Spring.

O motivo é mecânico, e foi medido na G-E antes de qualquer decisão:

- os tratadores devolvem `ProblemDetail` montado em tempo de execução e **não têm
  `@ResponseStatus`**; o springdoc lê anotação, não corpo de método;
- **pôr `@ResponseStatus` num tratador piora**: com o padrão do springdoc
  (`override-with-generic-response: true`), a resposta do `@RestControllerAdvice`
  vira resposta de **todas** as operações. Acrescentado ao tratador da
  `LojaSemExpediente`, o 409 apareceu no `GET` da listagem, que nunca o devolve;
- o 401 nem passa pelo advice — sai da cadeia de segurança —, e o 400 de
  identificador malformado na URL sai da conversão de parâmetros do Spring.

E a `RegraDoCatalogoViolada` não tinha tratador: a marcação de um
`SEM_CONTROLE` como esgotado, ou de uma opção de outro produto, saía como **500**.

**É o quinto defeito deste projeto outra vez, do outro lado.** Na G-B3 o contrato
descrevia uma chamada que ninguém conseguia fazer, por falta de
`@ParameterObject`. Aqui ele descrevia um serviço que nunca falha. As duas são a
mesma coisa: o contrato gerado afirma, com a autoridade de ser gerado, algo que o
código não faz.

E a conta vence na tela seguinte: a W-C precisa tratar o 409 da ADR-052, e os
tipos do front saem deste arquivo.

## Decisão

**O contrato declara, por rota, os códigos que aquela rota pode devolver — e um
teste de integração prova cada um contra o serviço de verdade.**

### 1. A declaração mora na rota

Cada método de controlador declara as respostas dele em `@ApiResponses`, com uma
descrição e **sem corpo**. E o springdoc deixa de pendurar respostas do advice:
`springdoc.override-with-generic-response: false`, nos dois serviços, com o
motivo no YAML.

**Os tratadores não ganham `@ResponseStatus`.** Com a chave desligada o springdoc
o ignora — medido: o 409 sumiu de todas as rotas —, e em execução o status é o
do `ProblemDetail`. Seria uma anotação que não muda nada em lugar nenhum.

### 2. A regra de domínio é 400

A `RegraDoCatalogoViolada` ganha tratador: **400**, com a mensagem inteira (ela
nomeia produto, grupo e estado — dado da loja). É erro do chamador: ele pediu
um estado que o modo daquele produto não tem, ou uma opção que não é dele. Segue
a distinção que o `catalogo.md` §5 escreveu para a cotação — 400 para pedido
malformado, 409 para o estado do mundo que mudou. **Não 422**: um terceiro código
viraria precedente para os oito serviços sem que nada o exigisse.

### 3. O que entra no conjunto de uma rota

**Os códigos que a rota devolve por ser esta rota**: autenticação (401),
autorização (403), entrada malformada (400, incluindo identificador que não é
UUID), estado do mundo (409) e dependência fora do ar (503).

**Não entram os códigos de protocolo** — 405, 406, 415 —, que toda rota devolve
por ser HTTP. Nem 500, que é defeito e não resposta.

### 4. O teste fecha as duas direções

Um `ContratoDeErrosIT` por serviço, com **uma lista de provocações**: rota,
código esperado e como provocá-lo por HTTP. Dela saem as duas metades:

- **o observado está declarado**: um teste dinâmico por provocação chama a rota,
  confere o código e lê o contrato congelado para conferir que a operação o
  declara;
- **o declarado foi provocado**: todo código de erro do contrato está na lista,
  ou num mapa de não-provocáveis com o motivo escrito.

A segunda metade lê a lista, não um registro do que a primeira observou — então
nenhum caso depende de ordem.

O mapa de não-provocáveis está **vazio** nos dois serviços. O 409 por conflito de
versão (ADR-052) não é provocado — exigiria costura no código de produção —, mas
o 409 das rotas de marcação é provado pela loja sem horário, e o conflito fica
nomeado num comentário do teste para que ninguém o conte como coberto.

## Consequências

**O contrato deixa de ser só derivado e passa a ter uma parte afirmada.** É a
emenda à ADR-039: a forma continua gerada; o conjunto de erros é uma afirmação,
e por isso vem com prova.

**Rota nova nasce com os erros dela, ou o teste fica vermelho** — desde que alguém
provoque o erro. Um código que a rota devolve, nenhuma provocação produz e o
contrato não declara continua invisível. O teste prende o que está na lista; a
lista é responsabilidade de quem escreve a rota.

**Os dois contratos congelados foram reescritos**, e os tipos do front junto. Em
comportamento, só muda a `RegraDoCatalogoViolada`: de 500 para 400.

**O 401 passa a ser visível** no contrato, e o 400 de identificador malformado
também.

**Nada sobre o corpo.** O contrato não declara esquema de erro: o 401 nem tem
corpo, e os outros são `ProblemDetail` sem que ninguém tenha conferido o
conteúdo do `detail`. **Gatilho escrito:** a primeira tela que mostre `detail` a
alguém — a W-C.

**Um 403 que o código tem e o contrato não declara**, de propósito: `GET
/api/v1/me/estabelecimentos` recusa com 403 um token cujo `sub` não é UUID, e o
`identity` nunca emite um. Declarar faria o cliente esperar uma recusa que o
sistema não produz.

## Alternativas consideradas

**`@ResponseStatus` nos tratadores, e deixar o springdoc pendurar tudo em tudo.**
Uma linha por tratador e nenhuma anotação nas rotas. **Recusada, e medida:** o
409 apareceu no `GET` da listagem. Trocar erro omitido por erro inventado é o
mesmo defeito com o sinal trocado, e mais difícil de perceber, porque um
contrato generoso demais parece completo.

**Declarar à mão, sem teste.** Exatamente o que esta ADR existe para impedir:
documentação de erro que diverge do código é pior que documentação ausente,
porque alguém acredita nela.

**Um `OpenApiCustomizer` que derive a lista do código.** Deduzir quais exceções
uma rota alcança exige percorrer o grafo de chamadas, e qualquer aproximação erra
para mais ou para menos sem dizer qual. O teste erra também — mas erra
**vermelho**.

**Adiar até a W-C.** Recusada porque a W-C é quem consome, e consumir um contrato
errado é como ela nasceria errada.

## Gatilho escrito

O primeiro serviço **sem** rota autenticada, ou com um esquema de erro diferente —
o `conversation`, que recebe webhook com assinatura no corpo e não token. A
pergunta de lá é outra, e esta ADR não a responde.
