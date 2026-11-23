import fs from 'node:fs';
const P = 'docs/propostas/o-que-falta-para-operar-como-saas.md';
const D = 'docs/dividas.md';
const prop = fs.readFileSync(P, 'utf8');
const sec = prop.split('## Gatilho escrito')[1];
const gat = sec.trim().split(/\n\s*\n/).map(p => p.replace(/\s*\n\s*/g, ' ').trim());
if (gat.length !== 5) throw new Error('gatilhos: ' + gat.length);
// aspas internas em itálico não cabem dentro de uma citação em itálico
const q = g => '*"' + g.replace(/\*"([^"]*)"\*/g, '"$1"') + '"*';
const fonte = '`docs/propostas/o-que-falta-para-operar-como-saas.md`, seção "Gatilho escrito"';
const linhas = [
  [133, gat[0], 'nenhum workflow implanta. **Reverifica com:** `grep -ln -e "deploy" -e "Deploy" -e "environment:" .github/workflows/*` — medido em 23/11/2026, na R-17: vazio; com `-e "runs-on"`, acha. A condição de partida é a da linha 16, que pede duas defesas do gateway; esta pede a decisão do ambiente', 'onde o produto roda, numa ADR'],
  [134, gat[1], 'nenhuma ADR decide o cofre. **Reverifica com:** `grep -l "^# .*[Cc]ofre" docs/architecture/decisions/*.md` — medido em 23/11/2026, na R-17: vazio; com `"^# .*[Rr]etenção"`, acha a ADR-013', 'o cofre, a partir do token do canal, e o que acontece com o certificado e a credencial do PSP'],
  [135, gat[2], 'o procedimento serve a todos os direitos, e acesso e portabilidade cabem numa frase, sem passos; nada registra um lojista pagante. A saga da exclusão espera, na linha 84, o primeiro pedido de titular. **Reverifica com:** `grep -c -i "^#.*acesso" docs/operacao/exclusao-de-titular.md` — medido em 23/11/2026, na R-17: `0`; com `"^#.*exclus"`, acha um', 'os passos de acesso e portabilidade, no `exclusao-de-titular.md`'],
  [136, gat[3], 'nenhum documento decide a loja que não pagou nem a que cancelou. **Reverifica com:** `grep -rln -i "inadimpl" docs/architecture docs/dominio docs/PRD.md` — medido em 23/11/2026, na R-17: vazio; `grep -c "Cobrança da assinatura" docs/PRD.md` acha um', 'o ciclo da assinatura, numa ADR'],
  [137, gat[4], 'a restauração só existe para o banco inteiro. **Reverifica com:** `grep -rn -i "restaura" docs/architecture/decisions docs/operacao` — medido em 23/11/2026, na R-17: ADR-013:175, `exclusao-de-titular.md`:57 e `revisao-juridica.md`:212, todos do banco inteiro, e ADR-050:96 e ADR-069:119, sem relação', 'voltar uma loja só, na ADR do ambiente'],
].map(([n, g, a, b]) => {
  const l = `| ${n} | ${fonte} | ${q(g)} | ${a} | ${b} |`;
  if ((l.match(/\|/g) || []).length !== 6) throw new Error('pipe na linha ' + n);
  return l;
});
let d = fs.readFileSync(D, 'utf8');
const troca = (de, para) => {
  const n = d.split(de).length - 1;
  if (n !== 1) throw new Error(`ocorrências ${n}: ${de.slice(0, 60)}`);
  d = d.replace(de, para);
};
const ult = d.split('\n').find(l => l.startsWith('| 132 | ADR-087'));
troca(ult + '\n', ult + '\n' + linhas.join('\n') + '\n');
troca('e na M3-2, sobre `b275220` mais a ADR-088', 'e na M3-2, sobre `b275220` mais a ADR-088, e na R-17, sobre `4819195` mais a proposta do que falta para operar como SaaS');
troca('A conta, em 22/11/2026, na M3-2:', 'A conta, em 23/11/2026, na R-17:');
troca('| o recorte estreito devolve | **112** |', '| o recorte estreito devolve | **113** |');
troca('| **= linhas da tabela vindas do recorte estreito** | **57** |', '| **= linhas da tabela vindas do recorte estreito** | **58** |');
troca('ADR-087 — linha 132) | **+43** |', 'ADR-087 — linha 132; e, na R-17, a proposta do SaaS — linhas 134 a 137) | **+47** |');
troca('| **= linhas da tabela que contam** | **129** |', '| **= linhas da tabela que contam** | **134** |');
troca('**A tabela tem 132 linhas numeradas:** as 129 que contam', '**A tabela tem 137 linhas numeradas:** as 134 que contam');
fs.writeFileSync(D, d);
console.log('ok');
