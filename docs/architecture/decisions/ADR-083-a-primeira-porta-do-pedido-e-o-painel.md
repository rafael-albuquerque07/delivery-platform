# ADR-083 — A primeira porta do pedido é o painel, e o canal vem logo depois

- **Status:** aceita
- **Data:** 17/11/2026
- **Rodada:** P-2
- **Relacionada:** ADR-075 (o pedido nasce no canal — emendada por esta), PRD §5 (P4), §10,
  §10.1 e §11; H4.5, H4.2, H8.1 e H8.2; `pedido.md` (T01), `conversa.md` §5 e §10

## Contexto

**A ADR-075 pôs o 7a antes do marco 3**, com o recorte da H8.2 e o que a H8.1 e a H4.2 exigem
dele, *"de ponta a ponta, até o pedido confirmado"*.

**A M-5 mediu o que isso carrega.** O 7a cria o `Pedido`: o `conversa.md` §5 diz *"Só depois
disso o `order-service` cria o pedido em `RECEBIDO`"*, o §10 declara
`UUID criarPedido(RascunhoConfirmado r)`, e o `pedido.md` tem a T01 de `—` para `RECEBIDO`, com
o gatilho *"Cliente confirma por botão"* e cinco guardas. **O `order-service` tem um arquivo
Java**, e as pastas da API estão vazias. E o 7a depende de quatro coisas fora deste repositório:
**provedor, número real, custo por mensagem e um webhook com entrada pela internet** — este,
contra a regra de publicar porta só em `127.0.0.1`.

**A ADR-075 manteve escrita a alternativa que não escolheu**: *"O atendente lança o pedido no
painel"*, que *"continua sendo a única das três que cobre o pedido que chega por telefone"*. Ela
virou a **H4.5**, no marco 6.

## Decisão

> **A H4.5 entra com o marco 3, e é a primeira porta por onde um pedido nasce. O 7a vem logo
> depois do marco 3. A ordem de construção muda; a P4 e a fundação do canal não.**

## Consequências

**A T01 ganha um chamador real sem dependência externa**, e a passagem humana do marco 3 se faz
na máquina de desenvolvimento.

**A mitigação do §11 do PRD vale desde o primeiro pedido**: balcão e telefone têm entrada antes
do canal.

**O `criarPedido` do `conversation` passa a chamar um `order` que já existe e já foi exercido.**

**O preço:** o primeiro pedido do sistema nasce numa tela, e não na conversa que a P4 diz ser
predominante — *a primeira passagem humana do marco 3 exercita o caminho minoritário*.

**E o canal fica mais tempo sem código**, com doze invariantes escritas no `conversa.md` e
nenhuma exercida.

**O §10 do PRD passa a dizer que histórias cada marco entrega**, numa coluna própria, na mesma
alteração: a ordem que esta ADR muda é a de histórias, e o PRD não tinha onde escrevê-la.

## Alternativas consideradas

**Aceitar o tamanho, como a ADR-075 escreve** — o 7a antes do marco 3, criando o `Pedido` e
arrastando o `order-service` inteiro, com provedor e webhook antes do primeiro pedido. **Não
escolhida em 17/11/2026.**

**Cortar o 7a antes da T01** — a conversa termina num rascunho confirmado, e o pedido nasce no
marco 3. **Não escolhida:** o rascunho confirmado ficaria sem quem o consuma até o marco 3.

## Gatilho escrito

**Nenhum.** A ordem de construção é decisão nossa, e o teste do `README.md` das decisões é
*"alguém que não seja nós pode fazer isso acontecer?"*. Não pode.
