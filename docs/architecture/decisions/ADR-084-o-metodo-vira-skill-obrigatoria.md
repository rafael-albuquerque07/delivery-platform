# ADR-084 — O método vira skill obrigatória do repositório

- **Status:** aceita · **emendada em 17/11/2026**: o critério aponta para o teste (S-3) · **emendada em 18/11/2026**: o revisor de pacote (S-4) · **emendada em 18/11/2026**: o subagente é do repositório (S-5) · **emendada em 22/11/2026**: o revisor de diff (S-6) · **emendada em 22/11/2026**: o pacote aplicado fica no repositório (S-7) · **emendada em 23/11/2026**: a busca no Git Bash e fora das rodadas, e a versão no §0 (S-8) · **emendada em 23/11/2026**: a guarda do Bash (S-9)
- **Data:** 17/11/2026
- **Rodada:** S-1
- **Relacionada:** ADR-072 (a seção que o comando colhe, e a segunda passada do `docs/dividas.md`),
  ADR-083 (a ordem de construção que a skill usa para fechar marco), o `CLAUDE.md` e a skill
  `novo-caso-de-uso`

## Contexto

**O método deste projeto estava em três lugares:** o `CLAUDE.md`, a repetição em cada pacote, e
uma página de status fora do repositório, que o agente não lê.

**A R-13 mostrou o custo:** três pacotes seguidos escreveram controles contra medições que nunca
foram registradas aqui. **A P-2 e a P-3 mostraram outro:** uma regra de anotação escrita para um
caso só, e corrigida duas vezes.

**Já existia uma skill no repositório**, a `novo-caso-de-uso`, que ensina como se escreve código
nas camadas hexagonais. Nenhuma ensinava como se trabalha: aplicar uma rodada, escrevê-la,
registrar uma passagem humana, fechar um marco.

## Decisão

> **O método mora em `.claude/skills/metodo-delivery-platform/`, versionado, e o `CLAUDE.md`
> manda carregá-lo.**

A skill **aponta** para as regras que já têm dono — o PRD, as ADRs, os documentos de domínio e o
`CLAUDE.md` — e **é dona** das regras de método que não tinham dono no repositório.

A `novo-caso-de-uso` continua dona de **como se escreve código**; esta, de **como se trabalha**.
Numa rodada de código, as duas valem juntas.

**Mudar uma skill é mudar documento:** entra por rodada.

**De `.claude/`, só `skills/` é do repositório.** O `.claude/settings.local.json` é da máquina, e o
`.gitignore` já o cobre (as linhas `.claude/settings.local.json` e `.claude/*.local.json`).

## Consequências

**Quem aplica confere o pacote contra a skill antes de aplicar.**

**Uma skill só carrega quando a descrição bate com a tarefa**, e por isso o `CLAUDE.md` a torna
obrigatória.

**Há um documento a mais para manter alinhado com o `CLAUDE.md`.**

## Alternativas consideradas

**Fundir o método na `novo-caso-de-uso`.** **Recusada:** uma skill carrega pela descrição, e uma
descrição que cobrisse código e método carregaria para tudo.

**Deixar o método no `CLAUDE.md` e nos pacotes.** **Recusada:** é a situação do contexto, com as
regras repetidas em cada pacote e uma parte fora do repositório.

## Gatilho escrito

**Nenhum.**

## Emenda de 17/11/2026, S-3 — o critério aponta para o teste

O fechamento de marco passa a ter quatro colunas — rota, tela, **teste** e exercida —, e a tabela
de cada marco mora em `docs/marcos/marco-N.md`. Os dois primeiros arquivos entraram nesta rodada,
com o censo dos testes dos marcos 1 e 2. O corpo fica como está.

## Emenda de 18/11/2026, S-4 — o revisor de pacote

A conferência do passo 3 do `aplicar-rodada.md` passa a ser feita por um subagente próprio,
`.claude/agents/revisor-de-pacote.md`, que só lê. As regras continuam no `escrever-rodada.md`; o
subagente diz como conferir cada uma. O corpo fica como está.

## Emenda de 18/11/2026, S-5 — o subagente é do repositório

A frase do corpo *"De `.claude/`, só `skills/` é do repositório"* passa a valer com `agents/`
também. O `revisor-de-pacote` entrou no git na S-4 e é configuração do projeto, como as skills:
quem clona o repositório ganha a conferência do passo 3. Decisão do Rafael. O `SKILL.md` e o
`.gitignore` passam a dizer o mesmo. O `escrever-rodada.md` ganha a regra 15 — a decisão que
muda o que vale procura as frases que diziam o contrário — e a 16, a mutação desfeita pela cópia;
a regra 11 passa a cobrir o dono de arquivo. O corpo fica como está.

## Emenda de 22/11/2026, S-6 — o revisor de diff

O `revisor-de-pacote` lê o plano antes do primeiro comando; o **`revisor-de-diff`**, em
`.claude/agents/`, lê o que foi feito, antes do commit de toda rodada que muda código (o passo 11
do `aplicar-rodada.md`). Só lê, como o primeiro. Nasceu de um defeito que nenhum dos dois
revisores pegaria: a M3-1 mudou o contrato do `merchant` e não regravou os tipos do front. E o
`revisor-de-pacote` passa a dizer quantos arquivos abriu, para o custo de cada revisão ser medido.
O corpo fica como está.

## Emenda de 22/11/2026, S-7 — o pacote aplicado fica no repositório

Até a S-6, o pacote aplicado ia para `_to_delete/`, que o git ignora: o porquê de cada rodada
ficava nos commits, nas ADRs e no razão, e o pacote — as previsões, as medições, as perguntas —
sumia. **Decisão do Rafael:** cada pacote aplicado fica em `docs/rodadas/<RODADA>/`, no commit da
rodada, com um `RELATORIO.md` resumido (o passo 12 do `aplicar-rodada.md`; a forma no
`docs/rodadas/README.md`). Nenhum framework de SDD entrou: a ideia veio do registro de mudanças
do OpenSpec, sem a ferramenta, porque ela duplicaria o PRD e as ADRs. O corpo fica como está.

## Emenda de 23/11/2026, S-8 — a busca no Git Bash e fora das rodadas, e a versão do pacote no §0

Duas regras novas no `escrever-rodada.md`, cada uma com o item do `revisor-de-pacote` que a
confere. **A 17:** no Git Bash do Rafael, `grep -i` com mais de um `-e` aborta (saída 134) —
duas vezes na S-7 —, e as alternativas passam a ir num padrão só, com `\|`; e toda busca em
`docs` leva `--exclude-dir=rodadas`, porque os pacotes guardados citam as frases antigas. **A 18:** pacote
reenviado muda de nome, e o §0 confere a versão — na S-7, a versão reenviada tinha o nome da
primeira, e a primeira foi aplicada, sem a linha do `ls -l` no `aplicar-rodada.md`. Essa linha
entra agora, com a do `grep -i`, no "Nunca no relatório", que é o que quem aplica lê. O corpo
fica como está.

## Emenda de 23/11/2026, S-9 — a guarda do Bash

Na S-8, com a linha nova já no `aplicar-rodada.md`, o primeiro comando de quem aplicou foi outra
vez um `ls -la` — antes de ler o pacote e a skill. Foi a terceira rodada seguida. **Texto lido
depois do primeiro comando não segura o primeiro comando.** Desde esta emenda, um gancho do Claude
Code — `.claude/settings.json`, `PreToolUse` sobre o Bash — chama `.claude/hooks/guarda-do-bash.mjs`
antes de cada comando, e ele recusa o que imprime o dono do arquivo (regra 11) e o `grep -i` com
mais de um `-e` (regra 17), com a forma certa na mensagem. O que está entre aspas é texto e passa.
Se a entrada não for a esperada, a guarda deixa passar: fechar travaria a sessão por um defeito
dela. `node .claude/hooks/guarda-do-bash.mjs --prova` roda os casos.

E a regra 17 fica mais estreita: a busca de frase antiga pula `docs/rodadas/`, e as conferências
de caminho de máquina e de token, que o passo 12 do `aplicar-rodada.md` passa a mandar rodar em
toda rodada, olham justamente para lá. Em célula de tabela, um padrão só sem `|` também serve.

A frase do corpo *"De `.claude/`, só `skills/` é do repositório"*, que a emenda da S-5 estendeu a
`agents/`, passa a valer com `hooks/` e o `settings.json` também — o `settings.json` só com ganchos,
nenhuma permissão e nenhum segredo. Decisão do Rafael. O `SKILL.md` e o comentário do `.gitignore`
dizem o mesmo. O corpo fica como está.
