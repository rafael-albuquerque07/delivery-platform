#!/usr/bin/env node
// As três contas do razão (docs/dividas.md, ADR-072), numa execução só, da raiz do repositório:
//
//   node scripts/razao.mjs
//
// A especificação são os dois comandos do cabeçalho do docs/dividas.md — o recorte estreito e a
// segunda passada — e a contagem das linhas numeradas da tabela. Este script os reproduz, e os
// números dele têm de bater com os do grep. Se divergirem, o defeito é deste arquivo.
//
// Só lê. Não escreve nada e não chama a rede. Usa o git só para dizer quais arquivos o git não
// conhece — sem git no caminho, essa parte sai como "não medido".
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join, sep } from 'node:path';

const barra = (p) => p.split(sep).join('/');

function arquivos(raiz, extensoes) {
  const achados = [];
  let entradas;
  try { entradas = readdirSync(raiz); } catch { return achados; }
  for (const nome of entradas) {
    const caminho = join(raiz, nome);
    const p = barra(caminho);
    if (p.includes('/build/') || p.includes('/node_modules/') || p.endsWith('/build')
        || p.endsWith('/node_modules')) continue;
    let st;
    try { st = statSync(caminho); } catch { continue; }
    if (st.isDirectory()) achados.push(...arquivos(caminho, extensoes));
    else if (extensoes.some((e) => nome.endsWith(e)) && nome !== 'dividas.md') achados.push(p);
  }
  return achados;
}

function linhas(lista, padrao) {
  const resultado = [];
  for (const f of lista) {
    const texto = readFileSync(f, 'utf8').split(/\r?\n/);
    texto.forEach((l, i) => { if (padrao.test(l)) resultado.push(`${f}:${i + 1}`); });
  }
  return resultado;
}

// 1 · o recorte estreito — grep -rn "Gatilho escrito\|gatilho escrito" docs/ contracts/ CLAUDE.md
//     backend/ frontend/ (.md .java .ts .tsx), sem dividas.md, sem /build/ e /node_modules/
const doRecorte = [
  ...arquivos('docs', ['.md', '.java', '.ts', '.tsx']),
  ...arquivos('contracts', ['.md', '.java', '.ts', '.tsx']),
  ...arquivos('backend', ['.md', '.java', '.ts', '.tsx']),
  ...arquivos('frontend', ['.md', '.java', '.ts', '.tsx']),
];
try { statSync('CLAUDE.md'); doRecorte.push('CLAUDE.md'); } catch { /* sem CLAUDE.md */ }
const recorte = linhas(doRecorte, /[Gg]atilho escrito/);

// 2 · a segunda passada — grep -rn -i "nesta altura\|por enquanto\|ainda não existe\|quando
//     houver\|até que haja" docs/ backend/*/*/src/main (.md .java), sem dividas.md, sem /build/
const daSegunda = [
  ...arquivos('docs', ['.md', '.java']),
  ...arquivos('backend', ['.md', '.java']).filter((f) => /^backend\/[^/]+\/[^/]+\/src\/main\//.test(f)),
];
const segunda = linhas(daSegunda, /nesta altura|por enquanto|ainda não existe|quando houver|até que haja/i);

// 3 · as linhas numeradas da tabela do razão
const numeros = readFileSync('docs/dividas.md', 'utf8').split(/\r?\n/)
  .map((l) => /^\| (\d+) \|/.exec(l)).filter(Boolean).map((m) => Number(m[1]));
const maior = Math.max(...numeros);
const distintos = new Set(numeros);
const faltam = [];
for (let n = 1; n <= maior; n++) if (!distintos.has(n)) faltam.push(n);
const repetidos = [...new Set(numeros.filter((n, i) => numeros.indexOf(n) !== i))];

// o que o git não conhece, entre os arquivos do recorte
let foraDoGit = 'não medido';
try {
  const conhecidos = new Set(execFileSync('git', ['ls-files'], { encoding: 'utf8' }).split('\n'));
  const fora = {};
  for (const r of recorte) {
    const f = r.slice(0, r.lastIndexOf(':'));
    if (!conhecidos.has(f)) fora[f] = (fora[f] || 0) + 1;
  }
  foraDoGit = Object.entries(fora).map(([f, n]) => `${f} (${n})`);
} catch { /* sem git */ }

console.log(`o recorte estreito devolve: ${recorte.length}`);
console.log(`  dessas, em arquivo que o git não conhece: ${Array.isArray(foraDoGit)
  ? `${foraDoGit.reduce((s, x) => s + Number(x.match(/\((\d+)\)$/)[1]), 0)}${foraDoGit.length ? ' — ' + foraDoGit.join(', ') : ''}`
  : foraDoGit}`);
console.log(`a segunda passada devolve: ${segunda.length}`);
console.log(`linhas numeradas: ${numeros.length} · maior número: ${maior} · distintos: ${distintos.size} · faltam: [${faltam.join(', ')}] · repetidos: [${repetidos.join(', ')}]`);
