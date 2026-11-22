# S-6 — o revisor de diff, e as contas do razão num script do repositório

- **Aplicada em:** 22/11/2026 · **sobre:** `5acb7ed` · **versão do pacote:** v1
- **Commits:** `7083fe1`; esta pasta chega no commit da S-7

## O que se mediu
- §0 e §2 bateram; o `gh` em `C:\Program Files\GitHub CLI\gh.exe`, autenticado
- o `facfb7f` muda o contrato do `merchant` e nenhum arquivo do front
- `razao.mjs` × os dois `grep`: 112 × 112 e 28 × 28; 132 linhas numeradas, sem buraco nem repetida;
  3 linhas do recorte em `frontend/.tsbuild/`, que o git não conhece

## O que mudou
- novos: `.claude/agents/revisor-de-diff.md` e `scripts/razao.mjs`
- `aplicar-rodada.md`: passos 2 (pipeline), 8 (`razao.mjs`), 9 (os seis passos do front) e 11 (o revisor de diff)
- `revisor-de-pacote.md` (quantos arquivos abriu); cabeçalho do razão; emendas às ADRs 072 e 084
- o índice das ADRs 072 e 084 — oitavo arquivo, por decisão do Rafael

## Perguntas ao Rafael
| Pergunta | Resposta |
|---|---|
| O índice das ADRs ficaria para trás (a 084 já sem três emendas) | atualizar as duas linhas, com as quatro emendas da 084 |
| Passo 2 com `--limit 15` não cobre os 13 workflows | o comando com uma linha por workflow |
| Passo 9: o `tipos:conferir` compara a árvore com o índice | dizer que os tipos vão antes para o índice |
| Passo 11: `git diff` não mostra arquivo novo | `git add -N` nos arquivos novos |

## Previsões × resultado
| A previsão | O que saiu |
|---|---|
| o `revisor-de-diff` pode só carregar numa sessão nova | não carregou; a prova sobre o `facfb7f` ficou para a sessão seguinte |
| o `revisor-de-pacote` não diria quantos arquivos abriu | disse ("14") — ele relê o próprio arquivo na sessão |
| o razão não se move | 112 · 28 · 132, antes e depois |

## Defeitos achados
- a frase do passo 8 ficou numa linha só; a do passo 9 deixou "Falha intermitente…" solta;
  o passo 11 diz "antes do commit" depois de "Depois `git push`"
- as emendas da ADR-084 não registram os passos 2 e 9; "os dois comandos acima" no razão, e são quatro
- o item 3 do `revisor-de-diff` cita o `ContratoDeErrosIT` como dono, e o dono é a ADR-053

## O razão
| | antes | depois |
|---|---|---|
| recorte estreito | 112 | 112 |
| segunda passada | 28 | 28 |
| linhas numeradas | 132 | 132 |
