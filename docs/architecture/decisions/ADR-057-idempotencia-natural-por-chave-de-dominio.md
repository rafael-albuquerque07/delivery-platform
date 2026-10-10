# ADR-057 — A idempotência natural por chave de domínio, e a segunda exceção à invariante 7

- **Status:** aceita
- **Data:** 10/10/2026
- **Rodada:** G-C3b
- **Relacionada:** invariante 7 (`CLAUDE.md`), ADR-043 (outbox + relay),
  ADR-048 (o primeiro consumidor de evento), ADR-026 (fila morta),
  ADR-049 (o carimbo com a loja fechada), ADR-052 (a versão no documento)

## Contexto

A invariante 7 do `CLAUDE.md` manda `processed_messages` para **todo consumidor que
escreve em banco**, e abre **uma** exceção escrita: quem só esvazia cache. A ADR-048
registrou essa exceção dizendo que o critério *"exclui todos os outros consumidores
previstos"*.

O consumidor da reativação é o primeiro que escreve em banco. Pelo texto vigente, ele
precisaria da coleção, do esquema, de uma decisão sobre expiração e de uma escrita a
mais por mensagem. **Esta ADR decide que ele não precisa**, e por isso reescreve as
duas frases acima.

**A decisão nasceu numa conversa de 29/09/2026**, ao desenhar a reativação, e até esta
ADR não existia no repositório — o `contracts/eventos.md`, cláusula 3, a registrava como
pergunta em aberto, "decide-se com o consumidor". É este documento que a torna uma
decisão do projeto.

## Decisão

> **Um consumidor que escreve em banco dispensa `processed_messages` quando o efeito
> da mensagem é _comparar_ uma chave de domínio que vem no próprio evento com o estado
> gravado — e não quando o efeito é acrescentar, somar, registrar ou publicar para
> fora.**

O teste do critério é uma frase: **processar a mensagem duas vezes deixa exatamente o
mesmo estado que processá-la uma vez, sem consultar nada que registre que ela já
passou.**

Na reativação isso vale porque a chave é o **expediente de referência**, e ele vem no
evento. O serviço compara o carimbo gravado em cada produto com aquela data (ADR-049)
e só altera quem está atrás. Na segunda entrega, nenhum produto está atrás, e o
resultado é `NADA`.

### O que a exceção obriga em troca

**Ela não é de graça.** Quem a invoca escreve um teste que **reentrega a mesma
mensagem** e afirma que o estado não mudou. Sem esse teste, a exceção é uma opinião
sobre o código.

É o caso 5 do `ConsumoDeExpedienteIT`.

### O que ela não cobre, e o exemplo que vem aí

O relay da ADR-043 **publica para fora**. Duas entregas da mesma mensagem são duas
publicações, e nenhuma comparação desfaz a segunda. A ADR-043 já tem o seu próprio
mecanismo do lado de quem escreve; o lado de quem consome, quando existir, cai na
regra geral.

## Consequências

**A dívida do `processed_messages` continua aberta, e o gatilho é rearmado.** A
ADR-048 a deixou com um gatilho que dizia, em substância, *"o primeiro consumidor que
escreve em banco"*. Esse consumidor chegou, e **não** paga a dívida — então o gatilho
tem de mudar, ou ele já disparou e não produziu nada. O novo está abaixo.

**A invariante 7 passa a ter duas exceções escritas**, e o `CLAUDE.md` passa a dizer
qual é o critério, não só qual é o caso. Uma exceção por caso é uma lista; uma exceção
por critério é uma regra.

**Quem for escrever o segundo consumidor tem uma pergunta a responder antes do
código**, e ela é curta: *qual é a chave, e ela vem no evento?* Se não vier no evento,
a exceção não se aplica — porque o que torna a comparação possível é o evento carregar
o dado. É a mesma frase que a ADR-054 deixou: *consumidor que recebe no evento o dado
de que precisa não autoriza nada*; aqui, não guarda nada.

**O risco que fica.** Se um dia a reativação passar a *registrar* o que fez — um
histórico, uma contagem, um evento de saída —, ela sai da exceção no mesmo instante, e
o teste do caso 5 é quem avisa: a segunda entrega deixaria de dar o mesmo estado.

## Alternativas consideradas

**Escrever o `processed_messages` agora.** Pagaria a dívida da ADR-048 e tiraria a
pergunta do caminho. **Recusada** porque o preço não é a coleção, é o esquema: expirar
por tempo ou nunca, chave por mensagem ou por par mensagem-consumidor, e o que fazer
quando a escrita do registro falha depois da escrita do efeito. São decisões de verdade
e nenhuma delas é exigida por este consumidor.

**Exigir `processed_messages` de todo consumidor, sem exceção.** É a leitura literal da
invariante 7 de hoje. **Recusada** porque transformaria uma comparação idempotente em
duas escritas e uma consulta, e a segunda escrita seria o único lugar do fluxo onde uma
falha deixaria estado inconsistente — a invariante criaria o problema que ela existe
para evitar.

**Deixar sem exceção e sem registro, calado.** Era o estado de fato enquanto esta ADR
não existia. **Recusada** pela razão mais simples: o `CLAUDE.md` diz o contrário, e
código que contraria o `CLAUDE.md` sem um documento é código que o próximo leitor vai
"consertar".

**Confiar na deduplicação do broker.** Não existe. O RabbitMQ entrega ao menos uma vez,
e é a reentrega que esta ADR trata.

## Gatilho escrito

**O primeiro consumidor cujo efeito não seja uma comparação** — um que acrescente, some,
registre ou publique para fora. Aí o `processed_messages` da ADR-048 deixa de ser dívida
e passa a ser requisito, e o esquema tem de ser decidido antes do consumidor.

**E um segundo, menor:** a primeira mensagem cuja chave de domínio **não venha no
corpo** e precise ser buscada. A exceção se apoia em o evento carregar o dado; sem
isso, ela não se apoia em nada.
