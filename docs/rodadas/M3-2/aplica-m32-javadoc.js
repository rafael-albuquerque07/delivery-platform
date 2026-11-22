// M3-2 v2 — três javadocs do `catalog` que a cotação deixa falsos. Roda da raiz, DEPOIS de o
// código existir (o `{@link #buscarPorIds}` precisa do método):  node m32/aplica-m32-javadoc.js
const fs = require('fs');
const C = 'backend/services/catalog-service/src/main/java/com/deliveryplatform/catalog/';
const trocas = {
  [C + 'config/SecurityConfig.java']: [
    [' * <p><b>Nenhuma linha para {@code /internal/**}</b>, e não por esquecimento:\n * este serviço não expõe rota interna nenhuma nesta rodada. Ele <i>consome</i> a\n * do {@code merchant}.\n',
     ' * <p><b>Nenhuma linha para {@code /internal/**}</b>, e não por esquecimento:\n * a única rota interna deste serviço, a cotação (ADR-088), exige o token do\n * portador como as outras e cai no {@code anyRequest().authenticated()}. E ele\n * <i>consome</i> as do {@code merchant}.\n'],
  ],
  [C + 'application/port/out/ProdutoRepositorio.java']: [
    [' * A porta de saída do produto. Quatro métodos, e nenhum a mais — o quarto,\n * {@link #idsParaReativar}, chegou na G-C3a; quem acrescentar o quinto emenda\n * esta frase.\n',
     ' * A porta de saída do produto. Cinco métodos, e nenhum a mais — o quarto,\n * {@link #idsParaReativar}, chegou na G-C3a; o quinto, {@link #buscarPorIds},\n * na M3-2, para a cotação ler os produtos numa ida só (ADR-088); quem\n * acrescentar o sexto emenda esta frase.\n'],
  ],
  [C + 'api/error/TratadorDeErros.java']: [
    [' * Nove tratadores, e cada um devolve', ' * Dez tratadores, e cada um devolve'],
    [' *   <li>{@code ExpedienteIndisponivel} → 503 (G-C2).</li>\n',
     ' *   <li>{@code ItensIndisponiveis} → 409, com a lista dos indisponíveis (M3-2, ADR-088);</li>\n *   <li>{@code ExpedienteIndisponivel} → 503 (G-C2).</li>\n'],
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
console.log('ok: 3 javadocs, 4 trocas');
