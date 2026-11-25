# S-10 — o auditor de marco: a especificação conferida por quem não leu o pacote

**Os dois revisores partem do pacote.** O `revisor-de-pacote` confere o plano contra as regras do
método; o `revisor-de-diff`, o código contra o pacote. Se a minha leitura da especificação estiver
errada, o pacote nasce errado, o código o segue, e os dois aprovam. Na M3-3, o critério da `V9`
estava errado no pacote, e só apareceu porque o revisor de pacote foi conferir o banco.

**O Rafael decidiu em 23/11:** um terceiro subagente, o **`auditor-de-marco`**, só leitura, que
começa do outro lado:

1. **fase 1, às cegas:** refaz a tabela de um marco — rota, tela, teste, exercida — a partir do PRD
   e do repositório, **sem ler `docs/rodadas/`, os pacotes nem a tabela**;
2. **fase 2:** só então compara com `docs/marcos/marco-N.md`. A divergência em que a tabela afirma
   **mais** do que o repositório **impede declarar** o marco;
3. devolve **a lista dos arquivos que abriu**, para a independência ser conferível.

Ele roda **antes de o Rafael declarar um marco fechado** — um item novo no `fechar-marco.md`.

**A prova é contra o marco 2**, que já tem registro de passagem, com uma **isca**: uma cópia da
tabela em `_to_delete/` com **uma** célula falsa — a coluna *exercida* da 3.2b passa a dizer que a
volta na abertura seguinte foi exercida, quando o item 15 da passagem humana diz o contrário. O
original não muda.

⚠ **Texto, configuração e um subagente. Nenhum código de serviço.** ⚠ **A
`metodo-delivery-platform` vale** — o revisor de pacote roda antes do primeiro comando.

⚠ **Esta rodada vem depois da R-17.** Ela não toca o razão, e a P-7 e a R-18 continuam válidas
depois dela.

---

## §0 · Antes de tudo

```bash
git log --oneline -3; git status --short
date +%d/%m/%Y
ls -A s10/ s10/agente/
ls s10/APLICAR-*.md
echo "== controle (tem de achar, é da R-17):"
ls docs/propostas/o-que-falta-para-operar-como-saas.md
grep -c "^| 137 " docs/dividas.md
ls -A .claude/agents/
gh run list --repo rafael-albuquerque07/delivery-platform --branch main --limit 200 --json workflowName,conclusion,createdAt -q 'group_by(.workflowName)[] | "\(.[0].workflowName) \(.[0].conclusion) \(.[0].createdAt[0:10])"'
```

- O `ls s10/APLICAR-*.md` mostra **só** `s10/APLICAR-s10-o-auditor-de-marco.md`.
- O controle da R-17 acha a proposta e dá **`1`**. **Se não, a R-17 não foi aplicada: pare.**
- `.claude/agents/` tem **só** `revisor-de-diff.md` e `revisor-de-pacote.md`.
- No `gh`, os treze workflows, **nenhum vermelho**.

---

## §1 · As premissas

**Medidas num clone de `4819195`.** O script da §3 rodou nesse clone: 4 arquivos, 4 trocas,
1 emenda e o subagente novo; a isca trocou uma célula só; o razão não se moveu.

| # | O que este pacote supõe | Situação |
|---|---|---|
| P1 | O `fechar-marco.md` tem seis itens, e o último é *"Quem declara o marco fechado é o Rafael"* | lido no clone |
| P2 | Nenhum documento cita os itens do `fechar-marco.md` pelo número — renumerar o 6 para 7 não deixa nada falso | `grep` pelas formas *item*, *regra*, *passo* junto do nome do arquivo, no clone: vazio |
| P3 | A célula *exercida* da 3.2b no `marco-2.md` diz que a volta não foi exercida, e o item 15 da passagem humana diz o mesmo | `marco-2.md`:21; `passagem-humana.md`:235 e :240 |
| P4 | O marco 2 tem 8 critérios no PRD, e 3 deles levam *"Entra a partir do 7a"* | `PRD.md`, H3.1 e H3.2 |
| P5 | O subagente novo só carrega numa sessão nova | como o da S-4 e o da S-6 — §5 |

---

## §2 · As medições

```bash
grep -c "^[0-9]\. " .claude/skills/metodo-delivery-platform/fechar-marco.md
grep -c "| — (o item 15 diz que a volta na abertura seguinte não foi exercida) |" docs/marcos/marco-2.md
sed -n 235p docs/operacao/passagem-humana.md | cut -c1-120; sed -n 240p docs/operacao/passagem-humana.md | cut -c1-120
grep -c "Entra a partir do 7a" docs/PRD.md
node scripts/razao.mjs
```

- Itens do `fechar-marco.md`: **`6`**. A célula da 3.2b: **`1`**. O *"Entra a partir do 7a"* no
  PRD: **`3`** — os três critérios do marco 2 que saem da conta.
- O item 15 da passagem e o aviso *"O que o item 15 não prova"*.
- A saída do `razao.mjs` é o **antes** da §6.

---

## §3 · O que se escreve — pelo script

```bash
node s10/aplica-s10.js "$(date +%d/%m/%Y)"
git status --short
```

**Leia o script e o `s10/agente/auditor-de-marco.md` antes de rodar.** Quatro trocas e uma emenda,
cada trecho antigo casando uma vez só, ou nada é escrito:

| Arquivo | O que muda |
|---|---|
| `.claude/agents/auditor-de-marco.md` | **novo**: as duas fases, o que ele não lê, e a lista dos arquivos abertos |
| `fechar-marco.md` | o item 6 novo: o auditor antes de declarar; o antigo 6 vira 7, com *"a tabela e a auditoria na frente"* |
| `SKILL.md` | a linha *"Fechar um marco"* da tabela das tarefas cita o subagente |
| ADR-084 | status e emenda |
| `README.md` das decisões | a linha da 084 |

⚠ **Se o script parar**, cole o erro e pare.

---

## §4 · A regra 15

```bash
grep -rn -i "subagente" CLAUDE.md .claude docs README.md --include=*.md --exclude-dir=rodadas | grep -v "^docs/architecture/decisions/" | cut -c1-120
grep -rn -i "quem declara o marco" CLAUDE.md .claude docs --include=*.md --exclude-dir=rodadas | cut -c1-120
```

- O primeiro acha **seis linhas**: as quatro do `aplicar-rodada.md` (os dois revisores, passos 3 e
  11), a do `SKILL.md` e a do `fechar-marco.md`. *Nenhuma diz que os subagentes são dois.*
- O segundo, **uma**: o item 7 do `fechar-marco.md`.

⚠ **Nenhum texto novo usa as formas da segunda passada nem cita na prosa o título da seção de
gatilhos.**

---

## §5 · A prova — antes do commit, numa segunda sessão

**Como na S-9:** o subagente só carrega numa sessão que abre com ele no disco. **Não faça o commit
ainda.** Primeiro a isca:

```bash
node s10/faz-isca.js
diff docs/marcos/marco-2.md _to_delete/isca-marco-2.md | wc -l
git status --short docs/marcos
```

**`4`** linhas de `diff` — uma célula trocada —, e **nada** em `docs/marcos`: o original não muda.

Então **peça ao Rafael** que abra uma segunda sessão do Claude Code no repositório, sem fechar esta,
e mande só isto:

> *"Use o auditor-de-marco para auditar o marco 2, conferindo contra a tabela
> _to_delete/isca-marco-2.md."*

**A previsão, escrita antes:**

| | O que tem de sair |
|---|---|
| a fase 1 | **5** critérios na conta — 3.1a, 3.1c, 3.1d, 3.2a e 3.2b — e **3** fora, pelo *"Entra a partir do 7a"* do PRD: 3.1b, 3.1e e 3.2c |
| a divergência da isca | **3.2b, coluna exercida**: a tabela diz *"19/10, item 15: … exercida"*, o repositório diz que não foi — peso **impede declarar** |
| a independência | a lista de arquivos abertos **sem** `docs/rodadas/`, **sem** `APLICAR-*.md`, e de `_to_delete/` só a isca |

*Outras divergências podem aparecer — o auditor pode achar, por exemplo, que um teste afirma menos
do que a tabela diz. Cole todas: são achados de verdade sobre o marco 2, e vão para o relatório. A
prova é a isca.*

- **Se a isca passar** — o auditor não acusar a 3.2b —, **pare sem commit** e traga a resposta
  inteira ao Rafael: o subagente não serve como está.
- **Se ele abrir arquivo que não podia**, a mesma coisa: pare e traga.
- **Se o subagente não carregar** na segunda sessão, pare e diga.

Com a isca acusada e a lista limpa, cole a resposta da segunda sessão nesta, e siga.

---

## §6 · O razão — **previsão de igualdade**

`.claude/` está fora das contas. A emenda da ADR-084 não escreve gatilho, não cita o título da seção
e não usa as formas da segunda passada. *Medido no clone: igual.* Na sua máquina, **os números que a
R-17 deixou**, antes e depois. ⚠ **Se qualquer número se mover, pare.**

---

## §7 · O relatório e o commit

**O relatório:** o §0; as saídas da §2; o `git status` do script; a busca da regra 15; a isca; **a
resposta inteira do auditor**, com a previsão e uma coluna *"achou?"*; o razão, antes e depois.

**O passo 12:** a pasta `docs/rodadas/S-10/`, com o `RELATORIO.md` e as duas conferências de caminho
de máquina e de token. **A isca não entra**: ela fica em `_to_delete/`.

**O `revisor-de-diff`** antes do commit, com o arquivo novo por `git add -N`.

**Um commit**, com os cinco arquivos — os quatro do script e o subagente — e a pasta da rodada:

```
chore(metodo): o auditor de marco confere a tabela contra a especificação, às cegas (ADR-084)
```

Confira com `git diff --cached --name-only` — **cinco linhas fora de `docs/rodadas/`**. E `git push`.
A pasta `s10/` vai para `_to_delete/s10-aplicada`.
