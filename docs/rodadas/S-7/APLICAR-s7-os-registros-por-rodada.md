# S-7 — cada pacote aplicado fica no repositório, com o relatório resumido

**Até aqui, o pacote aplicado ia para `_to_delete/`, que o git ignora.** O porquê de cada rodada
ficava nos commits, nas ADRs e no razão; o pacote — as previsões, as medições, as perguntas, as
versões que pararam — sumia. Nesta semana isso custou: para revisar a M3-1, foi preciso perguntar em
que pasta de `_to_delete/` ela estava.

**O Rafael decidiu em 21/11:** nenhum framework de SDD entra — a ideia vem do registro de mudanças
do OpenSpec, sem a ferramenta, que duplicaria o PRD e as ADRs. **Cada pacote aplicado passa a ficar
em `docs/rodadas/<RODADA>/`, no commit da própria rodada, com um `RELATORIO.md` resumido.**

**O que muda junto, e por quê:**

- **o razão:** os pacotes citam os comandos dele, as fórmulas dos gatilhos e as formas da segunda
  passada. Contados, cada rodada guardada mudaria as duas contas sem escrever gatilho. Os três
  comandos do cabeçalho ganham `--exclude-dir=rodadas`, e o `scripts/razao.mjs` o mesmo corte;
- **o passo 12 do `aplicar-rodada.md`** passa a mandar guardar, e diz que a contagem de arquivos
  que um pacote prevê para o commit não inclui `docs/rodadas/` — as rodadas que já estão na fila
  (R-17, P-7, R-18) dizem *"a pasta vai para `_to_delete/`"* e contam os arquivos do commit sem ela;
  **o passo 12 vale por cima delas**;
- **o `revisor-de-diff`** deixa de acusar `docs/rodadas/` como arquivo a mais;
- **o `metodo-das-rodadas.md`**:7, que diz que os pacotes vão para `_to_delete/`;
- **as ADRs 072 e 084**, por emenda.

E esta rodada guarda, além de si mesma, **as duas que vieram logo antes: a M3-2 e a S-6.**

⚠ **Texto, configuração e uma linha de script. Nenhum código de serviço.** ⚠ **A
`metodo-delivery-platform` vale** — o revisor roda antes do primeiro comando.

⚠ **Esta rodada vem DEPOIS da S-6**: ela muda o `scripts/razao.mjs` e o `revisor-de-diff`, que a
S-6 cria.

---

## §0 · Antes de tudo

```bash
git log --oneline -3; git status --short
date +%d/%m/%Y
ls -A s7/ s7/rodadas/
echo "== controles (têm de achar, são da S-6 e da M3-2):"
ls scripts/razao.mjs .claude/agents/revisor-de-diff.md
grep -c "ADR-088" docs/architecture/decisions/README.md
ls docs/rodadas 2>/dev/null; echo "rodadas=$?"
gh run list --repo rafael-albuquerque07/delivery-platform --branch main --limit 200 --json workflowName,conclusion,createdAt -q 'group_by(.workflowName)[] | "\(.[0].workflowName) \(.[0].conclusion) \(.[0].createdAt[0:10])"'
```

- Os controles têm de achar os dois arquivos e dar **`1` ou mais**. **Se algum falhar, a M3-2 ou a
  S-6 não foi aplicada: pare.**
- `docs/rodadas` **não pode existir**: `rodadas=2`.
- No `gh`, uma linha por workflow — os treze —, e **nenhum vermelho** no `main`: a M3-2 deixou o `frontend` verde, e a V-1, o `contracts`. **Vermelho: pare
  e diga qual** (o passo 2, desde a S-6).

---

## §1 · As premissas

**Medidas num clone de `b275220`, com a S-6 aplicada antes por script.** O script da §3.1 rodou
nesse clone: 8 arquivos, 11 trocas, 2 emendas e o `README.md` das rodadas. E, com o próprio pacote
da S-7 copiado para `docs/rodadas/S-7/`, o `razao.mjs` e o `grep` do recorte deram **o mesmo número
de antes**: o corte funciona.

| # | O que este pacote supõe | Situação |
|---|---|---|
| P1 | Os comandos do razão moram só no cabeçalho do `docs/dividas.md`: os candidatos (linha 9 e 10), o recorte (13) e a segunda passada (22 a 24) | `grep -rn` pelas formas dos comandos em `docs`, `.claude` e `CLAUDE.md`, no clone |
| P2 | O passo 12 do `aplicar-rodada.md` é *"A pasta do pacote vai para `_to_delete/`"* | lido no clone |
| P3 | Fora do passo 12, só o `metodo-das-rodadas.md`:7 diz que os pacotes vão para `_to_delete/`; o `.gitignore`:110 a ignora, e isso fica | `grep -rn "_to_delete"` em `CLAUDE.md`, `.claude`, `docs`, `.gitignore` e `README.md` |
| P4 | O item 1 do `revisor-de-diff` diz *"Arquivo a mais ou a menos é achado."* | o arquivo da S-6 |
| P5 | Os pacotes da M3-2 e da S-6 estão em `_to_delete/m32-aplicada` e `_to_delete/s6-aplicada`, como os dois mandaram | **não medido** — §2 |

---

## §2 · As medições

```bash
grep -n "exclude=dividas.md" docs/dividas.md | cut -c1-140
grep -n "^12\. " .claude/skills/metodo-delivery-platform/aplicar-rodada.md
grep -rn "_to_delete" CLAUDE.md .claude docs .gitignore README.md | cut -c1-140
ls -A _to_delete/ | grep -i -e "m32" -e "s6"
node scripts/razao.mjs
```

- `exclude=dividas.md`: as linhas 9, 13 e 23 do cabeçalho, e as do texto que as explica.
- As pastas da M3-2 e da S-6: **anote os nomes**. Se a M3-2 tiver mais de uma versão em
  `_to_delete/` (`m32-v1`, `m32-v2`), **só a aplicada** vai para `docs/rodadas/`; as outras ficam
  onde estão, e o relatório da M3-2 diz que elas existiram.
- A saída do `razao.mjs` é o **antes** da §4.

---

## §3 · O que se escreve

### §3.1 · O método e o razão — pelo script

```bash
node s7/aplica-s7.js "$(date +%d/%m/%Y)"
git diff --stat
```

**Leia o script e o `s7/rodadas/README.md` antes de rodar.** Onze trocas e duas emendas, cada trecho
antigo casando uma vez só, ou nada é escrito:

| Arquivo | O que muda |
|---|---|
| `aplicar-rodada.md` | o passo 12: o pacote aplicado fica em `docs/rodadas/<RODADA>/`, no commit da rodada, com o `RELATORIO.md` |
| `revisor-de-diff.md` | o item 1 não acusa `docs/rodadas/` |
| `scripts/razao.mjs` | pula `docs/rodadas/`, e o comentário do topo diz por quê |
| `docs/dividas.md` | `--exclude-dir=rodadas` nos três comandos do cabeçalho, e um parágrafo depois do que explica o `--exclude=dividas.md` |
| `metodo-das-rodadas.md` | a linha 7 |
| ADRs 072 e 084 | status e emenda |
| `docs/rodadas/README.md` | **novo**: o que a pasta guarda, por que fica fora do razão, e a forma do `RELATORIO.md` |

⚠ **Se o script parar**, cole o erro e pare.

### §3.2 · As três rodadas guardadas

```bash
mkdir -p docs/rodadas/M3-2 docs/rodadas/S-6 docs/rodadas/S-7
cp -r _to_delete/<a pasta da M3-2 aplicada>/. docs/rodadas/M3-2/
cp -r _to_delete/<a pasta da S-6 aplicada>/. docs/rodadas/S-6/
cp -r s7/. docs/rodadas/S-7/
find docs/rodadas -type f | sort
```

E um **`RELATORIO.md`** em cada uma, na forma do `docs/rodadas/README.md`:

- **S-7:** o desta rodada, escrito ao fim, antes do commit.
- **M3-2 e S-6:** se o relatório delas ainda estiver nesta sessão, resuma-o na mesma forma. **Se não
  estiver**, escreva só o cabeçalho — a data, o commit de partida, os commits pelo
  `git log --oneline --grep` com o título da mensagem — e uma linha: *"O relatório completo desta
  rodada não ficou guardado: ela é anterior à S-7."* **Não reconstrua de memória** o que não está
  escrito em lugar nenhum.

⚠ **Antes do commit, confira o que entra em `docs/rodadas/`:**

```bash
grep -rn -i -e "c:/users" -e "c:\\\\users" -e "appdata" -e "~1\." docs/rodadas --exclude="APLICAR-s7-*" --exclude="revisor-de-diff.md"; echo "maquina=$?"
grep -rln -e "gho_" -e "ghp_" -e "github_pat_" docs/rodadas --exclude="APLICAR-s7-*"; echo "token=$?"
```

Os dois têm de dar **`1`**: nenhum caminho com nome de usuário, nenhum token. *Os `--exclude` tiram
este pacote e a cópia do `revisor-de-diff` da S-6 porque os dois contêm os próprios padrões, como
texto — medido: são os únicos dos três pacotes. Sem isso, a busca acharia a si mesma.* *A isca da S-4 tem
`cat .env` como texto de comando — isso é texto, e fica; o que não pode é um valor.*

### §3.3 · A regra 15

```bash
grep -rn -e "vai para \`_to_delete" -e "vão para \`_to_delete" CLAUDE.md .claude docs --include=*.md | grep -v "^docs/rodadas/" | cut -c1-160
```

Tem de achar **nada**: o passo 12 e o `metodo-das-rodadas.md` foram trocados, e os pacotes antigos
dentro de `docs/rodadas/` contam a história deles. Outra ocorrência: troque, ou pare e diga.

⚠ **Nenhum texto novo usa as formas da segunda passada nem cita na prosa o título da seção de
gatilhos** — e, desta vez, isso vale também para o `docs/rodadas/README.md`, mesmo estando fora das
contas.

---

## §4 · O razão — **previsão de igualdade**

| | antes (§2) | depois do script | depois das três pastas |
|---|---|---|---|
| o recorte estreito devolve | *N* | **N** | **N** |
| a segunda passada | *P* | **P** | **P** |
| linhas numeradas | *L* | **L** | **L** |

**Rode o `razao.mjs` e o `grep` do recorte nas três colunas.** A terceira é a prova do corte: as
três pastas trazem dezenas de citações das fórmulas, e nenhuma pode entrar na conta. *Medido no
clone, com a S-7 guardada: igual.* ⚠ **Se qualquer número se mover, pare.**

---

## §5 · O relatório e o commit

**O relatório:** o §0; as saídas da §2; o `git diff --stat`; a lista do `find docs/rodadas`; as duas
conferências da §3.2; a busca da regra 15; as três colunas do razão.

**Um commit**, com os sete arquivos do script, o `docs/rodadas/README.md` e as três pastas:

```
docs: cada pacote aplicado fica em docs/rodadas, com o relatório resumido (ADR-084, ADR-072)
```

E `git push`. **Esta é a primeira rodada que segue o passo 12 novo:** ela mesma já está em
`docs/rodadas/S-7/`, e a pasta `s7/` vai para `_to_delete/s7-aplicada`.
