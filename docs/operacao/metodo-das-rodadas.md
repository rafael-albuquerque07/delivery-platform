# Como uma rodada funciona — e onde cada um de nós erra

*Escrito em 18/10/2026.*

## Por que este arquivo existe

**O método das rodadas morava nos pacotes**, e os pacotes iam para `_to_delete/` depois de
aplicados — desde a S-7, cada um fica em `docs/rodadas/`, com o relatório resumido. A regra da G-C1, a de colar antes de trocar, a de usar a data que o `date` imprime, a
de dizer qual trecho se seguiu quando dois se contradizem: cada pacote as reescrevia à mão. Se
quem escreve os pacotes parasse, o repositório não saberia como as rodadas funcionam.

E há uma segunda razão. Em 18/10/2026 o Rafael observou que *o agente não erra*. **Ele erra** — e
o fato de o erro quase nunca aparecer é efeito da posição dele, não da qualidade. Este arquivo
escreve essa assimetria e dá um lugar para o erro de cada papel.

---

## 1 · Os três papéis, e o tipo de erro de cada um

| Papel | Onde está | O que faz | O erro típico | Onde ele é pego |
|---|---|---|---|---|
| **Quem escreve o pacote** | fora do repositório, com relatórios e memória | afirma, prevê, decide, aposta | afirmar o que não mediu | **no repositório de outra pessoa**, depois de aplicado |
| **Quem aplica** | dentro do repositório, com shell | mede, aplica, recusa, relata | comando que mente — aspas, escape, truncagem | **pelo comando seguinte**, no mesmo minuto |
| **Quem passa no navegador** | diante da tela | exerce a coluna e anota a frase | concluir em vez de anotar | **em lugar nenhum** — por isso a regra é anotar ([`passagem-humana.md`](passagem-humana.md) §6) |

**A assimetria é de posição e de custo, não de competência.**

- Quem aplica está **depois** da medição: toda frase dele pode ser testada na hora, e é.
- Quem escreve o pacote está **antes**: toda frase dele é hipótese até alguém rodar o comando.
  **Isso é a função, não o defeito.** Um pacote que só afirmasse o que já foi medido seria uma
  transcrição, e não valeria a rodada. O que faz a medição valer a pena é a previsão que pode
  estar errada.
- O erro de quem aplica morre antes do commit. **O de quem escreve o pacote chega ao
  repositório** — ou a uma pessoa, num roteiro, que vai ao navegador medir algo que não existe.

> **A regra:** cada papel registra os próprios erros, no lugar do próprio papel. Contagem que só
> um lado mantém não mede os dois lados.

---

## 2 · O que um pacote tem de ter

Um pacote é uma pasta na raiz, com um `APLICAR-<letra>-<assunto>.md` e, quando há textos para
merge, um `_emendas-da-rodada-<letra>.para-merge.md`. Ele tem, nesta ordem:

1. **§0 — antes de tudo.** `git log --oneline -1`, `git status --short`, `date` e a conferência
   de que a letra da rodada está livre. Arquivo do repositório modificado antes do §0 é motivo
   para parar, não detalhe.
2. **§1 — o que a rodada é**, e **o que ela não é**, por escrito.
3. **As premissas, com a coluna "medida em".** Toda premissa diz de onde veio — relatório, ADR,
   comando. As que ninguém mediu entram como **não medido**, e viram medição.
4. **§2 — as medições.** Cada uma com **o comando**, com **o que ela decide** e com **as saídas
   possíveis**. Medição cuja saída não muda nada do pacote é cerimônia.
5. **§3 — o corte.** O que para a rodada, se aparecer.
6. **§4 — os testes e as mutações**, quando há código: a previsão escrita **antes** de rodar, e a
   saída colada.
7. **§5 — o relatório, antes dos commits.** As tabelas se montam primeiro, e os corpos dos
   commits se copiam delas.
8. **§6 — os commits**, com o corpo dizendo o quê e o porquê.
9. **A aposta.** Todo pacote termina dizendo onde o autor acha que está o próprio erro.

### As regras que todo pacote repete, e que passam a morar aqui

- **G-C1 — a pergunta no lugar da resposta.** Se um bloco afirma uma decisão que nenhuma ADR
  tomou, ou um fato que o repositório não confirma, quem aplica escreve a pergunta e diz.
- **Colar antes de trocar.** Nenhum texto existente é substituído sem ser colado antes, inteiro.
  Quem aplica preserva o resto e diz o que preservou.
- **A data é a que o `date` imprime.** Nenhum pacote carrega data escrita. *Este arquivo nasceu
  de um rascunho datado de 19/10/2026; o `date` imprimiu 18/10/2026.*
- **Número vem do comando.** Nenhum número do pacote é copiado para o repositório sem ser
  remedido. Para afirmações universais — *todos*, *nenhum*, *N de N* — a regra tem dono: a
  armadilha *"Vai escrever 'todos', 'nenhum', 'N de N' ou 'não existe'"* do
  [`CLAUDE.md`](../../CLAUDE.md).
- **Contradição interna se declara.** Se dois trechos do pacote se contradizem, quem aplica diz
  qual seguiu — não escolhe em silêncio.
- **Parar é na falta de decisão, nunca na presença de um fato.** Quem aplica para quando falta
  uma decisão nossa; quando o que falta é um fato, ele mede.

---

## 3 · Os erros de quem aplica

**Formato:** uma linha por erro, com **o que era**, **como foi pego** e **se chegou ao commit**.
Erro pego pelo comando seguinte é uma linha. Erro que chegou ao commit leva também o que o deixou
passar.

| Data | O erro | Como foi pego | Chegou ao commit? |
|---|---|---|---|
| 16/10 (R-7) | o `\b` do regex de JavaScript não casa letra acentuada, e o filtro da terceira passada deixava passar *"só"* e *"única"* | a contagem veio 31 em vez de 50 | não — o filtro de `ed862ca` já escreve a fronteira por Unicode |
| sem data (leitura anterior à E-4) | um `head -5` truncou a contagem dos enums do `Permissao` no contrato, relatada como *"cinco"* | contagem refeita sem o `head`, ao aplicar | não — o corpo de `23e99a4` (17/10) diz *"seis enums"* |
| 17/10 (leitura da W-H) | o censo da contagem das permissões achou **seis** lugares, e eram **sete** | um `grep` mais largo, ao aplicar a E-5 | não — o corpo de `6a7692f` diz *"sete lugares, e não seis"*, e nenhum commit da W-H carrega o seis |
| 18/10 (terceira passada) | um comando apontou para `build-logic` na raiz, e ele mora em `backend/` | a saída do `grep` trouxe o erro, com código 2 | não |
| 18/10 (terceira passada) | a âncora de uma inserção atravessava uma quebra de linha, e o script não a achou | o script parou antes de gravar | não |
| 18/10 (P-1) | `node -e` comeu as barras invertidas de um comando escrito dentro do razão | leitura do diff logo depois | não — trocado por `Edit` |
| 18/10 (P-1) | corpo do commit com um resto de edição: *"vai de 95… de 96 a 97"* | releitura antes do push | não — `--amend` antes do push |

**Fora desta tabela, de propósito:** o `awk -F` que não separava as células do razão no Git Bash
— as linhas saíram com 22 a 327 campos — estava escrito no pacote da R-7, e é erro de quem
escreve o pacote. Esses erros moram onde quem os escreve os registra.

**Esta tabela não existe para punir ninguém.** Ela existe porque a lista que só um lado mantém
faz o outro lado parecer infalível — e quem parece infalível deixa de ser conferido.

---

## 4 · O ponto cego dos dois, e a única linha que o fura

**Quem aplica confere, sobretudo, o que o pacote manda conferir.** É a maior qualidade do método e
o maior risco dele: o pacote decide onde se olha, e o que nenhum dos dois olha continua sem ser
olhado.

A defesa é uma linha do relatório, e ela é obrigatória:

> **O que a medição decidiu além da pergunta.**

Foi ela que achou, em rodadas diferentes: a permissão que não existia; a consequência que o
próprio mecanismo impedia; o segundo consumidor do enum, a um `log.info` de derrubar o `catalog`;
o sétimo lugar que escrevia a contagem; o gatilho que a rodada pagou sem o pacote prever; e a
permissão nova que não alcança nenhum administrador que já existe — em 18/10/2026, nenhuma linha
de `membro_permissao` tinha `CONFIGURAR_LOJA`.

**A forma de responder:** *"olhei X, que nada no pacote mandava olhar, e achei Y"* — ou *"não olhei
nada além"*, que é uma resposta legítima e informa tanto quanto a outra.

---

## 5 · O que não se faz

- **Não emendar de passagem.** Achou frase falsa fora do assunto da rodada? Cole e diga. O conserto
  é rodada própria, com teto e com comando.
- **Não atribuir coluna a código.** A regra tem dono: a armadilha *"Vai escrever 'o marco N ganha
  X'"* do [`CLAUDE.md`](../../CLAUDE.md).
- **Não escrever número de população em prosa** sem o comando que a enumera ao lado — a mesma
  armadilha do `CLAUDE.md` citada na §2.
- **Aspas só envolvem texto que um comando acha.** O que é nosso vai sem aspas.
- **Não afirmar trabalho futuro** em corpo de commit nem em texto permanente.
- **Uma rodada é back ou front**, nunca os dois. O corte é sobre **comportamento**, e comentário
  não é comportamento.

---

## 6 · O que este arquivo **não** decide

- **Não** diz quem escreve os pacotes, nem com que ferramenta.
- **Não** substitui o [`CLAUDE.md`](../../CLAUDE.md), que é o dono das invariantes e das
  armadilhas.
- **Não** substitui o [`README.md` das decisões](../architecture/decisions/README.md), que é o
  dono das regras sobre ADR, emenda e seção de gatilho.
- **Não** substitui o [`docs/dividas.md`](../dividas.md), que é derivado e tem comandos próprios.
- **Não** é checklist para colar em pacote: o pacote continua escrevendo as próprias guardas, e
  este arquivo é a razão delas, não a cópia.
