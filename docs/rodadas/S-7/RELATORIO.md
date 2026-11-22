# S-7 — cada pacote aplicado fica no repositório, com o relatório resumido

- **Aplicada em:** 22/11/2026 · **sobre:** `7083fe1` · **versão do pacote:** v1
- **Commits:** o commit desta rodada traz esta pasta e as da M3-2, da V-1 e da S-6

## O que se mediu
- §0: os controles da S-6 e da M3-2 acharam; `rodadas=2`; os 13 workflows verdes no `main`
- §2: as pastas aplicadas são `m32-aplicada` (v3), `v1-aplicada` e `s6-aplicada`; a M3-2 tem
  também `m32` e `m32-v2`, que ficaram em `_to_delete/`
- o censo das colunas (`dividas.md`, dois comandos): 108 e 13, antes e depois
- o recorte sem o corte, com as quatro pastas no disco: 116 — o corte tira 4

## O que mudou
- `aplicar-rodada.md` (passo 12), `revisor-de-diff.md` (item 1), `scripts/razao.mjs`, `metodo-das-rodadas.md`
- `docs/dividas.md`: `--exclude-dir=rodadas` nos três comandos do cabeçalho e nos dois do censo
- ADRs 072 e 084 (status e emenda) e as duas linhas do índice; o comentário do `.gitignore`
- novos: `docs/rodadas/README.md` e as pastas `M3-2/`, `V-1/`, `S-6/` e `S-7/`, cada uma com `RELATORIO.md`

## Perguntas ao Rafael
| Pergunta | Resposta |
|---|---|
| Guardar também a V-1, que veio entre a M3-2 e a S-6? | guardar |
| Padrão da regra 15, índice das ADRs, comentário do `.gitignore` | os três |
| `--exclude-dir=rodadas` nos dois comandos do censo das colunas? | acrescentar |
| Rodada de medição e versão que parou, no passo 12 | medição entra no commit da rodada seguinte; versão que parou não se guarda, e o relatório da aplicada diz que ela existiu, até onde foi e por que parou — o mesmo no `docs/rodadas/README.md` |

## Previsões × resultado
| A previsão | O que saiu |
|---|---|
| o razão igual nas três colunas | 112 · 28 · 132 nas três |
| nenhum caminho de máquina, nenhum token em `docs/rodadas/` | `maquina=1`, `token=1` |
| a regra 15 não acha nada | nada, com o padrão da forma antiga inteira |

## Defeitos achados
- do pacote: a busca da regra 15 acharia o próprio passo 12 novo; a V-1 ficava de fora; o índice das
  ADRs e o `.gitignore` ficavam afirmando o antigo; o censo das colunas sem o corte
- no caminho: o `grep -i` com vários `-e` aborta no Git Bash (saída 134) — a busca de caminho de
  máquina foi refeita em `node`, com as mesmas exclusões
- o revisor apontou, só para relatório: a varredura de nomes de evento do `contracts/README.md`
  passa a contar os pacotes guardados; o gitleaks roda sobre eles a cada push

## O razão
| | antes | depois do script | com as quatro pastas |
|---|---|---|---|
| recorte estreito | 112 | 112 | 112 |
| segunda passada | 28 | 28 | 28 |
| linhas numeradas | 132 | 132 | 132 |
