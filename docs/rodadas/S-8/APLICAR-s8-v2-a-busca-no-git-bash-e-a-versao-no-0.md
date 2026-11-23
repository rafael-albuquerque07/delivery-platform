# S-8 v2 — a busca roda no Git Bash e fora das rodadas, e o §0 confere a versão do pacote

**A v2 é a v1 com uma troca a mais**, decidida pelo Rafael em 23/11: a linha 267 do
`docs/front/premissas-do-front.md`, que o `revisor-de-diff` achou falsa na prova sobre o `facfb7f`
(§3). **É a primeira rodada que segue a regra 18 que ela mesma cria:** a pasta `s8/` tem de ter este
arquivo, `APLICAR-s8-v2-a-busca-no-git-bash-e-a-versao-no-0.md`, e nenhum outro `APLICAR-`. Se a v1
também estiver lá, pare.

**Três coisas que a S-7 mostrou, e que sem esta rodada se repetem na próxima:**

1. **A linha que proíbe `ls -l` no "Nunca no relatório" não entrou.** A pasta `s7/` aplicada era a
   primeira versão — onze trocas, não doze —, e o agente voltou a listar a pasta com `ls -la`,
   com o nome de usuário na coluna do dono. A versão reenviada tinha **o mesmo nome** da primeira, e
   nada no §0 as distinguia. *O defeito é de quem escreveu o pacote.*
2. **No Git Bash, `grep -i` com mais de um `-e` aborta** (saída 134). Duas vezes na S-7: a
   conferência de caminho de máquina teve de ser refeita em `node`. A forma de um padrão só, com
   `\|` — a dos comandos do razão —, funciona; sem `-i`, vários `-e` também.
3. **As buscas que percorrem `docs` passam a achar os pacotes guardados.** Eles citam as frases
   antigas, como texto, e a busca da regra 15 da próxima rodada as acharia. A S-7 cortou só as
   contas do razão e o censo; a varredura de nomes de evento do `contracts/README.md` ficou.
4. **A `premissas-do-front.md`:267 está falsa desde a M3-1.** Diz que a resposta *"a loja está
   aberta agora?"* não existe; ela existe, como rota interna (ADR-087). A consequência para a tela
   continua verdadeira — o gateway não roteia `/internal/` —, e só ela fica. *Defeito do pacote da
   M3-1: a busca da regra 15 não achou a frase. Quem achou foi o `revisor-de-diff`.*

**E quatro linhas do razão trazem a forma que aborta**, no comando de reverificação: a 16, a 118, a
120 e a 121. Quem as reverificar no Git Bash recebe o erro no lugar da conta. A célula não aceita
`\|` (regra 14): a forma nova tira o `-i` e lista as grafias com `-e`, ou junta as grafias num
padrão só sem `|`. **Cada uma foi medida no clone: a mesma saída da antiga** — vazio, vazio, três
arquivos, vazio.

**O Rafael decidiu em 23/11:** uma S-8 curta, só texto, **antes da M3-3**.

⚠ **Texto e configuração. Nenhum código, nenhum script de método.** ⚠ **A
`metodo-delivery-platform` vale** — o revisor roda antes do primeiro comando.

⚠ **Esta rodada vem DEPOIS da S-7, e antes da M3-3, da R-17, da P-7 e da R-18.** As quatro foram
reescritas como **v2**, com as buscas na forma nova e o §0 conferindo esta rodada.

---

## §0 · Antes de tudo

```bash
git log --oneline -3; git status --short
date +%d/%m/%Y
ls -A s8/
ls s8/APLICAR-*.md
echo "== controle (tem de achar, é da S-7):"
ls docs/rodadas/README.md docs/rodadas/S-7/RELATORIO.md
grep -c "^17\. " .claude/skills/metodo-delivery-platform/escrever-rodada.md
gh run list --repo rafael-albuquerque07/delivery-platform --branch main --limit 200 --json workflowName,conclusion,createdAt -q 'group_by(.workflowName)[] | "\(.[0].workflowName) \(.[0].conclusion) \(.[0].createdAt[0:10])"'
```

- O `main` em **`d69d2c4`**. O `ls s8/APLICAR-*.md` tem de mostrar **só**
  `s8/APLICAR-s8-v2-a-busca-no-git-bash-e-a-versao-no-0.md`.
- O controle da S-7 acha os dois arquivos. A regra 17 **ainda não existe**: `0`.
- No `gh`, os treze workflows, **nenhum vermelho**.

---

## §1 · As premissas

**Medidas num clone de `d69d2c4`.** O script da §3 rodou nesse clone: 9 arquivos, 12 trocas,
1 emenda, e o razão não se moveu.

| # | O que este pacote supõe | Situação |
|---|---|---|
| P1 | O "Nunca no relatório" do `aplicar-rodada.md` termina em *"nome de usuário do Windows."*, sem o `ls -l` | lido no clone |
| P2 | O `escrever-rodada.md` tem as regras 1 a 16, e a 16 é a da mutação | lido no clone |
| P3 | O `revisor-de-pacote.md` tem os itens 1 a 20, e o 20 é o da mutação | lido no clone |
| P4 | Fora de `docs/rodadas/`, a única busca que percorre `docs` sem o corte é a do `contracts/README.md`:44 | a busca da §2, no clone |
| P5 | Fora de `docs/rodadas/`, a forma `-i` com dois `-e` está na ADR-088:22, numa célula do corpo, e nas linhas 16, 118, 120 e 121 do razão | a busca da §2, no clone |
| P6 | Nenhum documento conta as regras ou os itens por extenso | a busca da §2, no clone |
| P7 | Fora do corpo das ADRs, só a `premissas-do-front.md`:267 diz que a resposta da operação não existe; o `estabelecimento.md`:297 já aponta a rota da M3-1 | a busca da §2, no clone |
| P8 | A :267 não tem forma do razão: trocá-la não move as contas | a busca da §2, no clone: `0` |

---

## §2 · As medições

```bash
grep -n "Nunca no relatório" -A1 .claude/skills/metodo-delivery-platform/aplicar-rodada.md
grep -c "^1[0-9]\. " .claude/skills/metodo-delivery-platform/escrever-rodada.md
grep -c "^2[0-9]\. \|^1[0-9]\. " .claude/agents/revisor-de-pacote.md
grep -rn "grep -r" CLAUDE.md .claude docs contracts README.md frontend/README.md --include=*.md --exclude-dir=rodadas | grep " docs" | grep -v "exclude-dir=rodadas" | cut -c1-140
grep -rn -e " -[a-z]*i[a-z]* -e " CLAUDE.md .claude docs contracts --include=*.md --exclude-dir=rodadas | cut -c1-140
grep -rn -i "dezesseis regras\|16 regras\|vinte itens\|20 itens" CLAUDE.md .claude docs --include=*.md --exclude-dir=rodadas
grep -rn -i "Operacao[A-Za-z]*.\{0,60\}não existe\|a porta não existe" docs/dominio docs/front docs/operacao CLAUDE.md --include=*.md | cut -c1-140
sed -n 267p docs/front/premissas-do-front.md | grep -c -i "nesta altura\|por enquanto\|ainda não existe\|quando houver\|até que haja\|gatilho escrito"
node scripts/razao.mjs
```

- O "Nunca no relatório" sem `ls -l`. Regras de 10 a 16: **`7`**. Itens de 10 a 20: **`11`**.
- A busca de `grep -r` em `docs` sem o corte: **três linhas**, e só o `contracts/README.md`:44
  percorre `docs` inteira. As outras duas ficam: a ADR-088:22 busca num arquivo só, o
  `catalogo.md`; a linha 1 do razão busca em `backend/` e cita `docs` noutro trecho da célula.
- A forma que aborta: **cinco linhas** — a ADR-088:22 e, no `dividas.md`, as linhas 16, 118, 120 e
  121 da tabela. A da ADR-088 é corpo de ADR: **fica**, e quem a reverificar roda a forma de um
  padrão só. As quatro do razão o script troca.
- Nenhuma contagem por extenso: **vazio** (`1`).
- A busca da operação: **três linhas** — a `premissas-do-front.md`:267, que o script troca; e, no
  `estabelecimento.md`, a :224, que fala de outra porta (a do `identity`, ADR-001), e a :392, que
  fala do `maxEntregasSimultaneas`. As duas ficam. E a :267 sem forma do
  razão: **`0`**.
- A saída do `razao.mjs` é o **antes** da §5.

---

## §3 · O que se escreve — pelo script

```bash
node s8/aplica-s8.js "$(date +%d/%m/%Y)"
git diff --stat
```

**Leia o script antes de rodar.** Doze trocas e uma emenda, cada trecho antigo casando uma vez só,
ou nada é escrito:

| Arquivo | O que muda |
|---|---|
| `aplicar-rodada.md` | o "Nunca no relatório" ganha o `ls -l`; um parágrafo novo: `grep -i` de um padrão só, e busca em `docs` com o corte |
| `escrever-rodada.md` | **regra 17**: a busca roda no Git Bash e fora de `docs/rodadas/` — em célula de tabela, sem `-i` e com as grafias em `-e`; **regra 18**: pacote reenviado muda de nome, e o §0 confere a versão |
| `revisor-de-pacote.md` | os itens **21** e **22**, que conferem as duas regras |
| `contracts/README.md` | a varredura de nomes de evento ganha `--exclude-dir=rodadas`, e uma frase diz por quê |
| `docs/dividas.md` | o comando de reverificação das linhas 16 (duas vezes), 121, 118 e 120, na forma que não aborta, com *"(S-8: sem `-i` com dois `-e`, medido igual)"* nas três últimas |
| `docs/front/premissas-do-front.md` | a linha 267, da tabela do §6: a resposta existe desde a M3-1, só para serviço (ADR-087), e o gateway não roteia `/internal/`; o gatilho passa a ser a primeira tela que mostre aberta/fechada |
| `.gitignore` | o comentário do `/_to_delete/`: é para onde vai a pasta original, depois de copiada para `docs/rodadas/` |
| ADR-084 | status e emenda |
| `README.md` das decisões | a linha da 084 |

⚠ **Se o script parar**, cole o erro e pare.

---

## §4 · A regra 15

```bash
grep -rn "ls -la\|ls -l " CLAUDE.md .claude docs --include=*.md --exclude-dir=rodadas | cut -c1-140
grep -rn -e " -[a-z]*i[a-z]* -e " CLAUDE.md .claude docs contracts --include=*.md --exclude-dir=rodadas | cut -c1-140
```

- O primeiro só pode achar **proibições**: a regra 11 do `escrever-rodada.md`, o item 10 do
  `revisor-de-pacote.md` e o "Nunca no relatório" novo — três linhas. Um `ls -l` que alguém
  mande rodar: troque por `ls -A`, ou pare e diga.
- O segundo só pode achar **a ADR-088:22**, pelo motivo da §2: as quatro linhas do razão saíram.

E as quatro células trocadas, rodadas — a previsão é a medida no clone, **igual à da forma antiga**:

```bash
for n in 16 121 118 120; do c=$(grep "^| $n |" docs/dividas.md | grep -o '`grep [^`]*`' | head -1 | tr -d '`'); echo "$n: $(bash -c "$c" | wc -l)"; done
```

**`16: 0`, `121: 0`, `118: 3`, `120: 0`.** Outro número: pare e cole.

E a busca da operação da §2, outra vez: **só o `estabelecimento.md`:224 e :392.**

⚠ **Nenhum texto novo usa as formas da segunda passada nem cita na prosa o título da seção de
gatilhos.**

---

## §5 · O razão — **previsão de igualdade**

`.claude/`, `contracts/README.md` e `.gitignore` estão fora das contas; a :267 não tem forma do razão; o `dividas.md` exclui a si mesmo, e as quatro trocas não mexem em número de linha. A emenda da ADR-084 não
escreve gatilho, não cita o título da seção e não usa as formas da segunda passada. *Medido no
clone: igual, antes e depois.* Na sua máquina, **112 · 28 · 132**, iguais. ⚠ **Se qualquer número
se mover, pare.**

---

## §6 · O relatório e o commit

**O relatório:** o §0; as saídas da §2; o `git diff --stat`; a busca da regra 15; o razão, antes e
depois.

**O `revisor-de-diff`**, antes do commit (passo 11): é rodada de texto, mas é curta, e esta é a
segunda vez que ele roda — **cole a resposta dele inteira**.

**Um commit**, com os nove arquivos do script e a pasta `docs/rodadas/S-8/` (passo 12):

```
docs: a busca do pacote roda no Git Bash e fora das rodadas, e o §0 confere a versão (ADR-084)
```

Confira com `git diff --cached --name-only` — **nove linhas fora de `docs/rodadas/`**. E `git push`.
A pasta `s8/` vai para `_to_delete/s8-aplicada`.
