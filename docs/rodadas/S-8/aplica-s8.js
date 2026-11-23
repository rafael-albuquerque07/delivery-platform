// S-8 v2 — roda da raiz do repositório, DEPOIS da S-7: node s8/aplica-s8.js DD/MM/AAAA
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
    ['⚠ **Nunca no relatório:** linha do `.env` que o pacote não nomeou, token, senha, nem caminho com o\n' +
     'nome de usuário do Windows.\n',
     '⚠ **Nunca no relatório:** linha do `.env` que o pacote não nomeou, token, senha, nem caminho com o\n' +
     'nome de usuário do Windows. **E nenhum `ls -l` nem `ls -la`**, nem para olhar uma pasta por conta\n' +
     'própria: a coluna do dono é o nome de usuário da máquina. Use `ls -A` (regra 11 do\n' +
     '`escrever-rodada.md`; na S-6 e na S-7, o nome saiu no relatório por essa coluna).\n\n' +
     '⚠ **No Git Bash, `grep -i` com mais de um `-e` aborta** (saída 134), e o que sai é o número do\n' +
     'erro, não a conta. Junte as alternativas num padrão só: `grep -i "uma\\|outra"` (regra 17 do\n' +
     '`escrever-rodada.md`). Se um pacote trouxer a forma que aborta, rode a forma de um padrão só e\n' +
     'diga no relatório. E busca sua que percorra `docs` leva `--exclude-dir=rodadas`.\n'],
  ],
  [S + 'escrever-rodada.md']: [
    ['    lendo o que cada caso afirma, não o título dele: dois casos que afirmam a mesma coisa caem juntos.\n',
     '    lendo o que cada caso afirma, não o título dele: dois casos que afirmam a mesma coisa caem juntos.\n' +
     '17. **A busca do pacote roda no Git Bash, e fora de `docs/rodadas/`.** `grep -i` leva um padrão\n' +
     '    só: com mais de um `-e`, ele aborta no Git Bash do Rafael (saída 134), e a conta prevista vira\n' +
     '    o número do erro. As alternativas vão num padrão, com `\\|` — `grep -rn -i "imprim\\|impress"`;\n' +
     '    em célula de tabela, onde `|` não entra (regra 14), tire o `-i` e liste as grafias com `-e`.\n' +
     '    E toda busca que percorre `docs` leva `--exclude-dir=rodadas`: os pacotes guardados lá citam as\n' +
     '    frases antigas, e a busca da regra 15 as acharia.\n' +
     '18. **Pacote reenviado muda de nome, e o §0 confere qual está na pasta.** Uma versão nova é\n' +
     '    `APLICAR-<rodada>-v2-…`, e o §0 lista os `APLICAR-` da pasta e diz o nome inteiro desta versão:\n' +
     '    se sair outro, ou dois, quem aplica para. Na S-7, a versão reenviada tinha o mesmo nome da primeira, nada no §0\n' +
     '    as distinguia, e a primeira foi aplicada.\n'],
  ],
  '.claude/agents/revisor-de-pacote.md': [
    ['    casos e confira se dois deles afirmam a mesma coisa.\n',
     '    casos e confira se dois deles afirmam a mesma coisa.\n' +
     '21. **A busca** (regra 17). Algum comando junta `-i` com mais de um `-e`? Alguma busca percorre\n' +
     '    `docs` sem `--exclude-dir=rodadas`? Cada um é achado, com a forma certa ao lado.\n' +
     '22. **A versão na pasta** (regra 18). Se o pacote é `-v2` ou adiante: o §0 lista os `APLICAR-` da\n' +
     '    pasta, diz o nome inteiro desta versão, e manda parar se sair outro?\n'],
  ],
  'contracts/README.md': [
    ['    grep -rhoE "[A-Z][A-Za-z]+V[0-9]" docs/ contracts/ | sort | uniq -c\n',
     '    grep -rhoE "[A-Z][A-Za-z]+V[0-9]" docs/ contracts/ --exclude-dir=rodadas | sort | uniq -c\n\n' +
     'O `--exclude-dir=rodadas` tira os pacotes aplicados guardados em `docs/rodadas/`, que citam\n' +
     'nomes de evento como texto (S-8).\n'],
  ],
  'docs/dividas.md': [
    ['`grep -lni -e "deploy" -e "environment:" .github/workflows/*`',
     '`grep -ln -e "deploy" -e "Deploy" -e "environment:" .github/workflows/*`', 2],
    ['`grep -rn -i -e "RequestRateLimiter" -e "ratelimit" -e "rate-limit" backend/infra/gateway/src/main`',
     '`grep -rn -i "rate.\\?limit" backend/infra/gateway/src/main` (S-8: sem `-i` com dois `-e`, medido igual)'],
    ['`grep -rln -i -e "canal" -e "whatsapp" backend/services/*/src/main/resources/db/migration`',
     '`grep -rln -e "canal" -e "Canal" -e "whatsapp" -e "WhatsApp" backend/services/*/src/main/resources/db/migration` (S-8: sem `-i` com dois `-e`, medido igual)'],
    ['`grep -rln -i -e "pitest" -e "stryker" backend/build-logic frontend/package.json .github`',
     '`grep -rln -e "pitest" -e "Pitest" -e "stryker" -e "Stryker" backend/build-logic frontend/package.json .github` (S-8: sem `-i` com dois `-e`, medido igual)'],
  ],
  'docs/front/premissas-do-front.md': [
    ['| O **estado da operação** — a loja está aberta agora? | O seletor não pode mostrar aberta/fechada. A `OperacaoDoEstabelecimentoPort` do `estabelecimento.md` §3 não existe em código | sem rodada marcada — a porta não existe (ADR-046, emenda de 30/09/2026), e a rota da G-C1 responde outra pergunta: qual expediente carimbar |\n',
     '| O **estado da operação** — a loja está aberta agora? | O seletor não pode mostrar aberta/fechada. A resposta existe desde a M3-1, mas só para serviço: `GET /internal/merchants/{estabelecimentoId}/operacao` (ADR-087), e o gateway não roteia `/internal/` | sem rodada marcada para uma rota pública: a primeira tela que mostre aberta/fechada pede uma, ou o campo na lista de lojas do portador |\n'],
  ],
  '.gitignore': [
    ['# Para onde vai o pacote aplicado: o agente move, não apaga. Sem esta linha,\n',
     '# A pasta original do pacote, depois de copiada para docs/rodadas/: o agente move, não apaga. Sem esta linha,\n'],
  ],
  [adr('084')]: [
    [': o pacote aplicado fica no repositório (S-7)\n',
     ': o pacote aplicado fica no repositório (S-7) · **emendada em ' + D + '**: a busca no Git Bash e fora das rodadas, e a versão no §0 (S-8)\n'],
  ],
  [dir + '/README.md']: [
    ['· **emendada** (22/11/2026: o pacote aplicado fica no repositório) |',
     '· **emendada** (22/11/2026: o pacote aplicado fica no repositório) · **emendada** (' + D + ': a busca no Git Bash e fora das rodadas, e a versão no §0) |'],
  ],
};

const aoFim = {
  [adr('084')]:
    '\n## Emenda de ' + D + ', S-8 — a busca no Git Bash e fora das rodadas, e a versão do pacote no §0\n\n' +
    'Duas regras novas no `escrever-rodada.md`, cada uma com o item do `revisor-de-pacote` que a\n' +
    'confere. **A 17:** no Git Bash do Rafael, `grep -i` com mais de um `-e` aborta (saída 134) —\n' +
    'duas vezes na S-7 —, e as alternativas passam a ir num padrão só, com `\\|`; e toda busca em\n' +
    '`docs` leva `--exclude-dir=rodadas`, porque os pacotes guardados citam as frases antigas. **A 18:** pacote\n' +
    'reenviado muda de nome, e o §0 confere a versão — na S-7, a versão reenviada tinha o nome da\n' +
    'primeira, e a primeira foi aplicada, sem a linha do `ls -l` no `aplicar-rodada.md`. Essa linha\n' +
    'entra agora, com a do `grep -i`, no "Nunca no relatório", que é o que quem aplica lê. O corpo\n' +
    'fica como está.\n',
};

const saida = {};
for (const [f, lista] of Object.entries(trocas)) {
  let s = fs.readFileSync(f, 'utf8');
  if (s.includes('\r\n')) throw new Error(f + ': CRLF');
  for (const [a, b, vezes = 1] of lista) {
    const n = s.split(a).length - 1;
    if (n !== vezes) throw new Error(`${f}: "${a.slice(0, 60)}" ocorre ${n} vez(es), e devia ${vezes}`);
    s = s.split(a).join(b);
  }
  if (aoFim[f]) s = s.replace(/\n*$/, '\n') + aoFim[f];
  saida[f] = s;
}
for (const [f, s] of Object.entries(saida)) fs.writeFileSync(f, s);
console.log('ok:', Object.keys(saida).length, 'arquivos,',
  Object.values(trocas).reduce((n, l) => n + l.length, 0), 'trocas,', Object.keys(aoFim).length, 'emenda');
