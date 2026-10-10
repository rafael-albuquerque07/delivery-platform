# ADR-048 — O primeiro consumidor de evento: topologia, idempotência e falha

**Status:** Aceita — 29/09/2026
**Emenda:** ADR-011 (a palavra *fanout*), ADR-026 (retentativa e fila morta),
ADR-043 (a confirmação que ninguém esperava)
**Invariante do `CLAUDE.md`:** 7 — e esta ADR abre a primeira exceção a ela
**Relacionada:** ADR-021 (catálogo de serviços), ADR-027 (mudança compatível),
ADR-045 (credencial entre serviços)
**Decide por:** `catalog-service`, e pelos sete consumidores que vierem depois

## Contexto

Até hoje este repositório publicava eventos e **não consumia nenhum**. Não havia
um `@RabbitListener` em lugar nenhum, nenhuma fila declarada, nenhum binding.

O primeiro consumidor define o padrão que os outros sete vão copiar, e ele
chega encontrando **três decisões escritas de forma contraditória** e **uma peça
que dez documentos citam e nenhum define**:

| o que | onde diz uma coisa | onde diz outra |
| --- | --- | --- |
| topologia | ADR-011: *"O evento vai para um exchange **fanout**"* | `merchant`, desde a C-B: `TopicExchange`, chave `merchant.vinculo.alterado.v1` |
| retentativa | `application.yml` de sete serviços: 2 s × 5 | ADR-026 §1: 1 s, 4 s, 16 s e fila morta |
| dedup | `CLAUDE.md`, invariante 7: *"Quem consome precisa de `processed_messages`. Sem exceção."* | ADR-011: fila **exclusiva por instância**, e cada instância precisa aplicar |
| `processed_messages` | dez arquivos a citam | **nenhuma migration, coleção, índice ou classe existe** |

## Decisão

### 1. A topologia é `topic` com fila exclusiva, e a ADR-011 perde uma palavra

O consumidor liga uma **fila anônima** — nome gerado no cliente (`spring.gen-…`), exclusiva,
auto-delete, não durável — à exchange `delivery.eventos`, com o padrão
`merchant.vinculo.#`.

**A ADR-011 protegia a segunda metade da própria frase, não a primeira.** Até esta
emenda, ela
escrevia: *"O evento vai para um exchange fanout, e cada instância se liga a ele
com uma fila exclusiva e temporária, **não a uma fila compartilhada** — fila
compartilhada entrega para uma instância só, e as outras N−1 ficam com dado
velho até o TTL."*

O que mata o cache é a fila compartilhada. Uma fila exclusiva ligada a um
`topic` pela chave certa entrega a cada instância **exatamente como a `fanout`
entregaria** — e ainda deixa o consumidor escolher o recorte, o que a `fanout`
não deixa. A palavra troca; o comportamento que a ADR exige fica inteiro.

**O consumidor redeclara a exchange**, com os mesmos três argumentos do
produtor. Não por posse: porque ligar uma fila a uma exchange que ainda não
existe falha com `NOT_FOUND`, e o `catalog` ficaria mudo se subisse antes do
`merchant`. Declaração divergente é recusada pelo AMQP, e essa recusa é a rede
que impede os dois lados de discordarem em silêncio.

**O binding é `merchant.vinculo.#`, e não a chave exata.** No dia em que houver
uma `v2`, este consumidor a recebe. Ele não vai saber lê-la — e aí quem decide é
a tolerância do ouvinte. Assinar só a `v1` faria o consumidor ficar em silêncio,
com o cache cheio e nenhum erro, que é a pior das duas falhas.

### 2. A invariante 7 ganha uma exceção, e o critério é estreito

> **Consumidor cujo único efeito é em memória do processo e é naturalmente
> idempotente não usa `processed_messages`.**

O motivo não é economia: é que **a invariante, aplicada aqui, produz a falha que
a ADR-011 manda evitar.**

- O cache é **por instância** — é o que a fila exclusiva garante.
- Uma `processed_messages` no banco do serviço é **compartilhada entre as
  instâncias**. A segunda veria o `eventId` que a primeira gravou e **pularia a
  própria invalidação** — ficando com permissão velha, sem erro nenhum.
- E não há *"transação do efeito"* (ADR-021) da qual participar: o efeito é
  memória.

Deduplicar por instância consertaria a primeira objeção criando estado que
cresce e que ninguém lê depois, mais um identificador de instância estável que
não existe neste sistema.

**Emenda de 10/10/2026 (G-C3b).** A frase original dizia que o critério *"exclui todos
os outros consumidores previstos"* — os do `order`, do `settlement`, do `delivery` e do
`conversation`, que escrevem em banco. **Não exclui todos.** O consumidor da reativação,
no `catalog`, escreve em banco e também dispensa o registro, por outro critério: o
efeito dele é **comparar** uma chave de domínio que vem no próprio evento com o estado
gravado, e comparar duas vezes dá o mesmo que comparar uma. A ADR-057 escreve esse
critério e o que ele obriga em troca — um teste que reentrega a mesma mensagem. Para
quem acrescenta, soma, registra ou publica, a invariante continua valendo inteira, e a
`processed_messages` continua sem definição — a dívida anotada abaixo.

**Consequência escrita:** as cláusulas 3 e 4 do `contracts/eventos.md` —
idempotência por `eventId` e descarte de evento velho por `occurredAt` — **ficam
vazias para quem só invalida**. Remover é idempotente; remover por causa de um
evento antigo apenas força uma consulta a mais. Elas voltam inteiras para o
primeiro consumidor que escrever alguma coisa.

### 3. Quando não dá para aplicar, esquece-se tudo — sem retentativa e sem fila morta

A ADR-026 decide 1 s, 4 s, 16 s e depois fila morta. **Não se aplica a
consumidor de invalidação**, por três razões:

1. a fila é **temporária** — uma fila morta ligada a ela morre junto, e
   reprocessar para uma instância que já não existe não é reprocessar;
2. reter uma mensagem por 21 segundos é reter uma **revogação** por 21 segundos;
3. existe uma resposta melhor do que repetir: **esvaziar o cache inteiro**.

> **Falhar para o lado seguro, num cache de autorização, é esquecer.**

Se não se consegue remover uma entrada, remover todas custa algumas consultas ao
`merchant` e não deixa **ninguém** com acesso que já foi retirado. A mensagem é
sempre confirmada: devolvê-la à fila repetiria a mesma falha em laço, enquanto o
cache já está vazio e correto.

**O mesmo gesto vale na reconexão ao broker.** A fila é temporária: se a conexão
cai e volta, ela renasce vazia e os eventos do intervalo não estão em lugar
nenhum — mas o cache, que vive no processo, não caiu junto. Um `ConnectionListener`
esvazia tudo quando a conexão se refaz.

O bloco `listener.simple.retry` do `application.yml` **não governa este
consumidor**, porque ele nunca recusa mensagem. Fica onde está, idêntico nos
sete serviços. **Gatilho escrito:** o primeiro consumidor que escreva em banco —
ele vai precisar da política da ADR-026 de verdade, e é aí que a divergência
entre o YAML (2 s × 5) e a ADR (1/4/16) tem de ser resolvida, com a armadilha do
`max-interval` padrão de 10 s no meio.

> **Disparou em 10/10/2026 (G-C3b), e foi resolvido sem tocar no YAML.** A política da
> ADR-026 foi para uma **segunda fábrica de contêineres**, só do consumidor da
> reativação, com os números em `delivery.consumo-de-eventos.*`. O bloco do YAML
> continua governando só a fábrica auto-configurada. E medido no jar nesta rodada: no
> Boot 4.1.1 a propriedade é `max-retries`, e **`max-attempts` não existe** — o `5`
> daquele bloco nunca foi lido. O `max-interval` padrão é de fato 10 s.

### 4. A corrida entre a leitura e a invalidação, e o contador que a fecha

Sem defesa, esta sequência acontece:

```
t0  a requisição não acha no cache e pergunta ao merchant
t1  o vínculo é revogado; o evento chega; a entrada é removida
t2  a resposta de t0 chega — com o contexto de ANTES — e é guardada
```

O evento chegou, foi aplicado, e não adiantou nada: uma permissão revogada fica
guardada por 60 s **depois** da invalidação.

A defesa é um contador de geração: quem lê anota o valor antes de perguntar e só
guarda se ele não mudou. **Qualquer** invalidação o incrementa, e portanto
descarta toda leitura em voo, inclusive de outras chaves. É exagerado de
propósito — descartar uma leitura boa custa uma consulta; guardar uma ruim custa
acesso indevido.

### 5. A chave do cache é texto, e não `UUID`

A ADR-011 diz `(usuarioId, estabelecimentoId)`. Quem lê tira o usuário do `sub`
do token, e **o `sub` pode não ser um `UUID`** — há um teste da G-B3 que passa
exatamente um assim, porque quem converte o `sub` é o `merchant` (ADR-038), não
o consumidor.

Como texto, um `sub` malformado vira uma chave que nenhum evento invalida — e
não precisa: o `merchant` responde 403 para ele, o negativo dura 10 s, e nenhum
`VinculoAlteradoV1` jamais falará de alguém que não existe.

## Emenda à ADR-043: a confirmação estava configurada e nunca era esperada

Os sete serviços declaram `publisher-confirm-type: correlated` e
`publisher-returns: true` desde a C-B. **Nenhuma linha de código registrava
callback, passava `CorrelationData`, ligava `mandatory` ou esperava
confirmação.** O relay marcava `publicado_em` assim que o `send` devolvia — ou
seja, assim que os bytes saíam pelo socket.

E havia uma consequência pior do que "talvez não tenha chegado": **mensagem sem
fila de destino é descartada pelo broker em silêncio.** Como nenhuma fila estava
ligada à `delivery.eventos` até esta rodada, **todo `VinculoAlteradoV1`
publicado desde a C-B foi descartado, e o outbox marcou cada um como entregue.**

O relay passa a esperar, com `mandatory` ligado:

| o que o broker fez | o que se conclui |
| --- | --- |
| `ack`, sem devolução | chegou numa fila: **publicado** |
| `ack`, **com** devolução | aceitou e não tinha para onde mandar: **pendente** |
| `nack`, ou nada dentro do prazo | não se sabe: **pendente** |

A segunda linha é a que engana: **o `ack` vem mesmo quando a mensagem é
descartada.** Confirmação diz "recebi", não "entreguei".

Manda-se o lote inteiro primeiro e espera-se depois: confirmação por mensagem
faria o pior caso ser cem vezes o tempo limite, dentro de uma transação
segurando cem cadeados.

A ADR-043 §3 continua valendo — a entrega é pelo menos uma vez. O que muda é que
agora ela é pelo menos uma vez **de verdade**, em vez de no máximo uma.

## Consequências

**Positivas**

- A revogação de acesso passa a valer em segundos, que é a razão de o
  `VinculoAlteradoV1` existir desde a C-B.
- A carga no `merchant` cai de uma consulta por clique para uma por minuto por
  par — o cálculo que a ADR-011 fez em agosto passa a ser verdade.
- O outbox deixa de poder dizer que publicou o que foi descartado.
- Existe, pela primeira vez, uma prova de ponta a ponta de que a exchange, o
  binding e o formato do envelope casam entre dois serviços.

**Negativas**

- **Uma exceção à invariante 7 é uma exceção.** O `CLAUDE.md` dizia "sem
  exceção", e a força daquela frase vinha de não ter nenhuma. O critério é
  estreito e está escrito, e é isso que impede a segunda.
- **A geração global descarta leituras boas.** Numa loja movimentada, cada
  mudança de vínculo joga fora todas as consultas em voo. Aceito: são
  milissegundos de trabalho, e a alternativa é uma geração por chave, que é mais
  código para o mesmo resultado nesta escala.
- **Esvaziar tudo na reconexão é grosseiro.** Uma oscilação de rede custa o
  reaquecimento inteiro do cache. É o preço de a fila ser temporária, e a fila é
  temporária porque a alternativa — uma fila durável por instância — acumularia
  mensagens para sempre para consumidores que já morreram.
- **O relay ficou mais lento.** Cada lote espera a confirmação do broker. Com
  lote de 100 e intervalo de 1 s, é folgado; se um dia não for, o caminho é
  aumentar o lote, não voltar a não esperar.

## Alternativas consideradas

- **Atualizar o cache em vez de invalidar.** O evento carrega o estado completo
  — `papel`, `estado` e a lista inteira de permissões —, então daria. **Rejeitada:**
  a ADR-011 e o `estabelecimento.md` §3 dizem "remove a entrada", e o
  `catalogo.md` §8, "invalida a cache", e atualizar obrigaria o consumidor a **ler os enums** do
  payload, trazendo a ADR-027 §2 — *"Valor novo em enum é incompatível por
  padrão"* — para dentro de um caminho que hoje não a toca. Invalidar custa uma
  consulta e não conhece enum nenhum.
- **`processed_messages` por instância.** Mantém a letra da invariante 7.
  **Rejeitada:** exige um identificador de instância estável que não existe, e
  cria estado que cresce e que ninguém lê depois — a mesma "promessa com sintaxe
  de código" que este repositório recusa.
- **Definir a `processed_messages` agora, para todos.** **Rejeitada nesta
  rodada, não em geral:** ela precisa de esquema, banco, chave e expiração, e
  nenhum dos consumidores que a usariam existe. Desenhá-la contra consumidor de
  mentira é o que a ADR-043 recusou fazer com o cache.
- **Fila durável e compartilhada, com um consumidor só.** Mais simples e
  entregaria a uma instância só. É exatamente a falha que a ADR-011 descreve
  como *"a primeira coisa a quebrar quando houver duas"*.

## A dívida que esta ADR não paga

**A `processed_messages` continua sem definição.** Dez arquivos a citam como se
existisse — `CLAUDE.md`, `README.md`, ADR-002, ADR-014, ADR-021, ADR-026,
`conversa.md`, `entrega.md`, `liquidacao.md` e a convenção de mensageria do
`build-logic`. Não há migration, coleção, índice nem classe.

Esta rodada não a define porque o consumidor dela ainda não existe, e porque as
citações discordam entre si: a ADR-014 pressupõe banco relacional
(*"constraint única e o comportamento exato em conflito"*), a `conversa.md` a põe
na configuração do MongoDB, e a chave é ora `eventId`, ora *"id da mensagem do
provedor"*.

**Gatilho escrito:** o primeiro consumidor que **escreva em banco** — hoje
previsto para o `order`, no marco 3. Ele chega precisando dela de verdade, e aí
o esquema é o assunto da rodada em vez de um detalhe dela.

> **Rearmado em 10/10/2026 (G-C3b).** O primeiro consumidor que escreve em banco chegou
> antes do `order` — a reativação, no `catalog` — e **não** precisa da
> `processed_messages` (ADR-057). Como estava, o gatilho teria disparado sem produzir
> nada. O novo está na ADR-057: **o primeiro consumidor cujo efeito não seja uma
> comparação** — que acrescente, some, registre ou publique para fora.
