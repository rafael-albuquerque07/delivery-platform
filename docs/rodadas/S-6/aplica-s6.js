// S-6 — roda da raiz do repositório: node s6/aplica-s6.js DD/MM/AAAA
// Cada trecho antigo tem de ocorrer exatamente uma vez; se um falhar, nada é escrito.
const fs = require('fs');
const data = process.argv[2];
if (!/^\d{2}\/\d{2}\/\d{4}$/.test(data || '')) throw new Error('passe a data do `date +%d/%m/%Y`');
const dir = 'docs/architecture/decisions';
const um = (p) => {
  const l = fs.readdirSync(dir).filter((f) => f.startsWith(p)).map((f) => dir + '/' + f);
  if (l.length !== 1) throw new Error(p + ': ' + l.length);
  return l[0];
};
const S = '.claude/skills/metodo-delivery-platform/';

const trocas = {
  [S + 'aplicar-rodada.md']: [
    ['   que o §0 do pacote diz. Diferente: pare e diga o que achou.\n',
     '   que o §0 do pacote diz. Diferente: pare e diga o que achou. **E a pipeline**, a última execução de\n' +
     '   cada workflow no `main`:\n' +
     "   `gh run list --branch main --limit 200 --json workflowName,conclusion,createdAt -q 'group_by(.workflowName)[] | \"\\(.[0].workflowName) \\(.[0].conclusion) \\(.[0].createdAt[0:10])\"'`\n" +
     '   — workflow vermelho no `main` que o pacote não previu: pare e diga qual, com o\n' +
     '   `gh run view <número> --log-failed`.\n'],
    ['9. **Testes:** os que o pacote nomear. A suíte do front, sob carga, roda com `--maxWorkers=2`.\n',
     '9. **Testes:** os que o pacote nomear. A suíte do front, sob carga, roda com `--maxWorkers=2`.\n' +
     '   **Rodada que toca `frontend/` ou `contracts/openapi/` roda os seis passos do `frontend.yml`**,\n' +
     '   na ordem dele — `format`, `lint`, `tipos:conferir`, `typecheck`, `test`, `build` —, antes do commit,\n' +
     '   com `frontend/src/api/generated/` já no índice (`git add`), porque o `tipos:conferir` compara a\n' +
     '   árvore com o índice.\n'],
    ['11. **O commit leva a mensagem do pacote, palavra por palavra**, mais as linhas de atribuição. Depois\n    `git push` e `git log --stat -1`.\n',
     '11. **O commit leva a mensagem do pacote, palavra por palavra**, mais as linhas de atribuição. Depois\n    `git push` e `git log --stat -1`. **Numa rodada que muda código, antes do commit:**\n' +
     '    `git add -N` nos arquivos novos da rodada, para que o `git diff` mostre o conteúdo deles; depois grave\n' +
     '    `git status --short` e `git diff` (com `--cached`, se já houver algo no índice) num arquivo em\n' +
     '    `_to_delete/`, e passe esse arquivo e o pacote ao subagente **`revisor-de-diff`**. Achado que\n' +
     '    impede o commit vai para o Rafael; o resto, para o relatório. *Sem o subagente na sessão,\n' +
     '    confira você mesmo, item por item da lista dele, e diga que foi assim.*\n'],
    ['8. **O razão (`docs/dividas.md`) segue a ADR-072.** Nenhuma linha se apaga. Os números da igualdade\n   antes e depois;',
     '8. **O razão (`docs/dividas.md`) segue a ADR-072.** Nenhuma linha se apaga. Os números da igualdade\n   antes e depois — `node scripts/razao.mjs` dá os três de uma vez;'],
  ],
  '.claude/agents/revisor-de-pacote.md': [
    ['conferir algo, diga *"não conferido"* e por quê.\n',
     'conferir algo, diga *"não conferido"* e por quê.\n\n' +
     '**A última linha: quantos arquivos você abriu, além do pacote.** Abra o que o pacote cita e o dono\n' +
     'de cada regra que você confere — não o repositório inteiro.\n'],
  ],
  'docs/dividas.md': [
    ['  | grep -v "/build/"\n```\n',
     '  | grep -v "/build/"\n```\n\n' +
     '**As três contas de uma vez:** `node scripts/razao.mjs`, da raiz — o recorte estreito, a segunda\n' +
     'passada e as linhas numeradas da tabela, e quantas linhas do recorte estão em arquivo que o git não\n' +
     'conhece. **Os dois comandos acima são a especificação dele:** os números têm de bater, e, se\n' +
     'divergirem, o defeito é do script (ADR-072, emenda de ' + data + ').\n'],
  ],
  [um('ADR-072-')]: [
    ['- **Status:** aceita · **emendada em 16/10/2026**: "condição nossa" tem duas espécies, e uma delas não é dívida\n',
     '- **Status:** aceita · **emendada em 16/10/2026**: "condição nossa" tem duas espécies, e uma delas não é dívida · **emendada em ' + data + '**: as contas têm um script (S-6)\n'],
  ],
  [um('ADR-084-')]: [
    ['· **emendada em 18/11/2026**: o subagente é do repositório (S-5)\n',
     '· **emendada em 18/11/2026**: o subagente é do repositório (S-5) · **emendada em ' + data + '**: o revisor de diff (S-6)\n'],
  ],
};

// as duas emendas entram ao fim do arquivo
const aoFim = {
  [um('ADR-072-')]:
    '\n## Emenda de ' + data + ', S-6 — as contas têm um script\n\n' +
    'As três contas do razão — o recorte estreito, a segunda passada e as linhas numeradas — passam a\n' +
    'sair também de `scripts/razao.mjs`, versionado. Antes, a contagem das linhas numeradas rodava num\n' +
    'arquivo fora do repositório, na pasta temporária de quem aplicava. **Os dois comandos do cabeçalho\n' +
    'do `docs/dividas.md` continuam sendo a especificação**; o script os reproduz, e divergência entre\n' +
    'os dois é defeito do script. O script também diz quantas linhas do recorte estão em arquivo que o\n' +
    'git não conhece — as cópias de `frontend/.tsbuild/`, que a conciliação já desconta. O corpo fica\n' +
    'como está.\n',
  [um('ADR-084-')]:
    '\n## Emenda de ' + data + ', S-6 — o revisor de diff\n\n' +
    'O `revisor-de-pacote` lê o plano antes do primeiro comando; o **`revisor-de-diff`**, em\n' +
    '`.claude/agents/`, lê o que foi feito, antes do commit de toda rodada que muda código (o passo 11\n' +
    'do `aplicar-rodada.md`). Só lê, como o primeiro. Nasceu de um defeito que nenhum dos dois\n' +
    'revisores pegaria: a M3-1 mudou o contrato do `merchant` e não regravou os tipos do front. E o\n' +
    '`revisor-de-pacote` passa a dizer quantos arquivos abriu, para o custo de cada revisão ser medido.\n' +
    'O corpo fica como está.\n',
};

const saida = {};
for (const [f, lista] of Object.entries(trocas)) {
  let s = fs.readFileSync(f, 'utf8');
  if (s.includes('\r\n')) throw new Error(f + ': CRLF');
  for (const [a, b] of lista) {
    const n = s.split(a).length - 1;
    if (n !== 1) throw new Error(`${f}: "${a.slice(0, 60)}" ocorre ${n} vez(es)`);
    s = s.replace(a, () => b);
  }
  if (aoFim[f]) s = s.replace(/\n*$/, '\n') + aoFim[f];
  saida[f] = s;
}
for (const [f, s] of Object.entries(saida)) fs.writeFileSync(f, s);
console.log('ok:', Object.keys(saida).length, 'arquivos,',
  Object.values(trocas).reduce((n, l) => n + l.length, 0), 'trocas e', Object.keys(aoFim).length, 'emendas');
