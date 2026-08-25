# ADR-031 — Nome de evento é único no repositório inteiro

**Status:** Aceita — 25/08/2026
**Fecha a pendência de:** `docs/dominio/catalogo.md` §8 e `docs/dominio/estabelecimento.md` §7 — as duas pedem esta decisão pelo nome
**Relacionada:** ADR-001 (monorepo), ADR-027 (compatibilidade de eventos)
**Precisa existir antes do marco 3** — é quando a verificação de contrato entra no build

## Contexto

Dois eventos diferentes, em serviços diferentes, com o mesmo nome:

| Serviço | Nome | Significa |
|---|---|---|
| `catalog` | `DisponibilidadeAlteradaV1` | acabou a calabresa |
| `merchant` | `DisponibilidadeAlteradaV1` | a loja abriu, fechou, pausou ou retomou |

Os dois documentos que os publicam **já sinalizaram o problema**, cada um com um
⚠, e os dois terminam a observação da mesma forma: *decidir antes do primeiro
consumidor*. Nenhum decidiu. É a decisão que fica esperando porque parece
pequena e porque parece de outro documento.

### Não é mais teórico

`docs/dominio/conversa.md` §14 já escreveu a linha do consumidor **usando o nome
novo**:

```
| ExpedienteAlteradoV1 | merchant | Responder aberto/fechado corretamente |
```

Nenhum produtor publica esse nome. Quem implementar aquele consumidor hoje
assina um tópico que não existe — e isso **não estoura**. Não há erro de
compilação, não há exceção, não há fila morta. Simplesmente nunca chega
mensagem, e a loja responde "estamos abertos" depois de fechar.

Um documento já tomou a decisão sozinho, e tomou a certa. O que falta é o
restante do repositório saber disso.

### Por que agora, e não depois

```
contracts/events/
└── _envelope-v1.json        ← é só isto
```

Nenhum esquema escrito, nenhum consumidor implantado, nenhuma mensagem em
trânsito. **Renomear hoje é editar texto.** Depois do marco 3 é o expand/contract
da ADR-027 §3: consumidor entende os dois nomes, produtor troca, consumidor
larga o antigo — três implantações, esperando fila drenar entre elas, para
consertar uma palavra.

E há um custo que só aparece no marco 3: a verificação de contrato que a ADR-027
exige **não consegue desambiguar dois payloads sob um `eventType`**. Ela
conferiria o esquema errado contra o consumidor certo e passaria.

## Decisão

### 1. Nome de evento é único no repositório inteiro

Não único por serviço. Único **no repositório**, porque o barramento é um só e o
`eventType` é o que o consumidor assina.

É a mesma lógica da ADR-001: o monorepo não resolve a coordenação sozinho, mas
torna a verificação possível — a lista completa de nomes está aqui, e é
varredura, não confiança.

### 2. O evento do `merchant-service` passa a `ExpedienteAlteradoV1`

O do `catalog-service` fica como está.

### 3. O critério de desempate, que vale para a próxima colisão

> **Quando dois serviços querem o mesmo nome, quase sempre um deles nomeou um
> atributo em vez de um fato. É esse que se renomeia.**

Aplicado aqui:

**O `catalog` nomeou o conceito de domínio dele.** O §3 inteiro de
`catalogo.md` se chama "Disponibilidade qualitativa"; são quatro estados
nomeados, há `Opcao.disponivel`, e a premissa P6 do PRD existe por causa disso.
Renomear seria arrancar a palavra do meio do vocabulário que a usa.

**O `merchant` nomeou um atributo solto.** O fato é "abriu, fechou, pausou,
retomou" — e o resto do repositório já chama isso de **expediente**:
`liquidacao.md` apura o *fechamento de expediente*, `catalogo.md` reativa na
*abertura do próximo expediente* e guarda `expedienteDeReferencia`, e o marco 6
do PRD se chama *Fechamento de expediente*.

O nome errado sobreviveu porque `estabelecimento.md` é o único documento que
**não** usa a palavra: a seção que trata do assunto se chama "Horário e pausa".
A colisão não criou o problema; ela expôs um conceito sem nome na própria casa.

### 4. A verificação entra no build junto com a da ADR-027

A ADR-027 já exige, no marco 3, uma verificação que falha com esquema obsoleto
tendo consumidor e avisa com esquema sem consumidor. Acrescenta-se uma linha:

> **Nome de evento declarado por mais de um serviço falha o build.**

É a mais barata das três — é comparar uma lista com ela mesma — e é a única que
pega um defeito que nenhum teste de integração pega, porque cada serviço passa
sozinho.

### 5. Isto não é expand/contract, e é por isso que é hoje

A ADR-027 §3 descreve como se troca um nome com consumidor em produção. **Nada
disso se aplica**: não há esquema, consumidor implantado nem mensagem em
trânsito. É renomear antes de existir, e o custo é uma edição de texto em quatro
documentos.

A decisão de *quando* é tão parte desta ADR quanto a de *qual nome*.

## Consequências

**Positivas**

- O consumidor que `conversa.md` já descreve passa a poder ser escrito. Hoje ele
  compila, roda e não recebe nada.
- Os dois ⚠ saem dos documentos. Aviso que sobrevive a três revisões deixa de
  ser aviso e vira paisagem.
- A verificação do marco 3 ganha uma regra que se checa sozinha, sem julgamento.
- `estabelecimento.md` passa a nomear **expediente**, alinhando-se ao vocabulário
  que `liquidacao.md`, `catalogo.md` e o PRD já usam. O ganho é maior que o
  evento: `expedienteDeReferencia` deixa de aparecer em `catalogo.md` referindo
  um conceito que sua origem não nomeia.

**Negativas**

- **A regra é verificável só dentro do monorepo.** Se um dia um serviço sair
  daqui, a unicidade volta a depender de convenção. Está aceito e é a mesma
  aposta da ADR-001.
- **O critério de desempate exige julgamento.** "Atributo em vez de fato" é
  claro neste caso e pode não ser no próximo. Quando não for, a decisão vira ADR
  em vez de conversa — que é o resultado desejado, não o custo.
- **Uma colisão futura depois do marco 3 custa três implantações**, e a regra
  não impede que ela aconteça: ela faz o build gritar. Impedir de verdade seria
  gerar os nomes a partir do agregado, o que engessa mais do que resolve.

## Alternativas consideradas

- **Renomear o do `catalog`.** Simétrico e igualmente fácil. Rejeitada porque
  arrancaria a palavra central do §3 de `catalogo.md` — o documento perderia o
  nome do próprio conceito para preservar um nome que estava errado do outro
  lado.
- **Prefixar todos os eventos com o serviço** — `MerchantDisponibilidadeAlterada`,
  `CatalogDisponibilidadeAlterada`. Resolve por construção e nunca mais colide.
  Rejeitada: o nome do evento passaria a carregar a topologia, e serviço que se
  funde ou se divide renomearia eventos que não mudaram. Amarra o contrato à
  organização do código, que é justamente o que a ADR-002 evita.
- **Separar por tópico e deixar os nomes iguais.** Funciona no broker. Rejeitada
  porque a ambiguidade voltaria em todo lugar onde o nome aparece sem o tópico:
  log, registro de mensagem morta, `eventType` do envelope, e este próprio
  repositório — onde `DisponibilidadeAlteradaV1` aparece sete vezes e não se sabe
  qual é qual sem ler o parágrafo em volta.
- **Deixar como está e resolver quando doer.** É o que vinha acontecendo. Vai
  doer no marco 3, com esquema escrito e consumidor implantado, e aí custa três
  implantações em vez de uma edição.

## Emendas que esta decisão provoca

**`docs/dominio/estabelecimento.md` §4** — a seção "Horário e pausa" passa a
nomear o **expediente**, que é o conceito que o evento renomeado carrega e que o
`catalog` já referencia por `expedienteDeReferencia`.

**Os dois ⚠** — em `catalogo.md` §8 e `estabelecimento.md` §7 — saem, e cada um
passa a apontar para cá.

## Pendência que esta decisão não fecha

**`TitularSolicitouExclusaoV1`**, nomeado na ADR-013 §7 como o desenho de quando
a exclusão de titular deixar de ser manual, não tem produtor nem consumidor
declarado em documento nenhum. Não é colisão e não bloqueia nada — mas é um nome
já escolhido para um evento que ainda não existe, e quando existir passa por esta
regra como qualquer outro.
