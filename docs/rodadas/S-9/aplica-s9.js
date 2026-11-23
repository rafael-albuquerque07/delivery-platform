// S-9 — roda da raiz do repositório, DEPOIS da S-8: node s9/aplica-s9.js DD/MM/AAAA
// Cada trecho antigo tem de ocorrer exatamente uma vez; se um falhar, nada é escrito.
// Copia também a guarda e o settings.json, que não podem existir antes.
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
const R17_ANTIGA =
  '17. **A busca do pacote roda no Git Bash, e fora de `docs/rodadas/`.** `grep -i` leva um padrão\n' +
  '    só: com mais de um `-e`, ele aborta no Git Bash do Rafael (saída 134), e a conta prevista vira\n' +
  '    o número do erro. As alternativas vão num padrão, com `\\|` — `grep -rn -i "imprim\\|impress"`;\n' +
  '    em célula de tabela, onde `|` não entra (regra 14), tire o `-i` e liste as grafias com `-e`.\n' +
  '    E toda busca que percorre `docs` leva `--exclude-dir=rodadas`: os pacotes guardados lá citam as\n' +
  '    frases antigas, e a busca da regra 15 as acharia.\n';
const R17_NOVA =
  '17. **A busca do pacote roda no Git Bash, e a de frase antiga fica fora de `docs/rodadas/`.**\n' +
  '    `grep -i` leva um padrão só: com mais de um `-e`, ele aborta no Git Bash do Rafael (saída 134),\n' +
  '    e a conta prevista vira o número do erro — desde a S-9, a guarda do Bash o recusa antes de\n' +
  '    rodar. As alternativas vão num padrão, com `\\|` — `grep -rn -i "imprim\\|impress"`. Em célula\n' +
  '    de tabela, onde `|` não entra (regra 14): um padrão só que cubra as grafias sem `|` —\n' +
  '    `grep -rn -i "rate.\\?limit"` —, ou sem `-i`, com as grafias em `-e`. A busca de frase antiga —\n' +
  '    a do razão e a da regra 15 — que percorre `docs` leva `--exclude-dir=rodadas`: os pacotes\n' +
  '    guardados lá citam as frases antigas. **As conferências de caminho de máquina e de token não\n' +
  '    levam:** é em `docs/rodadas/` que elas olham (`aplicar-rodada.md`, passo 12).\n';

const trocas = {
  [S + 'aplicar-rodada.md']: [
    ['`escrever-rodada.md`; na S-6 e na S-7, o nome saiu no relatório por essa coluna).\n',
     '`escrever-rodada.md`; na S-6 e na S-7, o nome saiu no relatório por essa coluna). Desde a S-9, a\n' +
     'guarda do Bash (`.claude/hooks/guarda-do-bash.mjs`) recusa esses comandos antes de rodarem.\n'],
    ['diga no relatório. E busca sua que percorra `docs` leva `--exclude-dir=rodadas`.\n',
     'diga no relatório; a guarda do Bash também recusa essa forma. E busca sua de frase antiga em\n' +
     '`docs` leva `--exclude-dir=rodadas`; as conferências de caminho de máquina e de token do passo 12\n' +
     'não levam, porque é em `docs/rodadas/` que elas olham.\n'],
    ['    existiu, até onde foi e por que parou.\n',
     '    existiu, até onde foi e por que parou.\n' +
     '    **Antes do commit, as duas conferências do que entra na pasta** — as duas têm de dar `1`:\n\n' +
     '    ```bash\n' +
     '    grep -rn -i "c:/users\\|c:\\\\\\\\users\\|appdata\\|~1\\." docs/rodadas/<RODADA>; echo "maquina=$?"\n' +
     '    grep -rln "gho_\\|ghp_\\|github_pat_" docs/rodadas/<RODADA>; echo "token=$?"\n' +
     '    ```\n\n' +
     '    Pacote que cite esses padrões como texto — o da S-9 cita — sai da busca por `--exclude`, com o\n' +
     '    nome do arquivo, e o relatório diz qual.\n'],
  ],
  [S + 'escrever-rodada.md']: [[R17_ANTIGA, R17_NOVA]],
  '.claude/agents/revisor-de-pacote.md': [
    ['21. **A busca** (regra 17). Algum comando junta `-i` com mais de um `-e`? Alguma busca percorre\n' +
     '    `docs` sem `--exclude-dir=rodadas`? Cada um é achado, com a forma certa ao lado.\n',
     '21. **A busca** (regra 17). Algum comando junta `-i` com mais de um `-e`? Alguma busca de frase\n' +
     '    antiga percorre `docs` sem `--exclude-dir=rodadas`? Alguma conferência de caminho de máquina ou\n' +
     '    de token o leva? Cada um é achado, com a forma certa ao lado.\n'],
  ],
  [adr('084')]: [
    [': a busca no Git Bash e fora das rodadas, e a versão no §0 (S-8)\n',
     ': a busca no Git Bash e fora das rodadas, e a versão no §0 (S-8) · **emendada em ' + D + '**: a guarda do Bash (S-9)\n'],
  ],
  [dir + '/README.md']: [
    ['· **emendada** (23/11/2026: a busca no Git Bash e fora das rodadas, e a versão no §0) |',
     '· **emendada** (23/11/2026: a busca no Git Bash e fora das rodadas, e a versão no §0) · **emendada** (' + D + ': a guarda do Bash) |'],
  ],
};

const aoFim = {
  [adr('084')]:
    '\n## Emenda de ' + D + ', S-9 — a guarda do Bash\n\n' +
    'Na S-8, com a linha nova já no `aplicar-rodada.md`, o primeiro comando de quem aplicou foi outra\n' +
    'vez um `ls -la` — antes de ler o pacote e a skill. Foi a terceira rodada seguida. **Texto lido\n' +
    'depois do primeiro comando não segura o primeiro comando.** Desde esta emenda, um gancho do Claude\n' +
    'Code — `.claude/settings.json`, `PreToolUse` sobre o Bash — chama `.claude/hooks/guarda-do-bash.mjs`\n' +
    'antes de cada comando, e ele recusa o que imprime o dono do arquivo (regra 11) e o `grep -i` com\n' +
    'mais de um `-e` (regra 17), com a forma certa na mensagem. O que está entre aspas é texto e passa.\n' +
    'Se a entrada não for a esperada, a guarda deixa passar: fechar travaria a sessão por um defeito\n' +
    'dela. `node .claude/hooks/guarda-do-bash.mjs --prova` roda os casos.\n\n' +
    'E a regra 17 fica mais estreita: a busca de frase antiga pula `docs/rodadas/`, e as conferências\n' +
    'de caminho de máquina e de token, que o passo 12 do `aplicar-rodada.md` passa a mandar rodar em\n' +
    'toda rodada, olham justamente para lá. Em célula de tabela, um padrão só sem `|` também serve.\n' +
    'O corpo fica como está.\n',
};

const novos = [
  ['s9/ganchos/guarda-do-bash.mjs', '.claude/hooks/guarda-do-bash.mjs'],
  ['s9/settings.json', '.claude/settings.json'],
];
for (const [, destino] of novos) if (fs.existsSync(destino)) throw new Error(destino + ' já existe');

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
fs.mkdirSync('.claude/hooks', { recursive: true });
for (const [origem, destino] of novos) fs.copyFileSync(origem, destino);
console.log('ok:', Object.keys(saida).length, 'arquivos,',
  Object.values(trocas).reduce((n, l) => n + l.length, 0), 'trocas,', Object.keys(aoFim).length, 'emenda, e',
  novos.length, 'arquivos novos');
