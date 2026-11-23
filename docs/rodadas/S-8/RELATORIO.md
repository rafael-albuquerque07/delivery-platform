# S-8 — a busca roda no Git Bash e fora das rodadas, e o §0 confere a versão do pacote

- **Aplicada em:** 23/11/2026 · **sobre:** `d69d2c4` · **versão do pacote:** v2
- **Commits:** o commit desta rodada traz esta pasta

A v1 não chegou a esta máquina: a v2 é a v1 com a troca da `premissas-do-front.md`:267, decidida
pelo Rafael antes da aplicação. Na pasta `s8/` havia só o `APLICAR-` da v2, como a regra 18 pede.

## O que se mediu
- §0: `d69d2c4`; os controles da S-7 acharam; a regra 17 ainda não existia (`0`); os 13 workflows verdes
- §2: regras de 10 a 16 = 7; itens de 10 a 20 = 11; a busca de `grep -r` em `docs` sem o corte, três
  linhas, só o `contracts/README.md`:44 percorrendo `docs` inteira; a forma que aborta em cinco linhas
  (ADR-088:22 e as linhas 16, 118, 120 e 121 do razão); nenhuma contagem por extenso; a busca da
  operação em três linhas; a :267 sem forma do razão (`0`)
- o script: 9 arquivos, 12 trocas, 1 emenda; `git diff --stat` com 45 inserções e 10 remoções

## O que mudou
- `aplicar-rodada.md`: o "Nunca no relatório" ganha o `ls -l`; parágrafo novo sobre `grep -i` e o corte
- `escrever-rodada.md`: regras 17 e 18; `revisor-de-pacote.md`: itens 21 e 22
- `contracts/README.md`: a varredura de nomes de evento com `--exclude-dir=rodadas`
- `docs/dividas.md`: o comando de reverificação das linhas 16, 118, 120 e 121, sem `-i` com dois `-e`
- `premissas-do-front.md`:267: a resposta da operação existe desde a M3-1, só para serviço (ADR-087)
- o comentário do `/_to_delete/` no `.gitignore`; ADR-084 (status e emenda) e a linha dela no índice

## Perguntas ao Rafael
| Pergunta | Resposta |
|---|---|
| A troca no `aplicar-rodada.md` cita a S-6 e a S-7; nenhum relatório guardado registra a S-6 | manter as duas, e este relatório registra o fato |
| Três frases em ADR ainda dizem que a porta da operação não existe (ADR-046:301, ADR-049:35, ADR-086:22) | só relatório |

## Previsões × resultado
| A previsão | O que saiu |
|---|---|
| regra 15, `ls -l`: só as três proibições | as três |
| regra 15, a forma que aborta: só a ADR-088:22 | só ela |
| as quatro células: `16: 0`, `121: 0`, `118: 3`, `120: 0` | iguais |
| a busca da operação: só o `estabelecimento.md`:224 e :392 | só as duas |
| o razão igual | 112 · 28 · 132, antes e depois |

## Defeitos achados
- **o que motivou a regra 18 e a linha do `ls -l`:** na S-6 e na S-7, um `ls -la` de quem aplicou
  mostrou o nome de usuário no relatório da conversa; nos dois casos, nada entrou no repositório. Na
  S-7, a pasta aplicada era a primeira versão do pacote, com o mesmo nome da reenviada
- na S-8, de novo: a primeira listagem da pasta `s8/` foi um `ls -la`, antes de ler o pacote; não foi
  para o relatório, e as seguintes foram `ls -A`
- do revisor de pacote, só para relatório: a regra 17 corta também buscas de segredo e de caminho de
  máquina, que precisam passar por `docs/rodadas/`; as formas novas das linhas 16, 118 e 120 cobrem
  menos grafias que o `-i` (medido igual hoje); a emenda da ADR-084 diz que a linha do `grep -i` entra
  "no 'Nunca no relatório'", e ela entra num parágrafo próprio logo depois; o §2 prevê `1` sem `echo $?`
- a prova do `revisor-de-diff` sobre o `facfb7f`, que achou a :267, rodou em 23/11, fora de rodada:
  achou também o `merchant.ts` não regravado (impede o commit) e a segunda passada movida pela troca no
  `ExpedienteCorrenteResponse.java`

## O razão
| | antes | depois |
|---|---|---|
| recorte estreito | 112 | 112 |
| segunda passada | 28 | 28 |
| linhas numeradas | 132 | 132 |
