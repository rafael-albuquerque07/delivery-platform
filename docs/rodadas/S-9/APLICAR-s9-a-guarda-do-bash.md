# S-9 — a guarda do Bash: o que a regra proíbe, uma máquina recusa antes de rodar

**Na S-8, o primeiro comando de quem aplicou foi um `ls -la`**, antes de ler o pacote. A linha que
o proíbe já estava no `aplicar-rodada.md`, e foi a terceira rodada seguida em que o nome de usuário
saiu no relatório por essa coluna. **Texto lido depois do primeiro comando não segura o primeiro
comando.**

**O Rafael decidiu em 23/11:** uma S-9 curta, antes da M3-3, com um **gancho do Claude Code**
versionado no repositório. Antes de cada Bash, o Claude Code chama
`.claude/hooks/guarda-do-bash.mjs`, que recusa:

- o que imprime o dono do arquivo — `ls -l`, `ls -la`, `find -ls`, `stat` sem formato (regra 11);
- o `grep -i` com mais de um `-e`, que aborta no Git Bash (regra 17).

A mensagem de recusa diz a forma certa. **O que está entre aspas é texto e passa:** um
`grep "ls -la"` da regra 15 não é recusado.

**E dois defeitos meus na regra 17, que os revisores da S-8 acharam:**

- ela manda **toda** busca em `docs` pular `docs/rodadas/` — e as conferências de caminho de máquina
  e de token precisam justamente olhar lá. Elas passam a ser **um passo fixo do método** (passo 12),
  na forma que não aborta;
- em célula de tabela ela só oferece *"tire o `-i`"*, e eu mesmo usei, na linha 121 do razão, a
  outra saída — um padrão só sem `|`, `"rate.\?limit"` —, que ela não descreve.

⚠ **Texto, configuração e um script de leitura. Nenhum código de serviço.** ⚠ **A
`metodo-delivery-platform` vale** — o revisor roda antes do primeiro comando.

⚠ **Esta rodada vem DEPOIS da S-8, e antes da M3-3 v2.** As rodadas da fila não mudam: as buscas
delas já passam pela guarda (§1, P6).

---

## §0 · Antes de tudo

```bash
git log --oneline -3; git status --short
date +%d/%m/%Y
ls -A s9/ s9/ganchos/
ls s9/APLICAR-*.md
echo "== controle (tem de achar, é da S-8):"
grep -c "^18\. " .claude/skills/metodo-delivery-platform/escrever-rodada.md
ls -A .claude/
node --version
gh run list --repo rafael-albuquerque07/delivery-platform --branch main --limit 200 --json workflowName,conclusion,createdAt -q 'group_by(.workflowName)[] | "\(.[0].workflowName) \(.[0].conclusion) \(.[0].createdAt[0:10])"'
```

- O `main` em **`a7f7f21`**. O `ls s9/APLICAR-*.md` mostra **só**
  `s9/APLICAR-s9-a-guarda-do-bash.md`.
- O controle da S-8 dá **`1`**.
- `.claude/` tem **`agents`, `skills` e `settings.local.json`** — e **nem `hooks` nem
  `settings.json`**. Se algum dos dois existir, pare e diga o que há nele.
- `node` 20 ou mais. No `gh`, os treze workflows, **nenhum vermelho**.

---

## §1 · As premissas

**Medidas num clone de `a7f7f21`.** O script da §3 rodou nesse clone: 5 arquivos, 7 trocas,
1 emenda e os 2 arquivos novos; o razão não se moveu; a prova da guarda deu **26 de 26**.

| # | O que este pacote supõe | Situação |
|---|---|---|
| P1 | O repositório não tem `.claude/settings.json` nem `.claude/hooks/`, e o `.gitignore` não os ignora | `ls -A .claude` e `git check-ignore --no-index`, no clone — **conferir** §2 |
| P2 | O Claude Code chama um gancho `PreToolUse` com a chamada em JSON pela entrada padrão, e a saída `2` recusa o comando, devolvendo o stderr a quem chamou | a documentação do Claude Code; **não medido nesta máquina** — §5 |
| P3 | O gancho só carrega numa sessão nova, e o Claude Code pode pedir para revisá-lo pelo `/hooks` | como o subagente da S-4 e o da S-6 — §5 |
| P4 | No Windows, o `$CLAUDE_PROJECT_DIR` do comando do gancho chega expandido | **não medido** — §5 |
| P5 | A guarda recusa as três formas que abortaram ou vazaram, e deixa passar as outras **221** linhas de comando dos pacotes guardados, das rodadas da fila e da skill | medido no clone: 221 linhas, 3 recusas — o `grep -i -e` da S-7 (duas) e o da V-1 |
| P6 | Nenhuma linha de comando da M3-3 v2, da R-17, da P-7 e da R-18 é recusada | a mesma medição |

---

## §2 · As medições

```bash
git check-ignore --no-index -v .claude/settings.json .claude/hooks/guarda-do-bash.mjs; echo "ignore=$?"
node s9/ganchos/guarda-do-bash.mjs --prova | tail -3
printf '{"tool_name":"Bash","tool_input":{"command":"ls -A s9"}}' | node s9/ganchos/guarda-do-bash.mjs; echo "passa=$?"
printf '{"tool_name":"Bash","tool_input":{"command":"grep -i -e a -e b x"}}' | node s9/ganchos/guarda-do-bash.mjs; echo "recusa=$?"
node scripts/razao.mjs
```

- `ignore=1`: os dois entram no git.
- A prova: **`26 de 26 casos certos`**. Outro número: pare e cole a saída inteira.
- `passa=0`; `recusa=2`, com a mensagem da regra 17. *A guarda ainda não está ligada: é o arquivo
  rodado à mão, com a entrada que o Claude Code mandaria.* ⚠ **Não teste a recusa do `ls -la` assim
  nem de outro jeito** — se a guarda falhasse, o nome sairia de novo; a prova roda esse caso por
  dentro.
- A saída do `razao.mjs` é o **antes** da §6.

---

## §3 · O que se escreve — pelo script

```bash
node s9/aplica-s9.js "$(date +%d/%m/%Y)"
git status --short
```

**Leia o script, a guarda e o `settings.json` antes de rodar.** A guarda só lê a entrada: não
escreve nada e não chama a rede. Sete trocas e uma emenda, cada trecho antigo casando uma vez só,
ou nada é escrito:

| Arquivo | O que muda |
|---|---|
| `.claude/hooks/guarda-do-bash.mjs` | **novo**: a guarda, com os 26 casos da prova |
| `.claude/settings.json` | **novo**: só o gancho `PreToolUse` sobre o Bash — nenhuma permissão, nenhum segredo |
| `aplicar-rodada.md` | o "Nunca no relatório" diz que a guarda recusa; o parágrafo do `grep -i` estreita o corte; **o passo 12 ganha as duas conferências** de caminho de máquina e de token, na forma que não aborta |
| `escrever-rodada.md` | a regra 17: a busca de frase antiga pula `docs/rodadas/`, as conferências não; em célula, um padrão só sem `|` também serve |
| `revisor-de-pacote.md` | o item 21, no mesmo corte |
| ADR-084 | status e emenda |
| `README.md` das decisões | a linha da 084 |

⚠ **Se o script parar**, cole o erro e pare.

---

## §4 · A regra 15

```bash
grep -rn "toda busca\|Toda busca" CLAUDE.md .claude docs contracts --include=*.md --exclude-dir=rodadas | cut -c1-140
grep -rn "ls -la\|ls -l " CLAUDE.md .claude docs --include=*.md --exclude-dir=rodadas | cut -c1-100
```

- O primeiro só pode achar **a ADR-084:103**, a emenda da S-8, que a da S-9 estreita — emenda não
  se reescreve.
- O segundo, **quatro linhas**: as três proibições (regra 11, item 10 do revisor, o "Nunca no
  relatório") e a emenda nova da ADR-084, que conta o `ls -la` da S-8.

⚠ **Nenhum texto novo usa as formas da segunda passada nem cita na prosa o título da seção de
gatilhos.**

---

## §5 · A prova da guarda

**Nesta sessão**, de novo, com a guarda já no lugar:

```bash
node .claude/hooks/guarda-do-bash.mjs --prova | tail -1
```

**`26 de 26 casos certos`.**

**A guarda ligada só se prova numa sessão nova** (P3). Depois do push, feche esta sessão e abra
outra no repositório. Se o Claude Code pedir para revisar os ganchos, revise pelo `/hooks` e aceite
o da guarda. Então peça ao agente:

> *"Rode `grep -i -e a -e b README.md` e me diga o que aconteceu."*

**A previsão: a guarda recusa, com a mensagem da regra 17.** O comando é inofensivo de propósito:
se a guarda não estiver ligada, ele só aborta, e nada vaza.

- **Se o comando rodar** — saída `134`, ou o `grep` respondendo —, a guarda não carregou. **Pare e
  traga a resposta ao Rafael**: a causa pode ser o `$CLAUDE_PROJECT_DIR` no Windows (P4), e trocá-lo
  é decisão dele, com a medição na mão.

---

## §6 · O razão — **previsão de igualdade**

`.claude/` está fora das contas. A emenda da ADR-084 não escreve gatilho, não cita o título da seção
e não usa as formas da segunda passada. *Medido no clone: igual.* Na sua máquina, **112 · 28 · 132**,
antes e depois. ⚠ **Se qualquer número se mover, pare.**

---

## §7 · O relatório e o commit

**O relatório:** o §0; as saídas da §2; o `git status` do script; a busca da regra 15; a prova da
§5 nesta sessão; o razão, antes e depois.

**O passo 12, com as conferências novas.** Esta pasta guardada cita os próprios padrões, como texto,
no pacote e no script — **os dois saem da busca**:

```bash
grep -rn -i "c:/users\|c:\\\\users\|appdata\|~1\." docs/rodadas/S-9 --exclude="APLICAR-s9-*" --exclude="aplica-s9.js"; echo "maquina=$?"
grep -rln "gho_\|ghp_\|github_pat_" docs/rodadas/S-9 --exclude="APLICAR-s9-*" --exclude="aplica-s9.js"; echo "token=$?"
```

**Os dois dão `1`** — medido no clone, com a pasta copiada.

**O `revisor-de-diff`** antes do commit, com os arquivos novos por `git add -N`. Cole a resposta.

**Um commit**, com os sete arquivos — os cinco do script, a guarda e o `settings.json` — e a pasta
`docs/rodadas/S-9/`:

```
chore(metodo): a guarda do Bash recusa o que a regra proíbe, antes de rodar (ADR-084)
```

Confira com `git diff --cached --name-only` — **sete linhas fora de `docs/rodadas/`**. E `git push`.
A pasta `s9/` vai para `_to_delete/s9-aplicada`.

**E entregue ao Rafael a frase da §5**, para a sessão nova.
