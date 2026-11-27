// A-1 — as tabelas dos marcos 1 e 2, pelo que o auditor achou. Roda da raiz, DEPOIS dos testes
// verdes: node a1/aplica-a1-docs.js
// Cada trecho antigo tem de ocorrer exatamente uma vez; se um falhar, nada é escrito.
const fs = require('fs');
const CAB = 'teste medido na S-3, sobre `3ff45f8`;';
const CAB_NOVO = 'teste medido na S-3, sobre `3ff45f8`, e conferido pelo `auditor-de-marco` na A-1;';

const trocas = {
  'docs/marcos/marco-1.md': [
    [CAB, CAB_NOVO],
    ['17 com teste, 3 deles\nem parte;', '17 com teste, 4 deles\nem parte;'],
    ['| `OperacaoTest` — "nova_com_mapa_vazio_ainda_e_recusada_pela_M12"; `CadastroDeLojaPage.test.tsx` — "A7 · a tela não antecipa a M12: matriz vazia, botão habilitado, e a requisição acontece" |',
     '| `CriarEstabelecimentoServiceTest` — "caso1_cria_a_loja_com_os_valores_do_pedido" (os métodos de `ENTREGA` gravados) e "caso3_minimos_e_desconto_nascem_zero_e_as_chaves_sao_as_dos_metodos" (as duas modalidades na loja salva); `CadastroDeLojaPage.test.tsx` — "A3 · enviar manda exatamente o corpo do contrato" (as duas modalidades no corpo). *Os dois citados antes — o `OperacaoTest` da M12 e o A7 — afirmam só a recusa do mapa vazio (A-1)* |'],
    ['| `PedidoMinimoEDescontoIT` — "2 · zero vale como "sem mínimo": entra, fica, volta na leitura — e omitir não é zero"; `ConfiguracaoDaLoja.test.tsx` — "2 · o mínimo zero aparece como "sem mínimo", e não como R$ 0,00 (M17)" |',
     '| em parte: `PedidoMinimoEDescontoIT` — "2 · zero vale como "sem mínimo": entra, fica, volta na leitura — e omitir não é zero"; `ConfiguracaoDaLoja.test.tsx` — "2 · o mínimo zero aparece como "sem mínimo", e não como R$ 0,00 (M17)". A metade *"sobre o valor dos itens — sem a taxa de entrega"* não tem teste, porque não existe pedido: quem a prova é o Verificável da H4.5 (`PRD.md`:204), no marco 3 (A-1) |'],
    ['| 1.3c | Taxa zero é valor válido e distinto de área não atendida | a mesma das áreas |',
     '| 1.3c | Taxa zero é valor válido e distinto de área não atendida | a mesma das áreas, e `POST /internal/merchants/{estabelecimentoId}/condicoes-do-pedido` |'],
    ['"3 · bairro que não está na lista não aparece com taxa zero — ausência não é zero (M11)" | — |',
     '"3 · bairro que não está na lista não aparece com taxa zero — ausência não é zero (M11)"; `CondicoesDoPedidoControllerIT` — "8 · bairro não atendido: 200, atendida falso, sem taxa (M11), e os bairros atendidos (ADR-020)" e "10 · área com taxa zero: atendida, e a taxa é zero — entrega grátis não é área ausente" | — |'],
  ],
  'docs/marcos/marco-2.md': [
    [CAB, CAB_NOVO],
    ['5 com teste, 1 deles em\nparte;', '5 com teste;'],
    ['`ProdutoAberto.test.tsx` — "M1 · produto em rascunho: criar grupo manda o POST com a ordem derivada, e relê" | — |',
     '`ProdutoAberto.test.tsx` — "M1 · produto em rascunho: criar grupo manda o POST com a ordem derivada, e relê"; e as remoções: `MontagemDeOpcoesIT` — "remoções: grupo opcional com opções de acréscimo zero, pela rota — e o banco guarda o zero" (A-1) | — |'],
    ['| em parte: `MarcacaoDeDisponibilidadeIT` — "ESGOTADO_HOJE: 200, o corpo traz o estado novo, e o banco guarda o carimbo do merchant"; `MarcarDisponibilidade.test.tsx` — "2 · o item é substituído pelo produto da resposta — inteiro, com o vendavel recalculado" (acabando). Marcar disponível com sucesso: nenhum | 19/10, itens 13 e 15,',
     '| `MarcacaoDeDisponibilidadeIT` — "ESGOTADO_HOJE: 200, o corpo traz o estado novo, e o banco guarda o carimbo do merchant", "loja sem horário + ESGOTADO_INDETERMINADO: 200, e sem carimbo" e "DISPONIVEL depois de ESGOTADO_HOJE: 200, e o produto volta a ser vendável" (A-1); `MarcarDisponibilidade.test.tsx` — "2 · o item é substituído pelo produto da resposta — inteiro, com o vendavel recalculado" (acabando); `DisponibilidadeTest` — "qualitativo_aceita_os_quatro", que passa a afirmar os quatro (A-1) | 19/10, itens 14 e 15,'],
  ],
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
  saida[f] = s;
}
for (const [f, s] of Object.entries(saida)) fs.writeFileSync(f, s);
console.log('ok:', Object.keys(saida).length, 'arquivos,',
  Object.values(trocas).reduce((n, l) => n + l.length, 0), 'trocas');
