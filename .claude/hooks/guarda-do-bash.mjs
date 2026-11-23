// A guarda do Bash (S-9, ADR-084): recusa, antes de rodar, dois comandos que as regras do método
// proíbem e que o texto não segurou.
//
//   1. o que imprime o dono do arquivo — `ls -l`, `ls -la`, `find -ls`, `stat` sem formato. O dono é
//      o nome de usuário da máquina; ele apareceu na S-6 e na S-7, e na S-8 o ls -la rodou outra vez,
//      como primeiro comando, com o nome na saída da tela — não no relatório final (regra 11 do
//      escrever-rodada.md);
//   2. `grep -i` com mais de um `-e`, que aborta no Git Bash (saída 134) e troca a conta pelo número
//      do erro (regra 17).
//
// O Claude Code chama este arquivo antes de cada Bash (.claude/settings.json, PreToolUse) e passa a
// chamada em JSON pela entrada padrão. Saída 2 recusa, e o texto do stderr volta para quem chamou.
// Saída 0 deixa passar. O que está entre aspas é texto, não comando: um `grep "ls -la"` passa.
//
// Falha aberta: se a entrada não for o JSON esperado, deixa passar. Fechar travaria a sessão inteira
// por um defeito deste arquivo, e as regras escritas continuam valendo.
//
// `node .claude/hooks/guarda-do-bash.mjs --prova` roda os casos do fim do arquivo e diz quais erram.
// Só lê a entrada. Não escreve nada e não chama a rede.

import { pathToFileURL } from 'node:url';

const SEM_DONO =
  'Recusado pela guarda do repositório (regra 11 do escrever-rodada.md): este comando imprime o dono ' +
  'do arquivo, que é o nome de usuário da máquina. Use `ls -A` para listar, e `stat -c` com formato ' +
  'para datas e tamanhos.';
const UM_PADRAO =
  'Recusado pela guarda do repositório (regra 17 do escrever-rodada.md): `grep -i` com mais de um `-e` ' +
  'aborta no Git Bash (saída 134). Junte as alternativas num padrão só: grep -i "uma\\|outra". ' +
  'Em célula de tabela, sem `|`: tire o `-i` e liste as grafias com `-e`.';

// As aspas viram uma palavra só: o que está entre elas não abre outro comando. Espaço e operador
// de shell dentro delas viram `_` — um `grep "ls -la"` não é um `ls`, e um caminho de programa
// entre aspas, como o do Git no Windows, continua sendo o programa.
function semAspas(c) {
  const palavra = (m) => m.slice(1, -1).replace(/[\s|;&()`$<>]/g, '_');
  return c.replace(/'[^']*'/g, palavra).replace(/"(?:\\.|[^"\\])*"/g, palavra);
}

// Quebra em comandos simples: `|`, `;`, `&&`, `||`, `&`, quebra de linha, `$(` e crase.
function comandos(c) {
  return semAspas(c)
    .split(/\|\||&&|[|;&\n`]|\$\(|\(|\)/)
    .map((s) => s.trim().split(/\s+/).filter(Boolean))
    .filter((t) => t.length > 0);
}

// O nome do programa: pula atribuições de ambiente, `sudo`, `xargs` e `command`, e o caminho.
function programa(t) {
  let i = 0;
  while (i < t.length && (/^[A-Za-z_][A-Za-z0-9_]*=/.test(t[i]) || ['sudo', 'xargs', 'command', 'env'].includes(t[i]))) i++;
  if (i >= t.length) return [null, []];
  return [t[i].replace(/^.*[\\/]/, '').replace(/\.exe$/i, ''), t.slice(i + 1)];
}

const curta = (a) => /^-[A-Za-z0-9]+$/.test(a);

export function veredito(comando) {
  for (const t of comandos(comando)) {
    const [p, args] = programa(t);
    if (p === 'ls' && args.some((a) => (curta(a) && a.includes('l')) || /^--format=(long|verbose)$/.test(a) || a === '-l')) return SEM_DONO;
    if (p === 'find' && args.some((a) => a === '-ls' || a === '-fls')) return SEM_DONO;
    if (p === 'stat' && !args.some((a) => /^(-c|--format|--printf|-[A-Za-z]*c)$/.test(a) || /^--(format|printf)=/.test(a))) return SEM_DONO;
    if (p === 'grep' || p === 'egrep' || p === 'fgrep') {
      const ignora = args.some((a) => (curta(a) && !a.startsWith('-e') && a.slice(1).includes('i')) || a === '--ignore-case');
      const padroes = args.filter((a) => (curta(a) && a.endsWith('e')) || a.startsWith('--regexp')).length;
      if (ignora && padroes >= 2) return UM_PADRAO;
    }
  }
  return null;
}

const CASOS = [
  // [comando, recusa?]
  ['ls -la s8', true],
  ['ls -l', true],
  ['ls -lh docs', true],
  ['ls docs -la', true],
  ['cd /c/dev/x && ls -la', true],
  ['find s7 -type f | xargs ls -la', true],
  ['find . -name x -ls', true],
  ['stat README.md', true],
  ['ls --format=long', true],
  ['grep -rn -i -e "a" -e "b" docs', true],
  ['grep -lni -e "deploy" -e "environment:" .github/workflows/*', true],
  ['ls -A _to_delete/ | grep -i -e "m32" -e "s6"', true],
  ['"/c/Program Files/Git/usr/bin/ls.exe" -la', true],
  ['/usr/bin/ls -la', true],
  ['ls -A s8/', false],
  ['ls -1 docs', false],
  ['ls --color=auto', false],
  ['git ls-files --others', false],
  ['stat -c \'%y  %n\' docs/x.md', false],
  ['grep -rn "ls -la\\|ls -l " CLAUDE.md .claude docs', false],
  ['grep -rn -i "a\\|b" docs', false],
  ['grep -rln -e "uma" -e "outra" docs/rodadas', false],
  ['grep -lni -e "runs-on" .github/workflows/*', false],
  ['grep -c "^17\\. " x.md', false],
  ['echo "ls -la" > nota.txt', false],
  ['node scripts/razao.mjs', false],
];

const direto = Boolean(process.argv[1]) && import.meta.url === pathToFileURL(process.argv[1]).href;

if (!direto) {
  // importado por outro arquivo: só o `veredito`
} else if (process.argv.includes('--prova')) {
  let erros = 0;
  for (const [c, recusa] of CASOS) {
    const ok = Boolean(veredito(c)) === recusa;
    if (!ok) erros++;
    console.log((ok ? 'ok   ' : 'ERRO ') + (recusa ? 'recusa  ' : 'passa   ') + c);
  }
  console.log(`${CASOS.length - erros} de ${CASOS.length} casos certos`);
  process.exit(erros ? 1 : 0);
} else {
  let entrada = '';
  process.stdin.setEncoding('utf8');
  process.stdin.on('data', (d) => (entrada += d));
  process.stdin.on('end', () => {
    let comando;
    try {
      const j = JSON.parse(entrada);
      if (j.tool_name !== 'Bash') process.exit(0);
      comando = String(j.tool_input?.command ?? '');
    } catch {
      process.exit(0);
    }
    const motivo = veredito(comando);
    if (motivo) {
      process.stderr.write(motivo + '\n');
      process.exit(2);
    }
    process.exit(0);
  });
}
