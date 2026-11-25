// S-10, a isca da prova: uma cópia da tabela do marco 2 com UMA célula falsa, em _to_delete/.
// A coluna "exercida" da 3.2b passa a dizer que a volta na abertura seguinte foi exercida — o
// item 15 da passagem humana diz o contrário. O original não muda.
const fs = require('fs');
const de = 'docs/marcos/marco-2.md';
const para = '_to_delete/isca-marco-2.md';
const certo = '| — (o item 15 diz que a volta na abertura seguinte não foi exercida) |';
const falso = '| 19/10, item 15: a volta na abertura seguinte, exercida |';
// E o Estado, para a tabela não se contradizer: a isca se acha pela passagem humana, não por
// comparação interna (decisão do Rafael na aplicação da S-10).
const estadoCerto = '2 exercidos, os 2 em parte; a 3.2b não tem tela e não foi exercida.';
const estadoFalso = '3 exercidos, 2 deles em parte; a 3.2b não tem tela.';
const s = fs.readFileSync(de, 'utf8');
if (s.split(certo).length !== 2) throw new Error('a célula da 3.2b não está como a S-10 mediu');
if (s.split(estadoCerto).length !== 2) throw new Error('o Estado não está como a S-10 mediu');
fs.writeFileSync(para, s.replace(certo, () => falso).replace(estadoCerto, () => estadoFalso));
console.log('ok: a isca em', para, '— a exercida da 3.2b e o Estado, trocados');
