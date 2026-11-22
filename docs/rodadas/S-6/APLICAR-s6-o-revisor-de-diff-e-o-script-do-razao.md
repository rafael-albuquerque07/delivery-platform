# S-6 — o revisor de diff, e as contas do razão num script do repositório

**Duas lacunas do método, as duas com defeito real por trás:**

1. **Ninguém lê o código antes do commit.** O `revisor-de-pacote` lê o plano, antes do primeiro
   comando. A M3-1 mudou o `contracts/openapi/merchant-service.json` e não regravou
   `frontend/src/api/generated/merchant.ts`. Nenhum teste do back ficou vermelho, e a pipeline
   `frontend` sim. Um leitor do diff pergunta *"o contrato mudou? os tipos também?"* e acha isso.
2. **A contagem das linhas numeradas do razão roda num arquivo fora do repositório**, na pasta
   temporária de quem aplica. Um instrumento do método sem versão, que some se a pasta for limpa,
   num caminho com o nome de usuário da máquina.

**O Rafael decidiu em 20/11:** um segundo subagente, o **`revisor-de-diff`**, só leitura, no fim de
toda rodada de código, antes do commit. E as três contas do razão num **script versionado**,
`scripts/razao.mjs`. **Contar é trabalho de script, não de agente**: mesmo número toda vez, nenhum
token gasto.

**E a pipeline entra no método.** A `frontend` ficou vermelha por sete dias sem ninguém ver: a
rodada da ADR-082 deixou três arquivos fora do Prettier, e a M3-1 empilhou os tipos não regravados.
O Rafael instalou e autenticou o `gh` em 21/11. **O passo 2 do `aplicar-rodada.md` passa a conferir
a última execução de cada workflow no `main`**, e **o passo 9 manda rodar os seis passos do
`frontend.yml`** em toda rodada que toca `frontend/` ou `contracts/openapi/`.

E um ajuste no revisor que existe: **ele passa a dizer quantos arquivos abriu.** A revisão da M3-2
levou 14 minutos e 181 mil tokens, e o método precisa saber se ele abre o que precisa ou o
repositório inteiro.

⚠ **Texto, configuração e um script de leitura. Nenhum código de serviço.** ⚠ **A
`metodo-delivery-platform` vale** — o revisor roda antes do primeiro comando.

⚠ **Depois da M3-2.** Com a R-17 e a P-7 não há conflito de texto, mas é uma rodada de cada vez.
**De preferência antes da M3-3**, para a próxima rodada de código já passar pelo revisor de diff.

---

## §0 · Antes de tudo

```bash
git log --oneline -3; git status --short
date +%d/%m/%Y
ls -A s6/ s6/agente/ s6/scripts/
echo "== controle (tem de achar, é da M3-2):"
grep -c "ADR-088" docs/architecture/decisions/README.md
ls .claude/agents/ scripts/
git check-ignore --no-index -v .claude/agents/revisor-de-diff.md scripts/razao.mjs; echo "check-ignore=$?"
```

- O controle da M3-2 tem de dar **`1` ou mais**. **Se der `0`, pare.**
- `.claude/agents/` tem de ter **só** o `revisor-de-pacote.md`; `scripts/`, **só** o
  `subir-local.sh`.
- O `check-ignore` tem de dar **`1`**: os dois arquivos entram no git.
- O `git status` só mostra pastas de pacote na raiz.

---

## §1 · As premissas

**Medidas num clone de `b275220`.** O script da §3.2 foi rodado nesse clone: 5 arquivos, 8 trocas
e 2 emendas. O `razao.mjs` deu **108 · 29 · 132**, iguais aos dois `grep` e ao `confere.js`
relatado na S-5, e o razão não se moveu.

| # | O que este pacote supõe | Situação |
|---|---|---|
| P1 | Os dois comandos do cabeçalho do `docs/dividas.md` são o recorte estreito e a segunda passada, e a tabela tem linhas numeradas de 1 a 132 | lido no clone |
| P2 | A conciliação do razão desconta 3 linhas do recorte, cópias em `frontend/.tsbuild/` | `dividas.md`:70 |
| P3 | O passo 11 do `aplicar-rodada.md` é o do commit; o 8, o do razão | lido no clone |
| P4 | O `revisor-de-pacote` termina em *"diga \"não conferido\" e por quê."* | lido no clone, depois da S-5 |
| P5 | O subagente novo pode só aparecer numa sessão nova | a S-4 mediu: o `revisor-de-pacote` carregou na sessão seguinte — §4 |
| P6 | O commit `facfb7f` muda o contrato do `merchant` e não muda `frontend/src/api/generated/` | `git show --stat facfb7f` no clone — **conferir** §2 |

---

## §2 · As medições

```bash
node --version
grep -c '^| [0-9]' docs/dividas.md
grep -n '^11\. \|^8\. ' .claude/skills/metodo-delivery-platform/aplicar-rodada.md | cut -c1-120
tail -2 .claude/agents/revisor-de-pacote.md
git show --stat --format=%h facfb7f | grep -e "contracts/" -e "frontend/"
ls _to_delete/ | grep -i "m3"
```

- `node` 22 ou mais. As linhas numeradas, o que a M3-2 deixou.
- O `facfb7f` tem de mostrar o `contracts/openapi/merchant-service.json` **e nenhum arquivo de
  `frontend/`**. É a isca de verdade da §4.
- A pasta da M3-1 em `_to_delete/` — anote o nome, ela vai para a §4.

---

## §3 · O que se escreve

### §3.1 · As duas peças novas

```bash
cp s6/agente/revisor-de-diff.md .claude/agents/
cp s6/scripts/razao.mjs scripts/
node scripts/razao.mjs
```

**Leia as duas antes de copiar.** O `razao.mjs` só lê: não escreve nada, não chama a rede, e usa
o git só para listar os arquivos que o git conhece.

⚠ **A prova do script — os números têm de bater com os dois `grep` do cabeçalho e com a contagem
que você usava:**

```bash
grep -rn "Gatilho escrito\|gatilho escrito" docs/ contracts/ CLAUDE.md backend/ frontend/ --include=*.md --include=*.java --include=*.ts --include=*.tsx --exclude=dividas.md | grep -v "/build/\|/node_modules/" | wc -l
grep -rn -i "nesta altura\|por enquanto\|ainda não existe\|quando houver\|até que haja" docs/ backend/*/*/src/main --include=*.md --include=*.java --exclude=dividas.md | grep -v "/build/" | wc -l
```

O *"em arquivo que o git não conhece"* tem de dar **3**, os três de `frontend/.tsbuild/`, que a
S-5 achou. **Qualquer número diferente entre o script e o `grep`: pare e cole os dois.** O
`confere.js` da sua pasta temporária **não entra no repositório**: o caminho dele traz o nome de
usuário.

### §3.2 · Os outros cinco arquivos, pelo script

```bash
node s6/aplica-s6.js "$(date +%d/%m/%Y)"
git diff --stat
```

**Leia o script antes de rodar.** Oito trocas e duas emendas, e cada trecho antigo tem de casar uma
vez só, ou nada é escrito:

| Arquivo | O que muda |
|---|---|
| `aplicar-rodada.md` | **o passo 2** confere a pipeline do `main` pelo `gh`; **o passo 8** aponta o `razao.mjs`; **o passo 9** manda rodar os seis passos do `frontend.yml`; **o passo 11** ganha o `revisor-de-diff` antes do commit de rodada de código |
| `revisor-de-pacote.md` | a última linha da resposta: quantos arquivos abriu |
| `docs/dividas.md` | **no cabeçalho**, depois do comando da segunda passada, um parágrafo: o script dá as três contas, e os comandos são a especificação dele |
| ADR-072 | status e emenda: as contas têm um script |
| ADR-084 | status e emenda: o revisor de diff |

⚠ **Se o script parar**, cole o erro e pare.

⚠ **A regra 15:**

```bash
grep -rn -e "confere.js" -e "revisor-de-diff" -e "razao.mjs" docs CLAUDE.md .claude | cut -c1-160
```

Tem de achar só o que esta rodada escreveu: o cabeçalho do razão, as duas emendas, o passo 8 e o
11, e o próprio subagente. **`confere.js` não pode aparecer.**

---

## §4 · A prova — o revisor de diff contra um defeito que já aconteceu

Grave o diff do `facfb7f` e chame o subagente com o pacote da M3-1:

```bash
{ echo "== git show --stat"; git show --stat --format=%h facfb7f; echo; echo "== git show"; git show --format=%h facfb7f; } > _to_delete/diff-facfb7f.txt
wc -l _to_delete/diff-facfb7f.txt
```

> *"Use o revisor-de-diff para revisar _to_delete/diff-facfb7f.txt contra o pacote
> _to_delete/<a pasta da M3-1>/APLICAR-m3-1-v2-as-duas-consultas-do-merchant.md."*

**A previsão, escrita antes de rodar:** o item **2** acha o contrato do `merchant` mudado sem
`frontend/src/api/generated/merchant.ts`, com peso **impede o commit**. É o defeito que a M3-2
conserta. *Outros achados podem aparecer — cole todos —, mas a prova é o item 2.* E a última linha
diz quantos arquivos ele abriu.

- **Se o subagente não carregar nesta sessão**, diga isso, faça o commit da §5 e entregue ao Rafael
  a frase acima, com o nome da pasta, para uma sessão nova.

**E o `revisor-de-pacote` sobre este pacote**, no passo 3, já com a linha nova: ele carrega o
arquivo de antes desta rodada, e **não** vai dizer quantos arquivos abriu. *Se disser, é a P7 da
S-5 medida: ele relê o próprio arquivo na sessão.*

---

## §5 · O razão, o relatório e o commit

**O razão — previsão de igualdade.** `scripts/` e `.claude/` estão fora dos dois comandos. O
parágrafo novo do cabeçalho está no próprio `dividas.md`, que os dois excluem. As emendas não
escrevem gatilho, não citam o título da seção e não usam as formas da segunda passada. *Medido no
clone: 108 · 29 · 132, antes e depois.* Na sua máquina, **os números que a M3-2 deixou, iguais**.
⚠ **Se qualquer um se mover, pare.**

**O relatório:** o §0; as saídas da §2; a saída do `razao.mjs` ao lado das dos dois `grep`; o
`git diff` dos cinco arquivos; a busca da regra 15; a resposta inteira do `revisor-de-diff` sobre o
`facfb7f`, com a previsão e uma coluna "achou?"; a resposta do `revisor-de-pacote` sobre este
pacote.

**Um commit**, com sete arquivos — `.claude/agents/revisor-de-diff.md`, `scripts/razao.mjs`, o
`aplicar-rodada.md`, o `revisor-de-pacote.md`, o `docs/dividas.md` e as ADRs 072 e 084:

```
docs: o revisor de diff confere o código antes do commit, e as contas do razão ganham um script (ADR-084, ADR-072)
```

Confira com `git diff --cached --name-only` — **sete linhas**, nenhuma de `s6/` nem de
`_to_delete/`. E `git push`. A pasta `s6/` vai para `_to_delete/s6-aplicada`.
