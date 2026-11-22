// M3-2 v2 — os documentos. Roda da raiz, DEPOIS do código e dos testes:
//   node m32/aplica-m32-docs.js DD/MM/AAAA
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
const novo088 = dir + '/ADR-088-a-cotacao-do-catalogo.md';
if (fs.existsSync(novo088)) throw new Error('a ADR-088 já existe');

const emenda = {
  '018': '## Emenda de ' + D + ', M3-2 — o caminho da cotação, e a nota de 07/10 respondida\n\n' +
    'A cotação ganhou código na M3-2, em `POST /internal/merchants/{estabelecimentoId}/catalog/quote`:\n' +
    'a loja sai do corpo e vai para a URL, como nas outras rotas (ADR-088). A nota de 07/10 acima diz\n' +
    'que *"é a rodada da cotação que a resolve"*: no marco 3 quem chama a cotação tem portador — o\n' +
    'atendente (ADR-086) —, e a contradição da ADR-054 segue armada na linha 48 do razão. O corpo fica\n' +
    'como está.\n',
  '024': '## Emenda de ' + D + ', M3-2 — o prazo chegou, e a decisão está confirmada\n\n' +
    'Esta ADR pediu que a pergunta do preço por modalidade fosse respondida antes de escrever o\n' +
    '`cotar`, e a emenda de 07/10 repetiu: *"Isso é folga, não dispensa"*. O `cotar` foi escrito na\n' +
    'M3-2, e o Rafael confirmou antes: **o preço não varia por modalidade, e o `cotar` não recebe\n' +
    'modalidade** (ADR-088, Decisão 3). A diferença entre entrega e retirada continua na taxa da área e\n' +
    'no desconto de retirada. O corpo fica como está.\n',
  '035': '## Emenda de ' + D + ', M3-2 — o caminho da cotação\n\n' +
    'A tabela acima diz, do `POST /internal/catalog/quote`, que o *"prefixo fica"* e que os campos do\n' +
    'payload viram domínio, *"`estabelecimentoId`, `produtoId`"*. O prefixo `/internal/` ficou; o\n' +
    'caminho é `/internal/merchants/{estabelecimentoId}/catalog/quote`, e o `estabelecimentoId` saiu\n' +
    'do corpo para a URL (ADR-088). Os campos do corpo nasceram em português: `itens`, `produtoId`,\n' +
    '`quantidade`, `opcoes`. O corpo fica como está.\n',
  '054': '## Emenda de ' + D + ', M3-2 — a rodada da cotação não pagou, e não precisava\n\n' +
    'O texto acima diz que a contradição entre a cotação sem portador e o `/internal/` que responde\n' +
    'sobre o portador *"é a rodada da cotação que paga"*. A cotação ganhou código na M3-2 (ADR-088), e\n' +
    '**no marco 3 quem a chama tem portador** — o atendente que lança o pedido (ADR-086). A\n' +
    'contradição não aparece aqui, e continua armada na linha 48 do razão. O corpo fica como está.\n',
  '068': '## Emenda de ' + D + ', M3-2 — a cotação da porta da ADR-018 tem código\n\n' +
    'O texto acima diz que o cardápio de quem compra entra *"pela porta da ADR-018\n' +
    '(`POST /internal/catalog/quote`), que não existe em código"*. A cotação dessa porta ganhou código\n' +
    'na M3-2 (ADR-088), em `/internal/merchants/{estabelecimentoId}/catalog/quote`. A leitura do\n' +
    'cardápio para o canal segue sem rota, e a linha 69 do razão segue esperando por ela. O corpo fica\n' +
    'como está.\n',
  '086': '## Emenda de ' + D + ', M3-2 — a fatia 2 decidida\n\n' +
    'A fatia 2 dizia *"`POST /internal/catalog/quote`"* e *"a permissão que a cotação exige, a rodada\n' +
    'da cotação decide"*. As duas ficaram decididas na ADR-088: o caminho é\n' +
    '`/internal/merchants/{estabelecimentoId}/catalog/quote`, e o vínculo ativo basta, sem permissão.\n' +
    'O corpo fica como está.\n',
};

const trocas = {
  [adr('018')]: [['**Status:** Aceita — 16/08/2026\n', '**Status:** Aceita — 16/08/2026 · **emendada em ' + D + '**: o caminho da cotação (M3-2, ADR-088)\n']],
  [adr('024')]: [['· **emendada em 16/10/2026**: o pedido mínimo existe desde a ADR-028\n', '· **emendada em 16/10/2026**: o pedido mínimo existe desde a ADR-028 · **emendada em ' + D + '**: o prazo chegou, e a decisão está confirmada (M3-2)\n']],
  [adr('035')]: [['**Status:** Aceita — 26/08/2026\n', '**Status:** Aceita — 26/08/2026 · **emendada em ' + D + '**: o caminho da cotação (M3-2)\n']],
  [adr('054')]: [['- **Status:** aceita\n', '- **Status:** aceita · **emendada em ' + D + '**: a rodada da cotação não pagou, e não precisava (M3-2)\n']],
  [adr('068')]: [['- **Status:** aceita\n', '- **Status:** aceita · **emendada em ' + D + '**: a cotação da porta da ADR-018 tem código (M3-2)\n']],
  [adr('086')]: [['· **emendada em 18/11/2026**: a fatia 1 são duas consultas (M3-1)\n', '· **emendada em 18/11/2026**: a fatia 1 são duas consultas (M3-1) · **emendada em ' + D + '**: a fatia 2 decidida (M3-2)\n']],
  [dir + '/README.md']: [
    ['| 018 | Snapshot de opções no item e cotação pelo catálogo | ✅ aceita |', '| 018 | Snapshot de opções no item e cotação pelo catálogo | ✅ aceita · **emendada** (' + D + ': o caminho da cotação — ADR-088) |'],
    ['(07/10/2026: o `cotar` é marco 3, e o prazo desta decisão vai com ele) |', '(07/10/2026: o `cotar` é marco 3, e o prazo desta decisão vai com ele) · **emendada** (' + D + ': o prazo chegou, e a decisão está confirmada) |'],
    ['| 035 | Domínio em português, o resto em inglês | ✅ aceita |', '| 035 | Domínio em português, o resto em inglês | ✅ aceita · **emendada** (' + D + ': o caminho da cotação) |'],
    ['| 054 | O gatilho da identidade de serviço não disparou | ✅ aceita · **emenda a 045** |', '| 054 | O gatilho da identidade de serviço não disparou | ✅ aceita · **emenda a 045** · **emendada** (' + D + ': a rodada da cotação não pagou, e não precisava) |'],
    ['| 068 | A listagem mostra o rascunho, porque quem a chama é o painel | ✅ aceita |', '| 068 | A listagem mostra o rascunho, porque quem a chama é o painel | ✅ aceita · **emendada** (' + D + ': a cotação da porta da ADR-018 tem código) |'],
    ['(18/11/2026: a fatia 1 são duas consultas — ADR-087) |', '(18/11/2026: a fatia 1 são duas consultas — ADR-087) · **emendada** (' + D + ': a fatia 2 decidida — ADR-088) |'],
    ['**Oitenta e quatro escritas, duas sem objeto', '**Oitenta e cinco escritas, duas sem objeto'],
    ['| 087 | A operação da loja e as condições do pedido, em duas consultas | ✅ aceita |\n', '| 087 | A operação da loja e as condições do pedido, em duas consultas | ✅ aceita |\n| 088 | A cotação do catálogo: a loja na URL, o vínculo basta, e o 409 traz tudo o que caiu | ✅ aceita |\n'],
  ],
  'docs/dominio/catalogo.md': [
    ['Contrato e justificativa na ADR-018. O que este documento fixa é o\ncomportamento de domínio.\n',
     'Contrato e justificativa na ADR-018 e na ADR-088, que pôs a loja na URL —\n`POST /internal/merchants/{estabelecimentoId}/catalog/quote`. O que este documento fixa é o\ncomportamento de domínio.\n'],
    ['| Opção não pertence ao produto | **400** | Idem |\n', '| Opção não pertence ao produto | **400** | Idem |\n| A mesma opção duas vezes no mesmo item | **400** | O modelo não tem quantidade por opção (ADR-088) |\n'],
    ['| Produto de outro estabelecimento | **409** | ADR-004 — um estabelecimento por pedido |\n', '| Produto de outro estabelecimento | **409** | ADR-004 — um estabelecimento por pedido |\n| Produto inexistente | **409** | Igual ao de outra loja: as duas respondem o mesmo (M7, ADR-088) |\n'],
    ['texto:** C7, C8 e C9 pela forma do agregado, sem regra própria; C10 na\n> cotação (G-C).\n', 'texto:** C7, C8 e C9 pela forma do agregado, sem regra própria. C10 é\n> cobrada na cotação, pelo `CotarItensService` (M3-2, ADR-088).\n'],
    ['`conversation`, pela porta da ADR-018, que não existe em código — e quando existir, entra por\nela.', '`conversation`, pela porta da ADR-018, cuja cotação ganhou código na M3-2 (ADR-088); a leitura do\ncardápio para o canal segue sem rota, e entra por essa porta.'],
  ],
  'docs/front/premissas-do-front.md': [
    ['A cotação está desenhada na\nADR-018 (`POST /internal/catalog/quote`), **ainda não existe em código**, e é\ninterna', 'A cotação é a da\nADR-088 (`POST /internal/merchants/{estabelecimentoId}/catalog/quote`), e é\ninterna'],
  ],
  'docs/dividas.md': [
    ['`ExpedienteCorrenteResponse.java`:15 — foram consertadas no lugar.', '`ExpedienteCorrenteResponse.java`:15 — foram consertadas no lugar. Na M3-2, **28**: a cotação ganhou código (ADR-088), e a frase do `premissas-do-front.md`:140 que dizia o contrário foi consertada no lugar.'],
    ['| o recorte estreito devolve | **111** |', '| o recorte estreito devolve | **112** |'],
    ['e na M3-1, sobre `d4fb2ec` mais a ADR-087. Este arquivo é', 'e na M3-1, sobre `d4fb2ec` mais a ADR-087, e na M3-2, sobre `b275220` mais a ADR-088. Este arquivo é'],
    ['relato de um que disparou. A conta, em 18/11/2026, na M3-1:', 'relato de um que disparou. A conta, em ' + D + ', na M3-2:'],
    ['e, na S-1, a ADR-084, *"Nenhum."*) | −14 |', 'e, na S-1, a ADR-084, *"Nenhum."*; e, na M3-2, a ADR-088, *"Nenhum."*) | −15 |'],
    ['| as duas rotas `/internal/` do `merchant` caem no `anyRequest().authenticated()` do `SecurityConfig` dele |', '| as rotas `/internal/` — quatro no `merchant` e a cotação no `catalog` — caem no `anyRequest().authenticated()` do `SecurityConfig` de cada um |'],
    ['| `POST /internal/catalog/quote` não existe em código (E-3d) **Reverifica com:** `grep -rn "catalog/quote" backend --include=*.java` — medido em 18/10/2026, `432c8e9`: só um javadoc do `merchant` a cita. |',
     '| a cotação da porta da ADR-018 ganhou código na M3-2 (ADR-088); a leitura do cardápio para o canal segue sem rota. **Reverifica com:** `grep -rn \'Mapping("/internal\' backend/services/catalog-service/src/main` — medido na M3-2: só o `CotacaoController` |'],
  ],
  '.claude/skills/novo-caso-de-uso/SKILL.md': [
    ['- **Recurso de outra loja é 404, com o mesmo corpo do inexistente** — nunca 403, que vazaria a\n  existência (ADR-056).',
     '- **Recurso de outra loja cujo identificador vem na URL é 404, com o mesmo corpo do inexistente** —\n  nunca 403, que vazaria a existência (ADR-056, linhas 91 e 92). **Identificador que vem no corpo**\n  segue o documento do agregado — a categoria do produto é 400 (`catalogo.md` §1), o produto da\n  cotação é 409 (`catalogo.md` §5, ADR-088) —, sempre com a mesma resposta para inexistente e para\n  de outra loja (M7).'],
    ['**404 para recurso de outra loja**;', '**404 para recurso de outra loja, com o id na URL**;'],
    ['da loja B → **404, com o mesmo corpo do inexistente** (ADR-056). Exemplo:',
     'da loja B pelo id na URL → **404, com o mesmo corpo do inexistente** (ADR-056); com o id no corpo,\nvale o documento do agregado, e a mesma resposta para inexistente e para de outra loja (M7). Exemplo:'],
    ['- [ ] Teste de isolamento entre estabelecimentos, com **404**',
     '- [ ] Teste de isolamento entre estabelecimentos: **404** para id na URL; para id no corpo, o que o\n  documento do agregado diz'],
  ],
  'docs/marcos/marco-3.md': [
    ['Fatia 1 medida na M3-1, sobre `d4fb2ec` (ADR-087).', 'Fatia 1 medida na M3-1, sobre `d4fb2ec` (ADR-087). Fatia 2 medida na M3-2, sobre `b275220` (ADR-088).'],
    ['nenhum completo; a fatia 1 é peça em 5 deles.', 'nenhum completo; a fatia 1 é peça em 5 deles, e a fatia 2 em 2.'],
    ['| 1 | — | — | peça (fatia 1): `CondicoesDoPedidoControllerIT`, casos 6 a 9 — a área atendida — e 5 — a regra do troco |', '| 1 · 2 | — | — | peça (fatia 1): `CondicoesDoPedidoControllerIT`, casos 6 a 9 — a área atendida — e 5 — a regra do troco · peça (fatia 2): `CotacaoControllerIT`, casos 9 a 14 — os itens vendáveis |'],
    ['| 1 | — | — | peça (fatia 1): `CondicoesDoPedidoControllerIT`, casos 5, 6 e 10 — a taxa e o desconto |', '| 1 · 2 | — | — | peça (fatia 1): `CondicoesDoPedidoControllerIT`, casos 5, 6 e 10 — a taxa e o desconto · peça (fatia 2): `CotacaoControllerIT`, casos 1 a 3 — os itens, com as opções e os preços |'],
  ],
};

const saida = {};
for (const [f, lista] of Object.entries(trocas)) {
  let s = fs.readFileSync(f, 'utf8');
  if (s.includes('\r\n')) throw new Error(f + ': CRLF');
  for (const [a, b] of lista) {
    const n = s.split(a).length - 1;
    if (n !== 1) throw new Error(`${f}: "${a.slice(0, 70)}" ocorre ${n} vez(es)`);
    s = s.replace(a, () => b);
  }
  for (const [n, texto] of Object.entries(emenda)) if (f === adr(n)) s = s.replace(/\n*$/, '\n') + '\n' + texto;
  saida[f] = s;
}
const adr088 = fs.readFileSync('m32/adr/ADR-088-a-cotacao-do-catalogo.md', 'utf8').replace('<<DATA>>', D);
if (adr088.includes('<<')) throw new Error('ADR-088 com marcador');
for (const [f, s] of Object.entries(saida)) fs.writeFileSync(f, s);
fs.writeFileSync(novo088, adr088);
console.log('ok:', Object.keys(saida).length + 1, 'arquivos,',
  Object.values(trocas).reduce((n, l) => n + l.length, 0), 'trocas,', Object.keys(emenda).length, 'emendas e a ADR-088');
