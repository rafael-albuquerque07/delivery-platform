// S-10 — roda da raiz do repositório: node s10/aplica-s10.js DD/MM/AAAA
// Cada trecho antigo tem de ocorrer exatamente uma vez; se um falhar, nada é escrito.
// Copia também o subagente novo, que não pode existir antes.
const fs = require('fs');
const D = process.argv[2];
if (!/^\d{2}\/\d{2}\/\d{4}$/.test(D || '')) throw new Error('passe a data do `date +%d/%m/%Y`');
const dir = 'docs/architecture/decisions';
const adr = (n) => {
  const l = fs.readdirSync(dir).filter((f) => f.startsWith('ADR-' + n + '-')).map((f) => dir + '/' + f);
  if (l.length !== 1) throw new Error('ADR-' + n + ': ' + l.length);
  return l[0];
};
const S = '.claude/skills/metodo-delivery-platform/';
const NOVO = '.claude/agents/auditor-de-marco.md';
if (fs.existsSync(NOVO)) throw new Error(NOVO + ' já existe');

const trocas = {
  [S + 'fechar-marco.md']: [
    ['6. **Quem declara o marco fechado é o Rafael**, com a tabela na frente.\n',
     '6. **Antes de o Rafael declarar, o subagente `auditor-de-marco` refaz a tabela às cegas** — do PRD\n' +
     '   e do repositório, sem ler `docs/rodadas/`, os pacotes nem a tabela — e só depois a compara com\n' +
     '   ela. Divergência em que a tabela afirma mais do que o repositório **impede declarar**, até a\n' +
     '   tabela ou o repositório mudar; a que afirma menos vai para o relatório. *Quem escreve as rodadas\n' +
     '   preenche a tabela; quem a audita não pode partir do que ele escreveu* (S-10).\n' +
     '7. **Quem declara o marco fechado é o Rafael**, com a tabela e a auditoria na frente.\n'],
  ],
  [S + 'SKILL.md']: [
    ['| [fechar-marco.md](fechar-marco.md) e a tabela do marco em docs/marcos/ |',
     '| [fechar-marco.md](fechar-marco.md), a tabela do marco em docs/marcos/ e o subagente `auditor-de-marco` |'],
  ],
  [adr('084')]: [
    [': a guarda do Bash (S-9)\n',
     ': a guarda do Bash (S-9) · **emendada em ' + D + '**: o auditor de marco (S-10)\n'],
  ],
  [dir + '/README.md']: [
    ['· **emendada** (23/11/2026: a guarda do Bash) |',
     '· **emendada** (23/11/2026: a guarda do Bash) · **emendada** (' + D + ': o auditor de marco) |'],
  ],
};

const aoFim = {
  [adr('084')]:
    '\n## Emenda de ' + D + ', S-10 — o auditor de marco\n\n' +
    'O `revisor-de-pacote` confere o plano contra as regras, e o `revisor-de-diff`, o código contra o\n' +
    'pacote. **Os dois partem do pacote**: se a leitura que quem escreveu fez da especificação estiver\n' +
    'errada, o pacote nasce errado, o código o segue, e os dois aprovam — foi o que o critério da `V9`\n' +
    'quase fez na M3-3. **Decisão do Rafael:** um terceiro subagente, o `auditor-de-marco`, só leitura,\n' +
    'que refaz às cegas a tabela de um marco — do PRD e do repositório, sem ler `docs/rodadas/`, os\n' +
    'pacotes nem a própria tabela — e só depois a compara com `docs/marcos/marco-N.md`. Ele roda antes\n' +
    'de o Rafael declarar um marco fechado (o item 6 do `fechar-marco.md`), e a divergência em que a\n' +
    'tabela afirma mais do que o repositório impede declarar. Ele é independente do texto de quem\n' +
    'escreve, não do modelo: a passagem humana continua sendo a única prova da tela. O corpo fica como\n' +
    'está.\n',
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
fs.copyFileSync('s10/agente/auditor-de-marco.md', NOVO);
console.log('ok:', Object.keys(saida).length, 'arquivos,',
  Object.values(trocas).reduce((n, l) => n + l.length, 0), 'trocas,', Object.keys(aoFim).length, 'emenda, e o subagente novo');
