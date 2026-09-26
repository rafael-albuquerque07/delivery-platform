# ADR-046 — Quem observa a abertura do expediente

- **Estado:** aceita
- **Data:** 26/09/2026
- **Fecha:** o buraco entre `catalogo.md` §3 e `estabelecimento.md` §4 — o evento
  de abertura é exigido por um e não tem produtor no outro
- **Emenda:** ADR-025 §5 (o mecanismo, não o resultado), `estabelecimento.md` §4 e §7
- **Relacionadas:** ADR-008, ADR-026, ADR-027, ADR-031, ADR-043

## Contexto

O `catalogo.md` §3 não deixa dúvida sobre o gatilho da reativação:

```
ExpedienteAlteradoV1 (merchant-service)
  motivo = ABERTURA_DE_EXPEDIENTE
      ↓
catalog-service reativa todo produto e opção com:
      estado == ESGOTADO_HOJE
   ∧  expedienteDeReferencia != expediente atual
```

E diz por que não é um job à meia-noite: *"A pizzaria abre às 18h e fecha às 2h;
à meia-noite ela está vendendo, e um job zeraria a calabresa que acabou às 23h."*
**A objeção é ao horário fixo, não a agendamento** — distinção que esta ADR
precisa fazer explícita, porque ela decide justamente a favor de uma varredura.

O documento também exige: *"Só a transição fechado → aberto **por horário**
conta."*

**Transição implica alguém observando.** E ninguém observa. No
`merchant-service`, `Disponibilidade.abertaEm(instante, fuso)` é calculado na
leitura; não existe registro de "estava fechada, agora está aberta". O
`estabelecimento.md` §4 é explícito sobre esse estilo, e com razão:

> *"O registro diz o que foi feito; o cálculo diz o que vale agora — nenhuma
> rotina passa limpando, e nenhum campo fica mentindo."*

Um fato que não é ato não pode virar evento sozinho. O desenho está certo e
**a metade produtora dele nunca foi decidida**.

## Decisão

### 1. Uma varredura periódica, e a chave primária é a idempotência

A cada minuto, o `merchant` percorre os estabelecimentos, pergunta a cada um se
está **dentro do horário**, e para os que estão tenta registrar a abertura do
expediente corrente numa tabela cuja chave primária é
`(estabelecimento_id, expediente)`:

```sql
insert into abertura_de_expediente (estabelecimento_id, expediente, publicado_em)
values (?, ?, ?)
on conflict do nothing
```

**Inseriu uma linha → grava o evento no outbox, na mesma transação. Não inseriu
→ a abertura já foi publicada, e não há nada a fazer.**

Isto não é um cache de "está aberta". É o **registro de um ato**: *publiquei a
abertura do expediente D para a loja X*. A regra do `estabelecimento.md` §4 vale
inteira — nada aqui passa limpando, e nenhum campo fica mentindo sobre o estado
atual, porque nenhum campo fala do estado atual.

**A idempotência é o banco, não o código.** Duas instâncias da varredura rodando
juntas disputam a mesma chave primária, e o PostgreSQL decide: exatamente uma
insere, exatamente um evento sai. Não há leitura-antes-da-escrita para correr, e
não há janela. É a mesma forma do índice único que decide a corrida de cadastro
no `identity` (rodada C-B), e ela tem teste com duas threads pelo mesmo motivo.

### 2. Por que varredura, e não agendador por loja

Um agendador que dispara no instante exato da abertura de cada loja daria
reativação no segundo certo. O custo é o que o torna pior:

- reagendar a cada alteração de horário, de fuso e de pausa;
- reconstruir a agenda inteira a cada reinício;
- e um defeito de agendamento **falha em silêncio numa loja só** — o pior modo
  de falha possível, porque ninguém olha a loja que não reclamou.

A varredura é uma consulta. Não tem estado a reconstruir, não tem o que
reagendar, e um defeito nela falha para todo mundo de uma vez, que é visível.

O preço é latência de até um minuto para reativar produto marcado no expediente
anterior. É irrelevante: o produto está esgotado desde ontem.

### 3. Dentro do horário, não "aberta"

A varredura pergunta **`dentroDoHorario`**, não `estaAberta`.

Parece detalhe e não é. `estaAberta` compõe horário **e** pausa. Se a pizzaria
abre às 18h e o dono pausou às 17h50 por uma hora, às 18h ela está dentro do
horário e pausada — e com `estaAberta` a abertura daquele expediente **nunca
seria publicada**, porque quando a pausa vencesse a loja já estaria aberta sem
nenhuma transição observável. Os produtos ficariam esgotados o dia inteiro.

O `estabelecimento.md` §4 já diz o que resolve isso: *"Pausar e retomar
acontecem **dentro** de um expediente e não abrem outro."* Pausa pressupõe
expediente. Então é o horário que abre o expediente, e a pausa é um estado
dentro dele.

### 4. O payload, que nenhum documento tinha escrito

Nenhum documento do repositório define o payload do `ExpedienteAlteradoV1`. O
`estabelecimento.md` §7 nomeia um campo — `motivo` — e um valor —
`ABERTURA_DE_EXPEDIENTE`. Esta ADR escreve o resto, no envelope comum
(`_envelope-v1.json`), com `eventType` sem versão:

```json
{
  "eventId": "…", "eventType": "ExpedienteAlterado", "eventVersion": 1,
  "occurredAt": "2026-09-26T21:00:04.117293Z",
  "correlationId": "…",
  "payload": {
    "estabelecimentoId": "…",
    "motivo": "ABERTURA_DE_EXPEDIENTE",
    "expedienteDeReferencia": "2026-09-26"
  }
}
```

**O `expedienteDeReferencia` vai no payload, e é o que torna C11 verificável.**
Sem ele, o consumidor teria que perguntar ao `merchant` qual é o expediente
corrente a cada evento — e a idempotência da reativação passaria a depender de
duas chamadas darem a mesma resposta, num intervalo que atravessa a hora de
corte uma vez por dia. Com ele, o consumidor compara dois valores que recebeu.

`occurredAt` é o instante da publicação; `expedienteDeReferencia` é o dia
operacional. **São coisas diferentes e é de propósito**: às 01:30 de domingo o
instante é domingo e o expediente é sábado (ADR-025).

### 5. O enum nasce com um valor só

`MotivoDoExpediente` tem **`ABERTURA_DE_EXPEDIENTE`, e mais nada**.

O `estabelecimento.md` §7 descreve o evento como "abriu, fechou, pausou,
retomou". Os outros três são atos de verdade, com dono óbvio, e nenhum deles é
produzido nesta rodada. Um valor de enum sem emissor é uma promessa com sintaxe
de código — é a mesma razão que tirou `CONVIDADO` do `EstadoDoMembro` na B1 e
`EXPIRADO` do `EstadoDoConvite` na B2.

Acrescentar valor a enum é mudança compatível (ADR-027), e o contrato já obriga
o consumidor a tolerar valor desconhecido. Então o custo de esperar é zero e o
custo de antecipar é uma lista que mente.

### 6. `diaOperacional` nasce aqui, e continua com um consumidor só

A ADR-025 definiu a função e nenhum código a implementou. Ela nasce agora, no
`merchant`, porque é aqui que mora o `fusoHorario` — e continua sendo o **único
lugar** que a calcula. O `catalog` recebe o valor no evento e o recebe de novo
pela porta quando precisa carimbar; **compara, nunca calcula**, que é
exatamente a distinção que manteve esta decisão fechada desde a rodada A2b.

## Consequências

**Positivas**

- O `catalogo.md` §3 passa a ter produtor, e a promessa do PRD — *"volta
  automaticamente a disponível na abertura do expediente seguinte"* — deixa de
  depender de uma peça que não existe.
- A tabela `abertura_de_expediente` é, de graça, o **histórico de expedientes
  abertos por loja** — que é o que o marco 6 vai querer para o fechamento.
- `diaOperacional` sai do papel com teste, antes de o `settlement` precisar
  dela.

**Negativas**

- **A varredura carrega todos os estabelecimentos a cada minuto.** Com o
  `SUBSELECT` da C-A isso é 1+5 consultas independentemente da quantidade de
  lojas, e na escala do MVP é irrelevante. **Gatilho escrito:** o dia em que uma
  passada da varredura levar mais que o intervalo entre passadas. A saída já é
  conhecida — restringir a busca às lojas sem linha para os dois últimos
  expedientes —, e é uma consulta, não um redesenho.
  É também a única exceção à paginação obrigatória do `CLAUDE.md`: o
  `EstabelecimentoRepositorio.todos()` não recebe `Pageable`, e o javadoc dele
  aponta para cá.
- **Até um minuto de atraso** na reativação. Assumido em §2.
- **A varredura publica sem ninguém ter pedido**, e portanto sem token. É o
  primeiro caso de escrita sem pessoa do outro lado neste repositório — e é
  justamente o caso que a ADR-045 registra como fora do alcance dela. Aqui não
  há problema, porque a varredura não *autoriza* nada: ela observa e publica um
  fato do próprio serviço.
- **Loja sem horário nunca abre expediente**, e portanto nunca reativa nada. É o
  comportamento certo — `estabelecimento.md` §4 diz que horário vazio significa
  "nunca abre por horário" — e precisa estar escrito, porque quem operar só por
  aceite manual vai estranhar.

## Emenda à ADR-025 §5: é um evento, não dois

A §5 explica o caso da padaria que abre 6h–14h e 18h–22h, e diz:

> *"são duas transições fechado → aberto, e o catálogo recebe **dois eventos de
> abertura**"*

Com a marca d'água por `(estabelecimento, expediente)`, **o catálogo recebe
um**. A segunda abertura do mesmo dia operacional não insere linha e não publica
evento.

O resultado que a ADR descreve continua exato — o pão que acabou no almoço
continua acabado no jantar —, mas por um caminho mais curto: em vez de o
consumidor receber `D`, comparar com `D` e decidir não reativar, o produtor não
chega a emitir. A idempotência do consumidor **continua obrigatória**, porque a
entrega é pelo menos uma vez (ADR-043 §3) e o mesmo evento pode chegar duas
vezes.

A diferença importa para quem for escrever o consumidor: o exemplo da ADR-025
descreve um tráfego que não vai existir.
