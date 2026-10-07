# ADR-055 — O que o cliente mostra quando o servidor recusa

- **Status:** aceita
- **Data:** 07/10/2026
- **Rodada:** W-C
- **Relacionada:** ADR-016 (front mínimo), ADR-047 (o token no navegador),
  ADR-049, ADR-052, ADR-053

## Contexto

A G-E fez o contrato declarar os códigos que cada rota recusa, e deixou **o corpo
fora de escopo**, com gatilho escrito: *"a primeira tela que mostre `detail` ao
usuário — a W-C"*. A W-C é esta, e o gatilho disparou.

Três fatos do repositório obrigam a decidir agora:

**1. Os erros foram declarados sem corpo.** Para não repetir o defeito em que o
springdoc copiava o tipo de retorno do método para a resposta de erro, a G-E
declarou cada erro com `content = @Content`, vazio. Nos tipos gerados isso sai
como `content?: never` — conferido no `catalog.ts`. **O cliente tipado afirma que
a resposta de erro não tem corpo**, e ela tem: é um `ProblemDetail`.

**2. O 409 tem dois significados, e o código não os distingue.** Um é a loja sem
horário (ADR-049): *"Use ESGOTADO_INDETERMINADO."*. O outro é conflito de versão
(ADR-052): *"o produto mudou enquanto você marcava; recarregue e tente de novo"*.
Os remédios são opostos — trocar de estado contra recarregar — e a única diferença
visível está na prosa do `detail`.

**3. Uma das mensagens já é fixa de propósito.** O tratador do
`ExpedienteIndisponivel` devolve texto constante, e o javadoc diz por quê: *"A
mensagem é fixa — a da exceção pode nomear o host, e endereço de serviço não sai
na resposta."* Ou seja: **o repositório já trata `detail` como coisa que alguém
vai ler**, em um lugar, por escrito.

## Decisão

**O cliente mostra o `detail` que recebeu, e não interpreta o texto dele.**

```
400 · 409 · 503   →  a tela mostra `detail` como veio
401               →  não chega aqui: o cliente derruba a sessão (W-A)
403               →  a tela não deveria ter oferecido a ação; recusa genérica
sem detail        →  mensagem do cliente, por código
sem resposta      →  mensagem do cliente, e oferece tentar de novo
```

### 1. Quem lê o corpo de erro é um lugar só, e ele já existia

O `cliente.ts` lê o `detail` desde a W-A (`lerDetalhe`): só quando o corpo é JSON
de objeto e o campo é texto não vazio, e o entrega em `ErroDaApi.detalhe`. Nenhum
tipo de `ProblemDetail` foi escrito à mão — o pacote desta rodada trazia um, e ele
não era necessário.

**A dívida continua, e é outra:** o contrato não declara o esquema do corpo de
erro, então os tipos gerados não sabem que ele existe. Hoje isso custa nada,
porque só o `lerDetalhe` lê corpo de erro, e só um campo. **Gatilho escrito:** a
primeira tela que precise de **outro** campo do `ProblemDetail` além do `detail` —
o `type`, por exemplo (regra 2). Aí o esquema entra no contrato e sai dos tipos.

### 2. O cliente não ramifica no texto, e a desambiguação do 409 vem do pedido

Ramificar em prosa é acoplar a tela à redação de uma exceção Java. Em vez disso:
**a tela sabe o que mandou.** Um 409 numa marcação de `ESGOTADO_HOJE` pode ser loja
sem horário; um 409 em qualquer outro estado só pode ser conflito. E nos dois casos
mostrar o `detail` diz a coisa certa, porque é o servidor que escreve a frase.

A tela oferece **recarregar a lista** junto do erro em todo 409 — inofensivo no
caso da loja sem horário, e é o remédio no caso do conflito. E também no 503 e na
falta de resposta, em que o outro lado pode ter voltado.

**O que isto não resolve:** uma tela que precise *se comportar* diferente nos dois
409, e não só *dizer* diferente. **Gatilho escrito:** a primeira que precisar — e aí
o servidor ganha um discriminador legível por máquina, que é o campo `type` do
`ProblemDetail`, hoje não usado por nenhum tratador.

### 3. Nenhum `detail` chega à tela sem passar por um filtro

Uma resposta de erro pode vir de qualquer coisa no caminho — gateway, proxy, página
de erro do contêiner. O filtro tem duas metades: o `lerDetalhe` só aceita texto de
um objeto JSON, e a tradução da recusa (`recusaDe`) descarta texto em branco. Sem
`detail` aproveitável, a tela usa a mensagem do cliente para o código.

## Consequências

**O comerciante lê frases escritas em Java.** É aceito, e é melhor que a
alternativa: uma segunda redação no front, que diverge da primeira sem ninguém
notar. A contrapartida é que **mensagem de exceção passa a ser texto de produto**,
e o `CLAUDE.md` passa a dizer isso.

**Toda exceção nova precisa decidir se a mensagem dela pode ser lida por um
estranho.** Hoje só o `ExpedienteIndisponivel` o fez por escrito. As outras que
chegam à tela — `LojaSemExpediente`, `RegraDoCatalogoViolada` — nomeiam loja,
produto, grupo e estado, que são dados de quem já passou pela autorização, e
nenhuma nomeia host, caminho ou identificador técnico (conferido na G-C2 e na G-E).

**O 403 não deveria acontecer.** A seção da marcação só existe para quem tem
`ALTERAR_PRODUTO` no vínculo (W-B, W-C): uma ação oferecida é uma ação permitida. Um
403 na marcação significa que a permissão mudou entre carregar o painel e clicar —
e aí a resposta certa é **recarregar o painel**, não explicar. A tela diz isso e
não mostra o `detail`, porque nesse caso ele é fixo e genérico de propósito (M7: a
diferença entre 403 e 404 é um scanner de estabelecimentos escrito em códigos de
status). Recarregar não custa a sessão: ela mora em `sessionStorage` (ADR-047).

## Alternativas consideradas

**Traduzir cada código numa frase do front, e ignorar o `detail`.** Tela com
redação própria, independente do servidor. **Recusada por um caso concreto:** o 409
da loja sem horário precisa dizer *"Use ESGOTADO_INDETERMINADO."*, que é uma
instrução de domínio. Reescrevê-la no front cria uma segunda fonte da mesma regra —
e quando a regra mudar no servidor, a tela continuará dizendo a antiga, com
confiança.

**Ramificar no texto do `detail`.** Resolveria os dois 409 hoje. **Recusada:**
acopla a tela à redação de uma mensagem, que é a coisa mais barata de mudar num
repositório. A primeira melhoria de frase quebraria a tela sem quebrar teste nenhum
do back.

**Declarar o esquema do `ProblemDetail` no contrato agora.** É a resposta certa no
longo prazo, e **recusada por tamanho nesta rodada**: ela reescreve o contrato dos
dois serviços de novo, um dia depois de tê-los reescrito, para servir um campo que
o cliente já lê. Fica como gatilho na regra 1.

**Usar o campo `type` do `ProblemDetail` como discriminador, já.** Seria uma
linha por exceção. **Recusada porque nenhuma tela precisa disso hoje:** a
desambiguação pelo pedido resolve, e inventar um vocabulário de URIs antes de haver
quem o consuma é o erro que a ADR-054 acabou de registrar sobre identidade de
serviço.
