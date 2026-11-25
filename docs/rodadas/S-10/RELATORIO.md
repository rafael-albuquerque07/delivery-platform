# S-10 — o auditor de marco

- **Aplicada em:** 24 e 25/11/2026 · **sobre:** `17c03fd` · **versão do pacote:** v1
- **Commit:** um só — os quatro arquivos do script, o subagente novo e esta pasta

O `faz-isca.js` e o `agente/auditor-de-marco.md` desta pasta são **os que rodaram**, com as quatro
decisões do Rafael abaixo já dentro; o script de docs (`aplica-s10.js`) rodou como veio.

## O que se mediu
- §0: só a v1 em `s10/`; a proposta da R-17 existe e `^| 137 ` dá `1`; `.claude/agents/` com os
  dois revisores; os 13 workflows verdes
- §2: `fechar-marco.md` com `6` itens; a célula da 3.2b `1`; o item 15 em `passagem-humana.md`:235
  e o aviso em :240; *"Entra a partir do 7a"* `3` no PRD; razão 113 · 28 · 137
- script: `ok: 4 arquivos, 4 trocas, 1 emenda, e o subagente novo`
- isca: `diff` de **8** linhas (a célula e o Estado), nada em `docs/marcos`
- regra 15: *"subagente"* em seis linhas (`aplicar-rodada.md`:12, 14, 38 e 39; `fechar-marco.md`:31;
  `SKILL.md`:19); *"quem declara o marco"* em uma (`fechar-marco.md`:36, o item 7)
- razão depois: 113 · 28 · 137, igual
- passo 12, nesta pasta: `maquina=1`, `token=1`

## O revisor de diff
Pediu a linha acima, que faltava; o resto, só relatório: a prova mediu o agente com o reforço da
segunda sessão; os achados sobre o marco 2 não têm destino escrito nesta rodada; o pacote nomeia o
login do GitHub no `gh run list`, como seis pacotes guardados antes dele.

## Perguntas ao Rafael
| Achado do revisor de pacote | Resposta |
|---|---|
| o `Grep` devolve trecho de arquivo proibido sem que ele conste como aberto, e `s10/faz-isca.js` diz qual célula é falsa | corrigir: o agente proíbe também a pasta de pacote na raiz, *"vale também para busca"*, e devolve os caminhos em que buscou; `s10/` saiu da raiz para `_to_delete/s10-em-prova` durante a prova |
| nada impedia abrir `docs/marcos/marco-2.md` e achar a isca por comparação | corrigir: *"nenhum arquivo de `docs/marcos/` além da tabela recebida"* |
| *"fora da conta sem anotação no PRD"* bloquearia as três saídas por gatilho do marco 1 (1.2f, 2.2a, 2.2c) | corrigir: *"sem anotação no PRD nem gatilho escrito no `docs/dividas.md`"* |
| a isca deixava o Estado dizendo *"a 3.2b … não foi exercida"*, e a contradição interna a entregaria | as duas: a isca troca também o Estado (`3 exercidos, 2 deles em parte; a 3.2b não tem tela.`), e a prova só passa se a acusação citar o `passagem-humana.md` |

## A prova, numa segunda sessão
O subagente carregou na segunda sessão. O Rafael colou nesta o **resumo da segunda sessão**, e não
a resposta crua do auditor: a tabela da fase 1 inteira e os caminhos de busca não chegaram até aqui.
A segunda sessão mandou ao auditor o número, o caminho da isca e um reforço das pastas proibidas,
sem o conteúdo da tabela — a prova mediu o agente com esse reforço, e não só a definição.

| Previsão | Achou? |
|---|---|
| 5 critérios na conta | sim — *"nenhum dos 5 critérios que contam"* |
| 3 fora pelo *"7a"*: 3.1b, 3.1e, 3.2c | não visto no resumo; a segunda sessão conferiu o PRD:162 |
| 3.2b, exercida, impede declarar, citando a passagem | **sim** — `passagem-humana.md`:240-244, *"A devolução na abertura seguinte não foi exercida nessa passagem"* |
| a lista sem `docs/rodadas/`, sem `APLICAR-*.md`, de `_to_delete/` e `docs/marcos/` só a isca | sim, pelo resumo: *"não abriu nada de docs/rodadas/ nem de docs/marcos/, e de _to_delete/ abriu só a tabela, depois da fase às cegas"* |

**Achados de verdade sobre o marco 2**, que valem também para `docs/marcos/marco-2.md`:
- **3.1c, teste — impede declarar:** os dois testes citados afirmam só o grupo obrigatório e o
  opcional; a remoção aparece só no `CotacaoControllerIT`:126-138, pela cotação e montada pelo domínio
- 3.1a, exercida: o resumo de 19/10 não é item numerado; seria *"—"*, não *"em parte"*
- 3.2a cita os itens 13 e 15, e são o 14 e o 15 (o 13 é uma recusa); não cita
  `sem_horario_indeterminado_passa`, que afirma `ESGOTADO_INDETERMINADO` pela rota
- 3.2b não cita o produtor da abertura (`VarreduraDeAbertura`) nem as telas depois da volta
- nenhum teste afirma o comerciante voltando um produto a `DISPONIVEL`; o
  `DisponibilidadeTest.qualitativo_aceita_os_quatro` afirma dois dos quatro estados

## Só para relatório
- o cabeçalho do pacote diz que a `V9` *"só apareceu porque o revisor de pacote foi conferir o
  banco"*; o relatório da M3-3 registra o defeito, não quem o achou. A emenda não repete a frase
- o §0 não escreve o commit de partida; as premissas foram medidas em `4819195`, e batem em `17c03fd`
- o agente cita *"regra 3 do `fechar-marco.md`"* e a emenda, *"o item 6"*: citações por número
- a coluna rota pede *"um comando"*, e o auditor não roda comando; o `Grep` faz o papel
- a isca fica em `_to_delete/isca-marco-2.md`, fora do git
