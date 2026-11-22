// V-1 — os documentos. Roda da raiz, DEPOIS do código e do lint verde:
//   node v1/aplica-v1-docs.js DD/MM/AAAA
const fs = require('fs');
const D = process.argv[2];
if (!/^\d{2}\/\d{2}\/\d{4}$/.test(D || '')) throw new Error('passe a data do `date +%d/%m/%Y`');
const dir = 'docs/architecture/decisions';
const l = fs.readdirSync(dir).filter((f) => f.startsWith('ADR-039-')).map((f) => dir + '/' + f);
if (l.length !== 1) throw new Error('ADR-039: ' + l.length);
const adr039 = l[0];

const trocas = {
  [adr039]: [
    ['**Status:** Aceita — 08/09/2026\n', '**Status:** Aceita — 08/09/2026 · **emendada em ' + D + '**: o validador do CI tem configuração, e passa (V-1)\n'],
  ],
  [dir + '/README.md']: [
    ['(06/10/2026: a lista de erros de cada rota é afirmada, e o `ContratoDeErrosIT` a prova) |',
     '(06/10/2026: a lista de erros de cada rota é afirmada, e o `ContratoDeErrosIT` a prova) · **emendada** (' + D + ': o validador do CI tem configuração, e passa) |'],
  ],
  'contracts/README.md': [
    ['  compara o documento gerado com o arquivo commitado e falha se divergirem\n  (ADR-039)\n',
     '  compara o documento gerado com o arquivo commitado e falha se divergirem\n  (ADR-039); e o workflow `contracts` os valida pelo redocly, com as regras do\n  `redocly.yaml` da raiz, cada exceção com o motivo escrito (ADR-039, emenda de ' + D + ')\n'],
  ],
  'docs/dividas.md': [
    ['| O `EstadoDePublicacao` é máquina de estados e não tem tabela de transições, que o `CLAUDE.md` (Convenções de código) manda ter. O `pedido.md`:14 registra o mesmo descompasso no pedido | R-11; E-8 |\n',
     '| O `EstadoDePublicacao` é máquina de estados e não tem tabela de transições, que o `CLAUDE.md` (Convenções de código) manda ter. O `pedido.md`:14 registra o mesmo descompasso no pedido | R-11; E-8 |\n' +
     '| O `identity` não declara as recusas das quatro rotas públicas — é anterior à ADR-053, e não tem `ContratoDeErrosIT`. O redocly as acusa como aviso (`operation-4xx-response`), e o JWKS não tem recusa a declarar | V-1; `redocly.yaml` |\n'],
  ],
};
const emenda =
  '\n## Emenda de ' + D + ', V-1 — o validador do CI tem configuração, e passa\n\n' +
  'O workflow `contracts` nasceu em 29/09/2026 para rodar as ferramentas que o `package.json`\n' +
  'declarava sem nunca executar, e rodava o redocly sem configuração, com as regras padrão: **falhou\n' +
  'nas 24 execuções que teve, de 29/09 a 22/11/2026 — nunca passou**. A V-1 mediu cada regra e decidiu, uma por\n' +
  'uma: o contrato foi consertado onde estava incompleto — o resumo das quatro rotas do `identity`, a\n' +
  'segurança pública declarada nelas, a licença nos três serviços —, e o `redocly.yaml` da raiz\n' +
  'escreve as duas exceções, cada uma com o motivo. O corpo fica como está.\n';

const saida = {};
for (const [f, lista] of Object.entries(trocas)) {
  let s = fs.readFileSync(f, 'utf8');
  if (s.includes('\r\n')) throw new Error(f + ': CRLF');
  for (const [a, b] of lista) {
    const n = s.split(a).length - 1;
    if (n !== 1) throw new Error(`${f}: "${a.slice(0, 60)}" ocorre ${n} vez(es)`);
    s = s.replace(a, () => b);
  }
  if (f === adr039) s = s.replace(/\n*$/, '\n') + emenda;
  saida[f] = s;
}
for (const [f, s] of Object.entries(saida)) fs.writeFileSync(f, s);
console.log('ok: 4 arquivos, 4 trocas e 1 emenda');
