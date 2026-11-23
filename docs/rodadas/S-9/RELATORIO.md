# S-9 — a guarda do Bash: o que a regra proíbe, uma máquina recusa antes de rodar

- **Aplicada em:** 23/11/2026 · **sobre:** `a7f7f21` · **versão do pacote:** v1
- **Commits:** o commit desta rodada traz esta pasta

## O que se mediu
- §0: `a7f7f21`; um `APLICAR-` só na pasta; o controle da S-8 deu `1`; `.claude/` com `agents`,
  `settings.local.json` e `skills`, sem `hooks` nem `settings.json`; node v24; os 13 workflows verdes
- §2: `ignore=1`; a prova da guarda `26 de 26 casos certos`; `passa=0`; `recusa=2`, com a mensagem da regra 17
- o script: 5 arquivos, 7 trocas, 1 emenda e 2 arquivos novos; a prova, de novo, 26 de 26
- **a guarda ligada**, numa segunda sessão aberta em paralelo, antes do commit, com os arquivos no
  disco: o `grep -i -e a -e b README.md` foi recusado antes de rodar —

  ```
  PreToolUse:Bash hook error: [node "$CLAUDE_PROJECT_DIR/.claude/hooks/guarda-do-bash.mjs"]: Recusado pela guarda do repositório (regra 17 do escrever-rodada.md): `grep -i` com mais de um `-e` aborta no Git Bash (saída 134). Junte as alternativas num padrão só: grep -i "uma\|outra". Em célula de tabela, sem `|`: tire o `-i` e liste as grafias com `-e`.
  ```

  A P2 (o protocolo do gancho) e a P4 (o `$CLAUDE_PROJECT_DIR` no Windows) ficam medidas. A pedido
  do Rafael, a segunda sessão rodou depois a forma de um padrão só, `grep -i "a\|b" README.md`, que
  passou (saída 0)

## O que mudou
- novos: `.claude/hooks/guarda-do-bash.mjs` e `.claude/settings.json` (só o gancho `PreToolUse`)
- `aplicar-rodada.md` (o "Nunca no relatório", o corte, as duas conferências no passo 12),
  `escrever-rodada.md` (regra 17), `revisor-de-pacote.md` (item 21)
- por decisão do Rafael: `SKILL.md` (regra 4) e o comentário do `.gitignore` incluem `hooks/` e o
  `settings.json`, só com ganchos; a emenda da ADR-084 estende a frase do corpo; o comentário da guarda
- ADR-084 (status e emenda) e a linha dela no índice
- o commit tem **nove** arquivos fora de `docs/rodadas/`, e não os sete da §7 do pacote: os dois a
  mais são o `SKILL.md` e o `.gitignore`, da decisão da regra 4

## Perguntas ao Rafael
| Pergunta | Resposta |
|---|---|
| A regra 4 diz que de `.claude/` só `skills/` e `agents/` entram no git | trocar, com o `settings.json` só com ganchos — `SKILL.md`, `.gitignore` e emenda |
| A prova da guarda vinha depois do push, e a emenda já afirma que ela recusa | provar antes do commit, numa segunda sessão em paralelo que só roda a frase da §5 |
| O comentário da guarda diz que o nome saiu no relatório também na S-8 | corrigir: apareceu na S-6 e na S-7; na S-8, na saída da tela, não no relatório final |

## Previsões × resultado
| A previsão | O que saiu |
|---|---|
| a prova: 26 de 26 | 26 de 26, antes e depois das trocas |
| regra 15, "toda busca": só a ADR-084:103 | só ela |
| regra 15, `ls -l`: quatro linhas | as quatro |
| a guarda ligada recusa, com a mensagem da regra 17 | recusou |
| as duas conferências do passo 12: `1` | `maquina=1`, `token=1`, com `--exclude` para o `APLICAR-s9-*` e o `aplica-s9.js`, que citam os padrões; sem o `--exclude`, as duas acham só esses dois |
| o razão igual | 112 · 28 · 132, antes e depois |

## Defeitos achados
- do pacote: a regra 4 não acompanhava os arquivos novos; a prova da guarda depois do push; o
  comentário da guarda com um fato que o `RELATORIO.md` da S-8 desmente. A cópia em `ganchos/`
  desta pasta é o pacote como chegou; o arquivo em `.claude/hooks/` tem o comentário corrigido
- do revisor de pacote, só para relatório: formas que escapam da guarda e imprimem o dono (`ls -o`,
  `stat -c '%U'`, `find -printf '%u'`, `bash -c "ls -la"`, `wsl -- ls -la`, `time`/`timeout` na
  frente, `ls --format long`, a ferramenta PowerShell, que o gancho não vê) e `grep -i -ea -eb`;
  recusas indevidas possíveis (linha de heredoc começando com `ls -l`, `-e "-visible"`); a mensagem
  da regra 17 na guarda sem a saída de "um padrão só sem `|`"; a P5 e a P6 medidas num clone, sem o
  comando que enumera
