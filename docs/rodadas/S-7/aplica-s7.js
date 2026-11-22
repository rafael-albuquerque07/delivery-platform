// S-7 — roda da raiz do repositório, DEPOIS da S-6: node s7/aplica-s7.js DD/MM/AAAA
// Cada trecho antigo tem de ocorrer exatamente uma vez; se um falhar, nada é escrito.
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

const trocas = {
  [S + 'aplicar-rodada.md']: [
    ['12. **A pasta do pacote vai para `_to_delete/`**, que o git ignora. Não se apaga.\n',
     '12. **O pacote aplicado fica em `docs/rodadas/<RODADA>/`, no commit da rodada** — o último, quando\n' +
     '    são dois: a pasta do pacote como rodou, e um `RELATORIO.md` com o relatório resumido, na forma\n' +
     '    do `docs/rodadas/README.md`. A contagem de arquivos que o pacote prevê para o commit não inclui\n' +
     '    essa pasta. Depois, a pasta original vai para `_to_delete/`, que o git ignora. Não se apaga.\n' +
     '    Rodada de medição não tem commit: a pasta dela entra em `docs/rodadas/` no commit da rodada que\n' +
     '    vem logo depois. Versão que parou não se guarda: o `RELATORIO.md` da versão aplicada diz que ela\n' +
     '    existiu, até onde foi e por que parou.\n'],
  ],
  'docs/architecture/decisions/README.md': [
    ['· **emendada** (22/11/2026: as contas têm um script) |\n',
     '· **emendada** (22/11/2026: as contas têm um script) · **emendada** (' + D + ': a pasta das rodadas fica fora das contas) |\n'],
    ['· **emendada** (22/11/2026: o revisor de diff) |\n',
     '· **emendada** (22/11/2026: o revisor de diff) · **emendada** (' + D + ': o pacote aplicado fica no repositório) |\n'],
  ],
  '.gitignore': [
    ['# Pacote de aplicação é transitório: extraído, aplicado, descartado.\n# Nunca deveria ser commitado — já aconteceu quatro vezes.\n',
     '# Pacote na raiz é de passagem: extraído e aplicado, nunca commitado dali — já\n# aconteceu quatro vezes. O pacote aplicado fica em docs/rodadas/ (S-7).\n'],
  ],
  '.claude/agents/revisor-de-diff.md': [
    ['   pedido no pacote? Cada peça que o pacote pede está no diff? Arquivo a mais ou a menos é achado.\n',
     '   pedido no pacote? Cada peça que o pacote pede está no diff? Arquivo a mais ou a menos é achado —\n' +
     '   salvo `docs/rodadas/<RODADA>/`, que o passo 12 manda guardar.\n'],
  ],
  'scripts/razao.mjs': [
    ["        || p.endsWith('/node_modules')) continue;\n",
     "        || p.endsWith('/node_modules') || p.endsWith('/rodadas') || p.includes('/rodadas/')) continue;\n"],
    ["// Só lê. Não escreve nada e não chama a rede.",
     "// A pasta docs/rodadas/ fica fora das duas contas, como o --exclude-dir=rodadas dos comandos (S-7).\n//\n// Só lê. Não escreve nada e não chama a rede."],
  ],
  'docs/dividas.md': [
    ['README.md frontend/README.md --include=*.md --exclude=dividas.md\n',
     'README.md frontend/README.md --include=*.md --exclude=dividas.md --exclude-dir=rodadas\n'],
    ['--include=*.tsx --exclude=dividas.md |', '--include=*.tsx --exclude=dividas.md --exclude-dir=rodadas |'],
    ['  docs/ backend/*/*/src/main --include=*.md --include=*.java --exclude=dividas.md \\\n',
     '  docs/ backend/*/*/src/main --include=*.md --include=*.java --exclude=dividas.md --exclude-dir=rodadas \\\n'],
    ['no cabeçalho na mesma rodada. **Instrumento que se mede a si mesmo não está medindo.**\n',
     'no cabeçalho na mesma rodada. **Instrumento que se mede a si mesmo não está medindo.**\n\n' +
     '**E o `--exclude-dir=rodadas` também não** (S-7). Os pacotes guardados em `docs/rodadas/` citam\n' +
     'estes comandos, as fórmulas dos gatilhos e as formas da segunda passada — é o trabalho deles.\n' +
     'Contados, cada rodada guardada mudaria as duas contas sem escrever gatilho nenhum. O mesmo corte\n' +
     'vale para os dois comandos do censo das colunas, mais abaixo.\n'],
    ['backend contracts --include=*.md --include=*.java --include=*.ts --include=*.tsx --exclude-dir=node_modules --exclude-dir=build --exclude=dividas.md\ngrep -rn -i "marco',
     'backend contracts --include=*.md --include=*.java --include=*.ts --include=*.tsx --exclude-dir=node_modules --exclude-dir=build --exclude-dir=rodadas --exclude=dividas.md\ngrep -rn -i "marco'],
    ['frontend/README.md backend contracts --include=*.md --include=*.java --include=*.ts --include=*.tsx --exclude-dir=node_modules --exclude-dir=build --exclude=dividas.md\n',
     'frontend/README.md backend contracts --include=*.md --include=*.java --include=*.ts --include=*.tsx --exclude-dir=node_modules --exclude-dir=build --exclude-dir=rodadas --exclude=dividas.md\n'],
  ],
  'docs/operacao/metodo-das-rodadas.md': [
    ['**O método das rodadas morava nos pacotes**, e os pacotes vão para `_to_delete/` depois de\naplicados.',
     '**O método das rodadas morava nos pacotes**, e os pacotes iam para `_to_delete/` depois de\naplicados — desde a S-7, cada um fica em `docs/rodadas/`, com o relatório resumido.'],
  ],
  [adr('072')]: [
    [': as contas têm um script (S-6)\n', ': as contas têm um script (S-6) · **emendada em ' + D + '**: a pasta das rodadas fica fora das contas (S-7)\n'],
  ],
  [adr('084')]: [
    [': o revisor de diff (S-6)\n', ': o revisor de diff (S-6) · **emendada em ' + D + '**: o pacote aplicado fica no repositório (S-7)\n'],
  ],
};

const aoFim = {
  [adr('072')]:
    '\n## Emenda de ' + D + ', S-7 — a pasta das rodadas fica fora das contas\n\n' +
    'Os pacotes aplicados passam a ficar em `docs/rodadas/` (ADR-084, emenda desta data), e eles citam\n' +
    'os comandos do razão, as fórmulas dos gatilhos e as formas da segunda passada. Os três comandos do\n' +
    'cabeçalho do `docs/dividas.md` ganham `--exclude-dir=rodadas`, e o `scripts/razao.mjs` o mesmo\n' +
    'corte, pelo motivo que o `--exclude=dividas.md` já tinha: instrumento que conta a própria\n' +
    'descrição não está medindo. O corpo fica como está.\n',
  [adr('084')]:
    '\n## Emenda de ' + D + ', S-7 — o pacote aplicado fica no repositório\n\n' +
    'Até a S-6, o pacote aplicado ia para `_to_delete/`, que o git ignora: o porquê de cada rodada\n' +
    'ficava nos commits, nas ADRs e no razão, e o pacote — as previsões, as medições, as perguntas —\n' +
    'sumia. **Decisão do Rafael:** cada pacote aplicado fica em `docs/rodadas/<RODADA>/`, no commit da\n' +
    'rodada, com um `RELATORIO.md` resumido (o passo 12 do `aplicar-rodada.md`; a forma no\n' +
    '`docs/rodadas/README.md`). Nenhum framework de SDD entrou: a ideia veio do registro de mudanças\n' +
    'do OpenSpec, sem a ferramenta, porque ela duplicaria o PRD e as ADRs. O corpo fica como está.\n',
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
if (fs.existsSync('docs/rodadas/README.md')) throw new Error('docs/rodadas/README.md já existe');
for (const [f, s] of Object.entries(saida)) fs.writeFileSync(f, s);
fs.mkdirSync('docs/rodadas', { recursive: true });
fs.copyFileSync('s7/rodadas/README.md', 'docs/rodadas/README.md');
console.log('ok:', Object.keys(saida).length + 1, 'arquivos,',
  Object.values(trocas).reduce((n, l) => n + l.length, 0), 'trocas,', Object.keys(aoFim).length, 'emendas e o README das rodadas');
