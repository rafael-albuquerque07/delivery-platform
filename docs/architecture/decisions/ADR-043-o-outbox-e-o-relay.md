# ADR-043 — O outbox e o relay

- **Estado:** aceita
- **Data:** 2026-09-24
- **Emenda:** esclarece a ADR-011 (onde mora o cache de autorização)
- **Relacionadas:** ADR-011, ADR-012, ADR-021, ADR-026, ADR-027, ADR-035, ADR-041
- **Invariante que ela cumpre:** invariante 7 — *nenhum evento é publicado fora do outbox*

## Contexto

O `contracts/eventos.md` declara seis eventos do `merchant-service` desde o
primeiro dia do repositório. Nenhum existe. A invariante 7 exige que todo evento
nasça no outbox, e o outbox também não existe — então a invariante está sendo
cumprida por vacuidade: é fácil não publicar fora do outbox quando não se publica
nada.

Um desses seis eventos tem consequência de segurança e não só de integração. O
`VinculoAlteradoV1` é o que faz revogação de acesso valer em segundos: quando a
Marli suspende a Bia, nenhum outro serviço fica sabendo, e qualquer resposta que
eles tenham guardado continua valendo até expirar sozinha. É por ele que o outbox
nasce.

Ao escrever esta rodada apareceu um fato que o repositório não deixava ver de
fora: **as escritas da equipe não têm dono na camada de aplicação**. As sete
operações administrativas da `Equipe` — `promover`, `rebaixar`, `suspender`,
`reativar`, `remover`, `sair` e `alterarPermissoes` — e o aceite do convite
existem no domínio, e quem as orquestra — carrega os membros, aplica, grava — são
os testes. Não havia nenhum lugar onde a gravação do `Membro` e a gravação do
evento pudessem acontecer na mesma transação, porque não havia nenhum lugar onde
a gravação do `Membro` acontecesse.

## Decisão

### 1. O evento é escrito na mesma transação do fato

`GerenciarEquipeService` nasce como o dono das sete operações, e o
`AceitarConviteService` como o do aceite. Cada escrita é `@Transactional` e faz,
na ordem: trava a linha do estabelecimento e carrega a `Equipe` — as duas coisas
numa chamada só, `MembroRepositorio.equipeParaAlteracao`, que a B1 desenhou para
isso —, aplica a operação de domínio, grava o `Membro`, grava a linha do outbox.
O cadeado vale para todas, e não só para as que contam administradores: é barato,
porque é por estabelecimento e escrita de equipe é rara.

Uma transação, dois `INSERT`s. Ou os dois valem ou nenhum vale. É isso, e só
isso, que a invariante 7 pede: **não existe instante em que o vínculo mudou e o
evento não vai sair**.

### 2. O relay é polling com `FOR UPDATE SKIP LOCKED`

Um `@Scheduled` dentro do próprio `merchant-service` lê um lote de linhas com
`publicado_em is null`, publica no RabbitMQ e marca `publicado_em`. A consulta é
SQL nativa:

```sql
select * from outbox
 where publicado_em is null
 order by ocorrido_em
 limit :lote
   for update skip locked
```

`SKIP LOCKED` é a cláusula inteira da decisão. Sem ela, duas instâncias do
serviço disputam as mesmas linhas e a segunda espera a primeira — o relay deixa
de escalar no exato momento em que há tráfego para escalar. Com ela, cada
instância pega um lote diferente e nenhuma bloqueia a outra.

É a segunda vez que o `FOR UPDATE` aparece no repositório como consulta nativa. A
primeira foi o cadeado da B1 sobre a linha do estabelecimento, e o motivo lá era
que o PostgreSQL recusa `FOR UPDATE` sobre o lado anulável de um `LEFT JOIN`. Aqui
o motivo é outro — `SKIP LOCKED` não tem equivalente em JPQL — e a conclusão é a
mesma: trava de linha se escreve em SQL.

**Descartadas:**

- *Publicar depois do commit, com `TransactionSynchronization`.* Menor latência, e
  abre a janela que o outbox existe para fechar: commit feito, processo morre,
  evento nunca sai.
- *CDC lendo o WAL (Debezium).* Resolve de verdade e traz um componente de
  infraestrutura inteiro para um repositório que acabou de tirar a
  observabilidade de dentro de si pela ADR-041. A porta fica aberta: quem lê o
  WAL lê a mesma tabela `outbox`, e nenhuma linha de domínio muda.

### 3. Entrega pelo menos uma vez, e está escrito

Publicar no broker e marcar `publicado_em` não são atômicos entre si. Se a
publicação vai e o commit não, a mensagem sai duas vezes. Não há conserto barato
para isso, e fingir que há é pior do que assumir:

- toda mensagem carrega `eventId`, que é a chave primária da linha do outbox;
- **o consumidor é obrigado a ser idempotente por `eventId`**, e isso está no
  `contracts/eventos.md` como requisito do contrato, não como recomendação.

### 4. Ordem não é garantida, e por isso o payload é estado

Com `limit` e `SKIP LOCKED`, duas instâncias podem publicar fora de ordem. Um
evento que carregasse *delta* ("perdeu a permissão X") aplicado fora de ordem
corrompe o consumidor em silêncio.

Então o `VinculoAlteradoV1` carrega **o estado completo do vínculo depois da
mudança** — papel, estado e a lista inteira de permissões. Duas consequências:

- aplicar duas vezes o mesmo evento dá o mesmo resultado (§3 fica barata);
- o consumidor descarta qualquer evento cujo `occurredAt` seja anterior ao do
  último que ele aplicou para aquele par `(estabelecimentoId, usuarioId)`, e com
  isso a ordem deixa de importar.

O `occurredAt` do envelope existe por causa desta cláusula. Não é carimbo
informativo.

### 5. O que o evento **não** carrega

Nome e telefone não entram. São dado do `identity-service`, a ADR-001 proíbe o
`merchant` de importá-lo, e um evento é o jeito mais silencioso de furar essa
regra: ninguém revisa o payload de uma fila com a atenção com que revisa um
`import`. A mesma frase da CLAUDE.md sobre log vale para a tabela `outbox`, que é
uma cópia durável do evento: **o que não pode ir para o log não pode ir para o
outbox**.

### 6. RabbitMQ deixa de ser configuração e vira dependência

Até a C-A o `merchant` não usava o broker, mas o `spring-boot-starter-amqp` já
chegava pelo `delivery.messaging-conventions`, e o indicador de saúde que o
starter registra fazia o `/actuator/health` responder `503` no teste. A saída foi
desligar o indicador — correta enquanto nada dependesse do broker, e inválida a
partir desta ADR.

O indicador volta. E volta com a consequência inteira: **um `merchant` sem
RabbitMQ passa a responder fora de serviço**, que é a resposta certa, porque um
serviço que aceita escrita e não consegue publicar o evento da escrita está
acumulando dívida silenciosa em `publicado_em is null`.

### 7. Na fila, o envelope que o repositório já tinha

O primeiro evento publicado não inventa formato. Ele segue
`contracts/events/_envelope-v1.json`, que as ADR-026, ADR-027 e ADR-035 já
pressupunham: `eventId`, `eventType`, `eventVersion`, `occurredAt`,
`correlationId` e `payload`, campos de envelope em inglês e payload em português.
O `eventType` é `VinculoAlterado`, **sem** o `V1` — a versão vive em
`eventVersion` (`contracts/README.md`); `VinculoAlteradoV1` é a forma abreviada
do par, como nos documentos de domínio.

A linha do outbox guarda o envelope inteiro, e o relay publica o que está nela:
nenhuma montagem acontece na hora de publicar, e o que foi gravado é exatamente
o que sai.

O `correlationId`, enquanto o serviço não tem correlação de requisição, é o
próprio `eventId`: o evento é a raiz da cadeia, e quem reagir a ele propaga o
valor adiante.

## Consequências

**Positivas**

- A invariante 7 sai da vacuidade e passa a ter um mecanismo.
- As escritas da equipe ganham dono. O que os testes faziam à mão agora é
  código de produção, e o teste volta a testar em vez de orquestrar.
- Revogação de acesso passa a ter caminho de saída, que é o pré-requisito escrito
  da ADR-011.

**Negativas**

- Latência de publicação igual ao intervalo do polling. Aceitável porque o prazo
  da ADR-011 é de dezenas de segundos, não de milissegundos.
- A tabela `outbox` cresce para sempre. O índice não — é parcial sobre
  `publicado_em is null` —, mas a tabela sim. **Vira dívida com prazo no dia em
  que o volume justificar expurgo**, e o expurgo é um `delete` por data, não um
  redesenho.
- Um `merchant` sem broker agora se declara fora de serviço e é reiniciado pelo
  orquestrador. É o comportamento pedido e é também uma forma nova de o serviço
  cair.

## Emenda à ADR-011: onde mora o cache

A ADR-011 diz "cache Caffeine em processo, 60s/10s, invalidado pelo
`VinculoAlteradoV1`, falha fechada". Ao implementar, ficou claro que eu vinha
lendo "em processo" como "dentro do `merchant`". Está errado.

O `merchant` é a **fonte da verdade** do vínculo: ele lê a própria tabela, numa
consulta que ele mesmo controla, dentro da transação que acabou de escrever. Um
cache ali serviria para o serviço não perguntar a si mesmo.

O cache da ADR-011 mora **em cada serviço que pergunta** — `order`, `catalog`,
`settlement` — e é alimentado pelo `VinculoAlteradoV1`. É por isso que o evento
vem antes do cache, e não junto: **o cache sem o evento é um cache que não
invalida**, e um cache de autorização que não invalida é uma falha de segurança
com nome de otimização.

Hoje não existe segundo serviço com rota protegida. O cache, portanto, não tem
casa, e construí-lo agora seria construir contra um consumidor de mentira.

**Gatilho escrito:** o primeiro serviço que precisar autorizar uma requisição
própria contra um vínculo do `merchant`.

## A pendência que esta ADR cria

O `AutorizacaoComercialPort` síncrono — o caminho que o consumidor usa quando o
cache está frio — **não pode ser escrito ainda, e o motivo não é prioridade**.

Uma chamada de serviço para serviço precisa de credencial, e a credencial entre
serviços nunca foi decidida. A ADR-037 decidiu o token de *pessoa*; a ADR-015,
emendada, o reduziu a seis claims — `iss`, `sub`, `aud`, `iat`, `exp`, `jti`. Não
há `scope`, não há `client_id`, não há nada que distinga um token de serviço de
um token de gente a não ser o que estiver no `sub`. Escrever o port hoje
significaria escolher essa credencial de passagem, dentro de uma rodada de
eventos, e essa é exatamente a forma de decidir uma coisa importante sem
perceber que se está decidindo.

**Gatilho escrito:** o mesmo da emenda acima — o primeiro serviço com rota
protegida. Ele chega precisando das duas coisas ao mesmo tempo, e aí a credencial
é o assunto da rodada em vez de um detalhe dela.
