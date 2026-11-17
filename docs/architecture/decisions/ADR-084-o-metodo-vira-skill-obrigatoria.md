# ADR-084 — O método vira skill obrigatória do repositório

- **Status:** aceita
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
