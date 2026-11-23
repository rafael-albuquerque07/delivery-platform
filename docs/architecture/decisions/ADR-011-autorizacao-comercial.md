# ADR-011 — Autorização comercial: cache em processo, invalidação por evento e fail-closed

**Status:** Aceita — 23/08/2026 · **emendada pela ADR-043** (24/09/2026): o cache
"em processo" mora em cada serviço que pergunta, não no `merchant`, e nasce junto
com o primeiro consumidor do `VinculoAlteradoV1` · **emendada pela ADR-045** (26/09/2026): a
credencial entre serviços é o token de quem pediu, e por isso `contexto` deixa
de receber `usuarioId` · **emendada em 28/09/2026**: a porta tem consumidor
desde a G-B3; o cache fica para a G-B4, com o evento que o invalida ·
**emendada em 29/09/2026**: a topologia é `topic` com fila exclusiva, e o
cache existe desde a G-B4 · **emendada em 17/10/2026**: `CONFIGURAR_LOJA`, e por que ela é ampla ·
**emendada em 19/10/2026**: a permissão não alcança quem já era membro · **emendada em 23/11/2026**:
a `LANCAR_PEDIDO`, e a dívida de 19/10 paga (M3-3)
**Relacionada:** ADR-012 (roteamento do gateway), ADR-015 (JWT com `NimbusJwtEncoder`)
**Detalha:** `docs/dominio/estabelecimento.md` §2 e §3
**Invariantes do `CLAUDE.md`:** 8 (nenhum serviço lê o banco de outro), 9 (identificador da URL não é confiável)
**Precisa existir antes do marco 1** — o `identity-service` é o primeiro consumidor da porta

## Contexto

Permissão neste sistema pertence ao **vínculo** entre usuário e estabelecimento,
não ao usuário. O mesmo Jorge é administrador na pizzaria e não enxerga nada no
mercadinho. A pergunta que todo serviço faz, em toda requisição, é "o que este
usuário pode fazer **nesta loja**".

Isso cria o objeto mais lido do sistema. E cria três problemas que precisam ser
decididos juntos, porque a resposta de cada um depende das outras:

1. **Onde a resposta é cacheada**, já que consultar o `merchant-service` a cada
   requisição de cada serviço é inviável.
2. **Como a revogação chega rápido**, já que cache e revogação são inimigos
   naturais — a Marli demite o Rafa às 20h e o acesso precisa cair agora.
3. **O que acontece quando o `merchant-service` está fora**, que é a pergunta de
   segurança de verdade.

A ADR-015 já decidiu que o JWT **não carrega permissão nem papel**. Isso não é
revisitado aqui: token com permissão dentro envelhece, e cresce com o número de
vínculos do usuário.

## Decisão

### O cache é local ao consumidor e vive em processo

Cada serviço mantém sua própria cache **em memória**, com Caffeine, atrás do
`AutorizacaoComercialPort`. Não há Redis nesse caminho.

```
Serviço X ──▶ AutorizacaoComercialPort
                  │
                  ├─ acerto na cache em processo  → responde em microssegundos
                  └─ falha → HTTP ao merchant-service → grava na cache
```

**Por que não Redis.** A cache existe para evitar uma ida à rede. Colocá-la no
Redis troca uma ida à rede por outra — mais barata, mas ainda assim uma. E
obrigaria `settlement`, `payment` e `conversation`, que hoje não têm Redis, a
ganhar uma dependência de infraestrutura para resolver um problema que 200 KB de
memória resolvem.

**Por que local e não compartilhada.** Uma chave única no Redis, escrita pelo
`merchant-service` e lida por todos, seria mais eficiente e tornaria a
invalidação um `DELETE` só. Foi rejeitada: transformaria o Redis num canal por
onde um serviço lê dado de outro sem passar pela porta dele, que é exatamente o
que a invariante 8 impede. A cache pertence a quem consulta.

| Parâmetro | Valor | Motivo |
|---|---|---|
| Chave | `(usuarioId, estabelecimentoId)` | A pergunta é sempre esse par |
| TTL de resposta positiva | **60 s** | Ver abaixo |
| TTL de resposta negativa | **10 s** | Também se cacheia "não tem vínculo", senão uma varredura bate direto no `merchant-service`. Curto porque acesso recém-concedido não pode demorar |
| Tamanho máximo | 10 000 entradas por serviço | Teto de memória previsível; descarte por menos-recentemente-usado |
| Timeout da chamada | **300 ms** | Além disso, a requisição do usuário já está lenta |
| Disjuntor | Abre após falhas consecutivas | Evita fila de requisições esperando um serviço morto |

### O TTL de 60 segundos é a janela de tolerância

Escolha deliberada, e o número tem raciocínio:

- **Segurança:** 60 s é o pior caso de acesso indevido depois de uma revogação
  cujo evento se perdeu. Para um sistema onde ninguém movimenta dinheiro sem
  registro, é aceitável.
- **Carga:** um atendente no pico faz alguns cliques por segundo. Com 60 s, cada
  serviço consulta o `merchant-service` uma vez por minuto por usuário, em vez de
  uma vez por clique. Reduz a carga em mais de 99%.
- **Disponibilidade:** e aqui está o ponto que costuma passar batido — **o TTL
  também é a tolerância a queda**. Se o `merchant-service` cair por 40 segundos,
  a maioria das requisições nem percebe, porque a cache ainda responde. Não é
  preciso inventar um "modo degradado" separado: ele já existe, e dura exatamente
  o TTL.

### Invalidação por evento é o caminho rápido; o TTL é a rede de segurança

O `merchant-service` publica `VinculoAlteradoV1` a cada criação, alteração,
suspensão ou remoção de membro. Cada serviço consome e **remove a entrada
correspondente** da sua cache.

```
Marli remove o Rafa
        │
merchant-service  ──▶ VinculoAlteradoV1 (topic, fila exclusiva por instância)
        │
        ├──▶ order-service     evict(rafa, pizzaria)
        ├──▶ catalog-service   evict(rafa, pizzaria)
        └──▶ …                 cada um na sua cache
```

Efeito em segundos. O TTL cobre o caso do evento perdido — e evento **se perde**,
por isso as duas coisas existem.

**Cada instância precisa receber o evento.** Cache em processo com N instâncias
significa N caches. O evento vai para um *exchange* **topic**, e cada instância se
liga a ele com uma **fila exclusiva e temporária**, não a uma fila compartilhada
— fila compartilhada entrega para uma instância só, e as outras N−1 ficam com
dado velho até o TTL.

No MVP há uma instância por serviço e isso é invisível. Está escrito aqui porque
é a primeira coisa a quebrar quando houver duas, e a falha é silenciosa.

### Indisponível é negado. Sem exceção, sem janela de graça

Cache expirada e `merchant-service` fora → **403**. Não se serve entrada vencida.

Considerei uma janela de graça — servir dado velho por alguns minutos durante uma
queda — e rejeitei. Ela inverte o modo de falha: em vez de "ninguém entra", vira
"todo mundo continua entrando com as permissões antigas", e a revogação para de
funcionar exatamente quando o sistema está instável. Fail-closed que abre sob
pressão não é fail-closed.

**O custo é real e assumido:** se o `merchant-service` cair por mais de um
minuto, a plataforma inteira para de autorizar. A resposta a isso é
disponibilidade — réplicas, health check, alerta — não relaxar a regra.

### O 403 é indistinguível de "não existe"

Sem vínculo ativo, com vínculo mas sem a permissão, ou estabelecimento
inexistente: **a mesma resposta**. Código diferente por caso transforma o erro
num scanner de estabelecimentos (invariante 9 e M7 de `estabelecimento.md`).

O corpo é `ProblemDetail` sem detalhe: "sem acesso a este estabelecimento".

### O que fica do lado de quem chama

```java
public interface AutorizacaoComercialPort {
    /** Vazio quando não há vínculo ativo — nunca nulo, nunca exceção de "não achei". */
    Optional<ContextoDeAcesso> contexto(UUID usuarioId, UUID estabelecimentoId);
}
```

O adaptador é quem cacheia. O caso de uso não sabe que existe cache, não sabe que
existe HTTP, e testa contra um duplo em memória.

> **Emenda — 26/09/2026 (ADR-045).** A assinatura desenhada acima recebe
> `usuarioId` porque o chamador **afirmava** quem era o usuário. Com o token
> encaminhado ele não afirma: **prova**. O parâmetro sai:
>
> ```java
> Optional<ContextoDeAcesso> contexto(UUID estabelecimentoId);
> ```
>
> O `ContextoDeAcesso` continua devolvendo o `usuarioId` — como resposta, que é
> o que ele sempre foi. Tudo o mais desta ADR fica de pé: Caffeine em processo,
> 60 s positivo, 10 s negativo, invalidação por `VinculoAlteradoV1`, e falha
> fechada. A forma final nasce com o primeiro consumidor, no marco 2.

## Consequências

**Positivas**

- Zero infraestrutura nova. Nenhum serviço ganha Redis por causa disto.
- O caminho quente não sai do processo: acerto de cache é microssegundos.
- Revogação vale em segundos pelo evento, e no pior caso em 60 s.
- A tolerância a queda curta sai de graça, sem modo degradado inventado.
- A cache pertence a quem consulta — nenhum serviço lê dado de outro por fora da
  porta.

**Negativas**

- **N caches significam N consultas a frio.** Oito serviços aquecendo
  independentemente batem mais no `merchant-service` do que uma cache
  compartilhada bateria. Aceito: acontece uma vez por minuto por par.
- **Fila exclusiva por instância é uma armadilha esperando.** Quem escalar para
  duas instâncias e usar fila compartilhada terá metade do sistema com permissão
  velha, sem erro nenhum aparecendo. Precisa de teste e de linha no `CLAUDE.md`.
- **Queda do `merchant-service` para tudo** depois de 60 s. É o preço do
  fail-closed, e está dito em voz alta.
- Cache em memória some no restart. Irrelevante: reaquece em segundos.
- **A ADR-015 fica com uma consequência que não estava explícita:** como o token
  não carrega permissão, *toda* requisição paga uma checagem. Esta ADR é o que
  torna esse custo aceitável.

## Alternativas consideradas

- **Permissão dentro do JWT.** Elimina a checagem inteira. Rejeitada na ADR-015 e
  não reaberta: revogação passaria a esperar a expiração do token, e o token
  cresceria com o número de vínculos, vazando a lista de lojas do usuário em toda
  requisição.
- **Cache compartilhada no Redis, escrita pelo `merchant-service`.** Mais
  eficiente, invalidação num `DELETE` só. Rejeitada por criar um caminho de
  leitura de dado alheio fora da porta — e porque um defeito de escrita passaria a
  contaminar os oito serviços de uma vez, em vez de um.
- **Janela de graça servindo cache vencida durante queda.** Rejeitada: inverte o
  modo de falha justamente sob instabilidade.
- **Sem cache, consultando sempre.** Rejeitada pelo óbvio, mas vale registrar o
  que ela teria de bom: revogação instantânea e nenhuma das armadilhas de
  invalidação acima. É o desenho correto se um dia a carga permitir.
- **TTL de 5 minutos.** Reduziria mais a carga. Rejeitada: cinco minutos de
  acesso indevido depois de uma demissão é longo demais para um sistema que
  registra dinheiro.

## Emenda que esta decisão provoca

`docs/dominio/estabelecimento.md` §3 e a tabela de armadilhas do `CLAUDE.md`
dizem "cache curto **no Redis**". Estava errado — ou melhor, estava indeciso, e
esta ADR decide. Os dois textos precisam passar a dizer **cache em processo**.

## Emenda de 28/09/2026 — o primeiro consumidor, e por que ele não tem cache

O `AutorizacaoComercialPort` desenhado aqui em agosto ganhou implementação na
G-B3: o `catalog` pergunta ao `merchant` por
`GET /internal/merchants/{estabelecimentoId}/me/contexto-de-acesso`,
encaminhando o token de quem pediu (ADR-045).

### O cache ficou para a rodada seguinte, e o argumento é desta ADR

Esta ADR aceita os 60 segundos como *"o pior caso de acesso indevido depois de
uma revogação cujo evento se perdeu"*. **Sem consumidor do
`VinculoAlteradoV1`, toda revogação é esse pior caso** — o que estava escrito
como exceção tolerada viraria o comportamento normal, e a frase que a justifica
deixaria de ser verdade.

A ADR-043 já tinha dito o mesmo por outro caminho: *"o cache sem o evento é um
cache que não invalida, e um cache de autorização que não invalida é uma falha
de segurança com nome de otimização"*.

Então na G-B3 cada requisição do catálogo pergunta ao `merchant`. O custo de
carga que esta ADR calculou — uma consulta por clique em vez de uma por minuto
— é real e é aceitável no MVP, com uma loja e um atendente. O cache entra na
G-B4, **junto** com o consumidor que o invalida.

### Vazio e indisponível são coisas diferentes, e a porta já as separa

A emenda de 28/09 à ADR-045 escreveu a tabela; a G-B3 a implementou:

| resposta do `merchant` | a porta | por quê |
| --- | --- | --- |
| **200** | contexto | |
| **403** | `Optional.empty()` | é uma resposta: o produtor consultou e disse não |
| **401** | `AutorizacaoIndisponivel` | **não é resposta** — ver abaixo |
| 5xx, tempo esgotado, conexão recusada | `AutorizacaoIndisponivel` | ausência de resposta |

**O 401 é o caso que não estava escrito.** Ele significa que o `merchant`
recusou um token que o `catalog` acabou de aceitar: os dois serviços discordam
sobre emissor ou audiência. Isso é erro de configuração entre ambientes, não
resposta sobre vínculo. Tratá-lo como "sem acesso" faria o defeito se disfarçar
de regra de negócio — e, com o cache da G-B4, ficaria escondido por dez
segundos a cada tentativa.

Os quatro terminam em **403 para o cliente**. Falha fechada, sem janela de
graça: *"Fail-closed que abre sob pressão não é fail-closed"*. O que muda é o
que se poderá guardar.

### Uma conferência que esta ADR não pedia e o adaptador faz

A resposta traz o `estabelecimentoId`, e o adaptador confere contra o que
perguntou. Divergência é `AutorizacaoIndisponivel`, não contexto. Autorizar com
o contexto de outra loja é a pior falha que esta porta pode ter, e sem a
conferência ela seria invisível.

### O que o `catalog` guarda do contexto

Três dos quatro campos que o `estabelecimento.md` desenha: `usuarioId`,
`estabelecimentoId` e as permissões — estas no recorte que o `catalog` nomeia
(ver a emenda de 28/09 à ADR-040). O `papel` vem no corpo e não é lido: o
`catalog` não tem regra que o consulte, e o mesmo documento diz que *"toda
autorização real é feita pela lista de permissões"*.

## Emenda de 29/09/2026 — o cache existe, e três números desta ADR foram lidos tarde

A G-B4 ligou o cache que esta ADR desenhou em agosto, junto com o consumidor do
`VinculoAlteradoV1` que o invalida.

### `fanout` era a palavra errada, e a frase seguinte já dizia o certo

Esta ADR dizia *fanout*; o `merchant` publica num *topic* desde a C-B. **A
propriedade que esta ADR protege é a segunda metade da própria frase** — *"não a
uma fila compartilhada"* —, e uma fila exclusiva ligada a um `topic` pela chave
certa entrega a cada instância exatamente como a `fanout` entregaria. A palavra
trocou; o comportamento exigido ficou inteiro. Ver a ADR-048 §1.

### Três parâmetros desta tabela ficaram de fora da G-B3

A tabela de parâmetros acima tem seis linhas. A G-B3 implementou a porta lendo
três delas e **sem notar as outras três**:

| parâmetro | esta ADR | o que a G-B3 entregou | o que a G-B4 faz |
| --- | --- | --- | --- |
| tempo limite | **300 ms** | 1 s de conexão, 2 s de leitura | volta a 300 ms nos dois |
| tamanho máximo | **10 000** | não havia cache | 10 000, configurável |
| disjuntor | "abre após falhas consecutivas" | não existe | **continua não existindo** |

O tempo limite é o que mais importa, e o argumento é o desta ADR: *"Além disso,
a requisição do usuário já está lenta"*. Com cache, a chamada ao `merchant`
deixa de ser o caminho comum — esperar dois segundos por ela passa a ser esperar
dois segundos numa exceção, e não numa regra.

**O disjuntor fica sem implementação, e agora com gatilho.** Ele depende do
`resilience4j`, declarado no catálogo de versões e que **não resolve em Boot 4**
— o artefato é o `-spring-boot3`. O gatilho já está escrito lá: *"quando a queda
de um serviço derrubar dois em cascata, ou quando alguém medir que o tempo
limite sozinho não basta"*. O cache reduz a pressão para que ele exista, porque
uma queda do `merchant` passa a ser invisível durante 60 s — que é o que esta
ADR chama de *"a janela de tolerância"*.

## Emenda de 17/10/2026 — `CONFIGURAR_LOJA`, e por que ela é ampla

A configuração da loja **não tinha permissão**. O `estabelecimento.md` diz que o `merchant` é
*"onde mora a configuração que determina o comportamento do sistema inteiro"*, e **nenhum dos dez
valores do `Permissao` dizia quem pode alterá-la** — medido na E-4.

**`CONFIGURAR_LOJA` governa a configuração inteira:** o horário agora (ADR-073), e as áreas de
entrega, o desconto de retirada e o pedido mínimo quando ganharem rota.

**Ampla de propósito.** Uma permissão por campo faria o enum crescer com cada linha da H1.2, e
**ninguém pediu** *"pode mexer no horário mas não nas taxas"*. Quando alguém pedir, ela se parte.

**E permissão, não papel.** Autorizar por *"só `ADMINISTRADOR`"* criaria **um segundo critério
de autorização** ao lado desta ADR. **Um mecanismo só.**

**Quem a tem, hoje:** o fundador, porque o `ADMINISTRADOR` recebe `EnumSet.allOf(Permissao.class)`
(`Membro.java`) — **a permissão nova entrou sozinha, sem lista literal a estender.**

**E o convite não a oferece na tela.** A rota de convite **aceita** a permissão — a A2 governa,
como governa as outras —, e **a tela não a mostra**: o `PermissaoDoConvite` do front a exclui
por tipo. Oferecê-la faria uma rodada de back mudar tela. *A assimetria está no razão, com
gatilho: a primeira tela de convite que a ofereça.*

**A compatibilidade:** o `Permissao` viaja no `VinculoAlteradoV1` e na resposta da rota interna
de autorização, e a emenda de hoje à **ADR-027** registra por que o valor novo não exigiu versão.

**Gatilho escrito: nenhum.**

## Emenda de 19/10/2026 — a permissão não alcança quem já era membro

A emenda de 17/10/2026 diz, de `CONFIGURAR_LOJA`: *"Quem a tem, hoje: o fundador, porque o
`ADMINISTRADOR` recebe `EnumSet.allOf(Permissao.class)`"*. **Vale para o fundador criado depois
dela, e para nenhum outro.**

`Membro.fundador` concede o conjunto inteiro **no momento da criação do membro**, e as permissões
ficam gravadas em `membro_permissao`. A permissão nasceu **sem migração**, e **nenhuma rota
concede permissão a membro já criado** — a alteração de permissões existe no
`GerenciarEquipeService` e não tem borda. **Toda loja cadastrada antes de 17/10/2026 fica sem
ela**, e a tela que ela protege não abre para o fundador dessas lojas.

**Medido em 19/10/2026**, no banco desta máquina: 7 membros com o conjunto de administrador, e só
**3** com `CONFIGURAR_LOJA` — os criados depois da E-4. A passagem do mesmo dia está em
`docs/operacao/passagem-humana.md`.

**Não é dívida com condição do mundo:** pagar é decisão nossa.

## Emenda de 23/11/2026, M3-3 — a LANCAR_PEDIDO, e a dívida de 19/10 paga

A H4.5 pede *"uma permissão própria, que nenhuma das permissões de H2.1 cobre"*: nasce a
**`LANCAR_PEDIDO`**. O fundador a recebe com as outras, em `Membro.fundador`, e a tela de convite
a oferece. **E ela alcança quem já existe** — o que a `CONFIGURAR_LOJA` não fez (a emenda de
19/10): a migration `V9` dá a `LANCAR_PEDIDO` **e a `CONFIGURAR_LOJA` que faltava** ao fundador,
que é o primeiro membro da loja — as três condições juntas: papel `ADMINISTRADOR`, o primeiro
membro criado na loja, e as dez permissões anteriores à E-4. **O colaborador convidado com as dez
não entra, nem o administrador promovido depois:** papel não é lista de permissões
(`estabelecimento.md` §2), e a promoção nunca deu permissão. Sem filtro de estado: permissão não é
acesso, e `SUSPENSO` ou `REMOVIDO` já barram o acesso sozinhos. Decisão do Rafael.

A exceção da ADR-027 (emenda de 17/10), medida de novo para o valor novo nos três consumidores: o
`OuvinteDeVinculoAlterado` do `catalog` não lê `permissoes`; `contracts/events/` não tem esquema do
`VinculoAlteradoV1`, só o envelope, que não declara o enum; e o `PermissaoDoCatalogo.de` devolve
vazio para o que não conhece. O corpo fica como está.
