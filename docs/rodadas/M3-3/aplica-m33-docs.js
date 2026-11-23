// M3-3 — os documentos. Roda da raiz, DEPOIS do código e dos testes verdes:
//   node m33/aplica-m33-docs.js DD/MM/AAAA
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

const emenda = {
  '011': '\n## Emenda de ' + D + ', M3-3 — a LANCAR_PEDIDO, e a dívida de 19/10 paga\n\n' +
    'A H4.5 pede *"uma permissão própria, que nenhuma das permissões de H2.1 cobre"*: nasce a\n' +
    '**`LANCAR_PEDIDO`**. O fundador a recebe com as outras, em `Membro.fundador`, e a tela de convite\n' +
    'a oferece. **E ela alcança quem já existe** — o que a `CONFIGURAR_LOJA` não fez (a emenda de\n' +
    '19/10): a migration `V9` dá a `LANCAR_PEDIDO` **e a `CONFIGURAR_LOJA` que faltava** a todo membro\n' +
    'que tem as dez permissões anteriores à E-4 — os fundadores, que receberam o conjunto inteiro na\n' +
    'criação. **O promovido a administrador não entra:** papel não é lista de permissões\n' +
    '(`estabelecimento.md` §2), e a promoção nunca deu permissão. Decisão do Rafael. O corpo fica como\n' +
    'está.\n',
  '086': '\n## Emenda de ' + D + ', M3-3 — a fatia 3 decidida\n\n' +
    'A fatia 3 dizia que o valor novo do `Permissao` *"o `ADMINISTRADOR` recebe por `EnumSet.allOf`"* e\n' +
    'que *"a rodada da permissão decide como a nova alcança"*. Quem recebe o conjunto inteiro é o\n' +
    '**fundador**, na criação; e a nova, `LANCAR_PEDIDO`, alcança os fundadores que já existem pela\n' +
    'migration `V9`, junto com a `CONFIGURAR_LOJA` que faltava (ADR-011, emenda desta data). O corpo\n' +
    'fica como está.\n',
};

const trocas = {
  [adr('011')]: [
    ['**emendada em 19/10/2026**: a permissão não alcança quem já era membro\n',
     '**emendada em 19/10/2026**: a permissão não alcança quem já era membro · **emendada em ' + D + '**:\na `LANCAR_PEDIDO`, e a dívida de 19/10 paga (M3-3)\n'],
  ],
  [adr('086')]: [
    ['· **emendada em 22/11/2026**: a fatia 2 decidida (M3-2)\n',
     '· **emendada em 22/11/2026**: a fatia 2 decidida (M3-2) · **emendada em ' + D + '**: a fatia 3 decidida (M3-3)\n'],
  ],
  [dir + '/README.md']: [
    ['19/10/2026: a permissão não alcança quem já era membro) |',
     '19/10/2026: a permissão não alcança quem já era membro; ' + D + ': a `LANCAR_PEDIDO`, e a dívida de 19/10 paga) |'],
    ['· **emendada** (22/11/2026: a fatia 2 decidida — ADR-088) |',
     '· **emendada** (22/11/2026: a fatia 2 decidida — ADR-088) · **emendada** (' + D + ': a fatia 3 decidida) |'],
  ],
  'docs/dominio/estabelecimento.md': [
    ['Exatamente as de H2.1, mais duas:\n', 'Exatamente as de H2.1, mais três:\n'],
    ['GERENCIAR_JORNADA\nCONFIGURAR_LOJA\n```\n', 'GERENCIAR_JORNADA\nCONFIGURAR_LOJA\nLANCAR_PEDIDO\n```\n'],
    ['ganhar rota. O `ADMINISTRADOR` a recebe com todas as outras, e **a tela de convite não a\n> oferece**',
     'ganhar rota. O **fundador** a recebe com todas as outras, na criação, e **a tela de convite não a\n> oferece**'],
    ['> **`GERENCIAR_JORNADA` é acréscimo ao PRD**',
     '> **`LANCAR_PEDIDO` nasce da H4.5**, que pede *"uma permissão própria, que nenhuma das permissões\n' +
     '> de H2.1 cobre"* (M3-3, ADR-011 emenda de ' + D + '). O fundador a recebe com as outras, e **a tela\n' +
     '> de convite a oferece**: é para o dono dá-la ao atendente do balcão. Quem a exige é a rota de lançar\n' +
     '> do `order`, na fatia 4 da ADR-086.\n' +
     '>\n' +
     '> **Quem já existia, e a migration `V9`.** As permissões ficam gravadas por membro, e o fundador só\n' +
     '> ganha o conjunto inteiro na criação. A `V9` dá a `LANCAR_PEDIDO` e a `CONFIGURAR_LOJA` a todo\n' +
     '> membro com as dez permissões anteriores à E-4 — os fundadores. O promovido a administrador não\n' +
     '> entra: papel não é lista de permissões.\n\n' +
     '> **`GERENCIAR_JORNADA` é acréscimo ao PRD**'],
  ],
  'docs/marcos/marco-3.md': [
    ['Fatia 2 medida na M3-2, sobre `b275220` (ADR-088).', 'Fatia 2 medida na M3-2, sobre `b275220` (ADR-088). Fatia 3 medida na M3-3, sobre `<<BASE>>` (ADR-011, emenda; ADR-086, emenda).'],
    ['a fatia 1 é peça em 5 deles, e a fatia 2 em 2.', 'a fatia 1 é peça em 5 deles, a fatia 2 em 2, e a fatia 3 em 1.'],
    ['| 4.5f | Lançar exige uma permissão própria, que nenhuma das permissões de H2.1 cobre | | — | — | — | — |',
     '| 4.5f | Lançar exige uma permissão própria, que nenhuma das permissões de H2.1 cobre | 3 | — | — | peça (fatia 3): `PermissaoDeLancarIT`, casos 1 a 5 — a permissão existe, o fundador a recebe, o convite a dá, a `V9` alcança quem já existia; e `ConvidarParaEquipe.test.tsx`, caso 3 — a tela a oferece | — |'],
  ],
};

const base = process.argv[3];
if (!/^[0-9a-f]{7}$/.test(base || '')) throw new Error('passe também o commit de partida, 7 caracteres');
const saida = {};
for (const [f, lista] of Object.entries(trocas)) {
  let s = fs.readFileSync(f, 'utf8');
  if (s.includes('\r\n')) throw new Error(f + ': CRLF');
  for (const [a, b0] of lista) {
    const b = b0.replace('<<BASE>>', base);
    const n = s.split(a).length - 1;
    if (n !== 1) throw new Error(`${f}: "${a.slice(0, 60)}" ocorre ${n} vez(es)`);
    s = s.replace(a, () => b);
  }
  for (const [n, texto] of Object.entries(emenda)) if (f === adr(n)) s = s.replace(/\n*$/, '\n') + texto;
  saida[f] = s;
}
for (const [f, s] of Object.entries(saida)) fs.writeFileSync(f, s);
console.log('ok:', Object.keys(saida).length, 'arquivos,',
  Object.values(trocas).reduce((n, l) => n + l.length, 0), 'trocas e', Object.keys(emenda).length, 'emendas');
