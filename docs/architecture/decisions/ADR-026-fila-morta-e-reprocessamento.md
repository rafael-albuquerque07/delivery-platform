# ADR-026 — Fila morta, retentativa e reprocessamento

**Status:** Aceita — 24/08/2026 · **emendada em 29/09/2026**: consumidor de invalidação
não tem retentativa nem fila morta — ele esquece o cache inteiro
**Fecha a consequência assumida da:** ADR-010 (saga do pedido)
**Relacionada:** ADR-002 (banco por serviço), ADR-023 (fronteira com o PSP)
**Invariante do `CLAUDE.md`:** 7 (quem publica precisa de outbox, quem consome precisa de `processed_messages`)
**Detalhada em:** `docs/operacao/mensagem-na-fila-morta.md`
**Precisa existir antes do marco 3** — é quando o primeiro consumidor de evento é escrito

## Contexto

A ADR-010 registrou isto como consequência negativa e não decidiu:

> Duas pernas retriáveis sem compensação exigem **fila morta e procedimento
> operacional**. "Tentar para sempre" sem um lugar onde a mensagem morre e
> alguém olha é como se perde pedido em silêncio.

Depois do pivô não existe compensação: existe avançar até um estado terminal. Se
a perna que cria a entrega falhar, ou a que lança a liquidação na jornada, a
única saída é tentar de novo. E "tentar de novo" sem limite tem dois modos de
falha, ambos silenciosos: o laço apertado que derruba o serviço, e a mensagem
que nunca vai dar certo ocupando a fila para sempre.

### Três lugares onde uma mensagem se perde, e só um precisa de fila morta

Vale separar, porque o desenho é diferente em cada um:

| Onde | O que acontece | Resposta |
|---|---|---|
| **O outbox não publica** | a linha está gravada, o broker está fora | o despachador tenta de novo. Nada se perde — a linha fica lá. Precisa de **alerta por profundidade**, não de fila morta |
| **O consumidor falha por causa transitória** | banco fora, timeout, indisponibilidade | retentativa curta resolve a maioria |
| **O consumidor falha sempre** | dado inválido, defeito determinístico | **é aqui que a fila morta existe** |

Confundir os três produz o desenho errado: mandar falha transitória direto para
a fila morta esvazia a fila de trabalho num incidente de banco, e alguém tem que
reprocessar duzentas mensagens à mão por causa de trinta segundos de queda.

## Decisão

### 1. Retentativa curta no consumidor, depois fila morta

```
tentativa 1  →  falhou  →  espera  1 s
tentativa 2  →  falhou  →  espera  4 s
tentativa 3  →  falhou  →  espera 16 s
tentativa 4  →  falhou  →  FILA MORTA
```

Backoff exponencial, quatro tentativas, teto de vinte e um segundos. Cobre
reinício de banco, reeleição de réplica e indisponibilidade curta de serviço —
que é o que de fato acontece.

**Nunca retentativa imediata sem espera.** Requeue no mesmo instante é um laço
apertado: a mensagem volta, falha, volta, e o consumidor consome CPU sem
progredir enquanto a causa não passa.

### 2. Uma fila morta por fila de trabalho, não uma por serviço

O nome da fila morta diz o que falhou, e o reprocessamento é sempre por origem.
Uma fila morta única por serviço misturaria eventos de domínios diferentes e
obrigaria a filtrar na hora errada — quando alguém está sob pressão às onze da
noite.

Custa nomes de fila, não infraestrutura.

### 3. A mensagem morre com o motivo junto

O broker acrescenta o histórico de rejeições no cabeçalho, e o envelope já
carrega `eventId`, `eventType`, `eventVersion`, `occurredAt` e
`correlationId`. É o suficiente para responder "o que era, de quando, e por que
não passou" sem abrir o serviço que a rejeitou.

**Nada de log com o corpo da mensagem.** O envelope pode conter dado pessoal, e
o `CLAUDE.md` proíbe. O `correlationId` é o que se registra.

### 4. Mensagem na fila morta nunca cancela nada

Regra herdada da ADR-010, e é a mais importante desta:

> Retentativa esgotada gera **alerta**, nunca cancelamento automático.

Um pedido cujo evento de entrega morreu na fila continua sendo um pedido válido
que precisa de intervenção humana. Cancelá-lo automaticamente transformaria uma
falha de infraestrutura em perda de venda, e o cliente ficaria sem a comida que
a cozinha já produziu.

### 5. O reprocessamento é seguro porque a invariante 7 existe

Reprocessar é mover a mensagem da fila morta de volta para a fila de trabalho.
Isso só é seguro porque **todo consumidor registra o que já processou** em
`processed_messages`: se a mensagem morreu depois de ter tido efeito parcial, o
reprocessamento não duplica.

É a primeira vez que a invariante 7 paga o próprio custo de forma visível. Sem
ela, o botão de reprocessar seria uma roleta.

### 6. O caso que engana: a liquidação que não chega na jornada

A perna 2 da saga merece um parágrafo próprio, porque o modo de falha dela **se
disfarça de outra coisa**.

Se o evento de conclusão do pedido morrer antes de virar lançamento na jornada,
a liquidação está gravada no pedido e o `settlement` não sabe dela. No
fechamento, `dinheiroEsperado` fica **menor** do que o dinheiro que o entregador
de fato tem em mãos — e a diferença aparece como **sobra de caixa**.

Sobra é registrada, então o sistema sinaliza. Mas sinaliza com o rótulo errado:
parece erro de conferência e é mensagem perdida. Por isso o runbook manda, antes
de tratar qualquer sobra como erro de caixa, **conferir a fila morta do
`settlement`**.

### 7. Quem olha

No MVP não há plantão, então a resposta é honesta: **métrica e alerta, não
pessoa de sobreaviso.** Profundidade de cada fila morta exposta ao Prometheus,
alerta quando passar de zero, e o procedimento em
`docs/operacao/mensagem-na-fila-morta.md`.

Alerta em zero e não em um limiar porque, nesta escala, **uma** mensagem morta é
um pedido de um cliente real.

## Consequências

**Positivas**

- A promessa da ADR-010 deixa de ser frase: existe um lugar onde a mensagem
  morre, e um procedimento para quem a encontra lá.
- Falha transitória não vira trabalho manual — as quatro tentativas cobrem o
  caso comum sem envolver ninguém.
- O reprocessamento é seguro por construção, e não por cuidado de quem executa.
- A sobra de caixa deixa de ser diagnóstico ambíguo: o runbook diz onde olhar
  primeiro.

**Negativas**

- **Uma fila morta por fila de trabalho multiplica objetos no broker.** Com oito
  serviços e vários eventos, são dezenas de filas. Aceito: são declarativas,
  nascem com a configuração do serviço, e o nome é o que torna o incidente
  legível.
- **Quatro tentativas em vinte e um segundos não cobrem queda longa.** Um banco
  fora por dez minutos manda tudo para a fila morta, e alguém reprocessa em
  lote. A resposta certa é o consumidor **parar** em vez de esvaziar a fila
  quando muitas mensagens falham seguidas — não está desenhado, e está nomeado
  abaixo como pendência.
- **Alerta em zero vai disparar por defeito de desenvolvimento**, não só por
  incidente. Aceito enquanto não houver produção: o barulho é preferível ao
  silêncio, e o custo é uma notificação.
- **O procedimento é manual.** Mover mensagem de fila é operação de console, e
  ninguém automatizou. Vira botão no painel quando doer.

## Alternativas consideradas

- **Retentativa infinita, sem fila morta.** É o que acontece por omissão em
  quase toda configuração. Rejeitada: a mensagem que nunca vai dar certo bloqueia
  a fila ou gira para sempre, e ninguém descobre — que é exatamente o "perde
  pedido em silêncio" que a ADR-010 nomeou.
- **Fila de espera no broker, com tempo de vida e retorno automático**, em vez de
  retentativa dentro do consumidor. É o desenho mais robusto: sobrevive a
  reinício do processo e a espera é observável. Rejeitada para o MVP por custo de
  configuração — três filas por evento em vez de duas — e porque, com uma
  instância por serviço, a retentativa em processo não perde nada relevante.
  Volta se o número de instâncias crescer.
- **Uma fila morta única para todo o sistema.** Menos objetos, um lugar só para
  olhar. Rejeitada: mistura domínios, e o reprocessamento passa a exigir filtro
  no momento em que ninguém quer filtrar.
- **Cancelar o pedido depois de N tentativas.** Tentadora, porque "resolve" o
  estado pendente. Rejeitada com veemência: transforma falha de infraestrutura em
  perda de venda, e faz o sistema decidir sozinho algo que só o comerciante pode
  decidir.
- **Alerta com limiar em vez de em zero.** Reduziria ruído. Rejeitada nesta
  escala: com dezenas de pedidos por dia, uma mensagem morta é um cliente.

## Pendência que esta decisão cria

**Parada do consumidor por falha em série.** Quando muitas mensagens seguidas
falham, o certo é o consumidor parar de consumir — deixando as mensagens na fila
de trabalho — em vez de despejá-las na fila morta. Não está desenhado. Sem isso,
uma queda de banco de dez minutos vira reprocessamento manual em lote.

Requisito do marco 3, junto do primeiro consumidor real.

## Emenda que esta decisão provoca

O documento de arquitetura v2 (`docs/referencia/`), §18.2, lista **fila morta e
procedimento operacional** como nomeado e não construído. Passa a estar decidido
aqui e detalhado em `docs/operacao/mensagem-na-fila-morta.md`. O que permanece
sem desenho é a parada do consumidor por falha em série — ver a pendência acima.

## Emenda de 29/09/2026 — o primeiro consumidor não segue esta política, e por quê

Esta ADR previa-se necessária *"antes do marco 3 — é quando o primeiro consumidor
de evento é escrito"*. Ele chegou antes, na G-B4, e **não usa nada do que está
aqui**.

O consumidor do `VinculoAlteradoV1` invalida um cache em memória. A política
desta ADR não serve a ele por três motivos:

1. a fila dele é **exclusiva e temporária** (ADR-011) — uma fila morta ligada a
   ela morre junto, e reprocessar para uma instância que já não existe não é
   reprocessar;
2. reter uma mensagem por 21 segundos é reter uma **revogação de acesso** por 21
   segundos;
3. há resposta melhor do que repetir: **esvaziar o cache inteiro**. Se não se
   consegue remover uma entrada, remover todas custa algumas consultas e não
   deixa ninguém com acesso que já foi retirado.

**Falhar para o lado seguro, num cache de autorização, é esquecer.** A mensagem
é sempre confirmada; devolvê-la repetiria a mesma falha em laço enquanto o cache
já está vazio e correto.

A política desta ADR continua valendo, inteira, para **todo consumidor que
escreva em banco** — que são todos os outros previstos. Ver a ADR-048 §3.

### E um número desta ADR nunca foi confrontado com a configuração

O `application.yml` de sete serviços declara `initial-interval: 2s` e
`max-attempts: 5`. Esta ADR decide 1 s, 4 s, 16 s e quatro tentativas. **Os dois
descrevem retentativa de consumo**, e nenhum dos dois nunca rodou — não havia
consumidor.

Há ainda uma armadilha no meio: o `max-interval` padrão do Spring Boot é **10 s**,
então mesmo implementando esta ADR a espera de 16 s viraria 10 sem ninguém
declarar nada.

**Gatilho escrito:** o primeiro consumidor que escreva em banco. É ele que
precisa desta política de verdade, e é aí que a divergência se resolve — não
antes, porque resolvê-la agora seria escolher entre dois números que nenhum
código usa.

## Emenda de 10/10/2026 — a recusa definitiva não tem quatro tentativas (G-C3b)

Do primeiro consumidor a usar esta política: o `OuvinteDeExpedienteAlterado`, no
`catalog`.

As quatro tentativas são para **falha transitória** — broker instável, Mongo ocupado,
conflito de escrita. **Corpo que não corresponde ao contrato vai para a fila morta na
primeira entrega**, sem espera nenhuma: quatro leituras do mesmo JSON inválido são quatro
leituras do mesmo JSON inválido, e as três esperas só atrasam a hora em que alguém olha.
No código isso são duas exceções excluídas da política — `EventoIlegivel` (o corpo) e
`DocumentoIlegivel` (o documento gravado que não reconstitui) —, e o
`ConsumoComFalhaIT` as mede contando entregas: **uma** para as duas, **quatro** para a
transitória.

**Onde a política mora.** Numa segunda fábrica de contêineres, só deste consumidor, com
os números em `delivery.consumo-de-eventos.*` — e não no
`spring.rabbitmq.listener.simple.retry`, que vale para a fábrica inteira, inclusive para
o ouvinte de invalidação, que não quer retentativa (ADR-048 §3). Medido no jar do Boot
4.1.1: o `max-interval` daquele bloco tem **10 s** de padrão, que cortaria os 16 s desta
ADR; e a propriedade de contagem é `max-retries` — o `max-attempts` que o YAML declara
**não existe** e nunca foi lido.

**E a retentativa é a do Spring Framework 7** (`org.springframework.core.retry`), que o
Spring AMQP 4.1.1 recebe; o `spring-retry` não está no classpath. Nela, `maxRetries`
conta **repetições**: as quatro tentativas desta ADR são `maxRetries = 3`. Há um teste que
lê os padrões (`ConsumoDeEventosPropertiesTest`), porque o teste de integração os
sobrescreve para milissegundos.
