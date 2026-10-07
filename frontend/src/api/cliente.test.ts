import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { chamar, ErroDaApi, quandoASessaoCair, SemResposta } from './cliente';

/**
 * O cliente HTTP contra um `fetch` de mentira que <b>guarda o que recebeu</b>.
 *
 * O mesmo desenho do `MerchantDeMentira` do back, e pelo mesmo motivo: um dublê
 * que só devolvesse resposta provaria que a função foi chamada. Este prova
 * <b>o que saiu</b> — cabeçalho, método, corpo e política de credencial —, que é
 * onde os defeitos deste arquivo moram.
 */
describe('cliente HTTP', () => {
  let chamadas: Array<{ url: string; init: RequestInit }>;

  function responderCom(status: number, corpo?: string, cabecalhos: HeadersInit = {}) {
    return vi.fn((url: string, init: RequestInit) => {
      chamadas.push({ url, init });
      return Promise.resolve(
        new Response(corpo ?? null, {
          status,
          headers: { 'Content-Type': 'application/json', ...cabecalhos },
        }),
      );
    });
  }

  beforeEach(() => {
    chamadas = [];
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  // ── o que sai ──────────────────────────────────────────────────────────────

  it('o token vai como Bearer, e o corpo vai como JSON', async () => {
    vi.stubGlobal('fetch', responderCom(200, '{"ok":true}'));

    await chamar('/api/v1/x', { metodo: 'POST', corpo: { a: 1 }, token: 'abc' });

    const [chamada] = chamadas;
    const cabecalhos = chamada?.init.headers as Record<string, string>;
    expect(cabecalhos['Authorization']).toBe('Bearer abc');
    expect(cabecalhos['Content-Type']).toBe('application/json');
    expect(chamada?.init.body).toBe('{"a":1}');
    expect(chamada?.init.method).toBe('POST');
  });

  it('sem token, NÃO vai cabeçalho de autorização', async () => {
    // As rotas de /auth são públicas. Mandar `Authorization: Bearer undefined`
    // — que é o que acontece quando se interpola um valor ausente — faz o
    // Resource Server recusar com 401 uma rota que nem exigia token.
    vi.stubGlobal('fetch', responderCom(200, '{}'));

    await chamar('/api/v1/auth/login', { metodo: 'POST', corpo: { a: 1 } });

    const cabecalhos = chamadas[0]?.init.headers as Record<string, string>;
    expect(cabecalhos).not.toHaveProperty('Authorization');
  });

  it('nunca manda credencial de navegador', async () => {
    // O CORS do gateway declara allowCredentials: false. Com
    // credentials: 'include', o navegador RECUSA a resposta inteira — e o erro
    // que aparece não fala de credencial.
    vi.stubGlobal('fetch', responderCom(200, '{}'));

    await chamar('/api/v1/x', { token: 'abc' });

    expect(chamadas[0]?.init.credentials).toBe('omit');
  });

  // ── o que volta ────────────────────────────────────────────────────────────

  it('200 com corpo vira objeto', async () => {
    vi.stubGlobal('fetch', responderCom(200, '{"total":2}'));

    const corpo = await chamar<{ total: number }>('/api/v1/x');

    expect(corpo.total).toBe(2);
  });

  it('202 sem corpo não estoura — é o caminho de sucesso do verification-code', async () => {
    // Sem a saída de corpo vazio, o JSON.parse('') estouraria num caminho que é
    // sucesso, e a tela de cadastro mostraria erro depois de dar tudo certo.
    vi.stubGlobal('fetch', responderCom(202));

    await expect(
      chamar<void>('/api/v1/auth/verification-code', { metodo: 'POST' }),
    ).resolves.toBeUndefined();
  });

  it('403 com ProblemDetail traz o detail', async () => {
    vi.stubGlobal(
      'fetch',
      responderCom(
        403,
        '{"status":403,"title":"Forbidden","detail":"sem acesso a este estabelecimento"}',
      ),
    );

    const erro = await chamar('/api/v1/x', { token: 'abc' }).catch((causa: unknown) => causa);

    expect(erro).toBeInstanceOf(ErroDaApi);
    expect((erro as ErroDaApi).status).toBe(403);
    expect((erro as ErroDaApi).detalhe).toBe('sem acesso a este estabelecimento');
  });

  it('401 SEM corpo vira erro sem detalhe — e é assim que o Resource Server responde', async () => {
    // Este é o caso que derruba um front que dependa de `detail` para ter texto.
    // O `detalhe` precisa ser undefined, não string vazia: quem renderiza usa
    // `?? 'texto próprio'`, e '' não dispara o `??`.
    vi.stubGlobal('fetch', responderCom(401, undefined, { 'WWW-Authenticate': 'Bearer' }));

    const erro = await chamar('/api/v1/x', { token: 'morto' }).catch((causa: unknown) => causa);

    expect(erro).toBeInstanceOf(ErroDaApi);
    expect((erro as ErroDaApi).detalhe).toBeUndefined();
  });

  // ADR-055 §3 (W-C): o `detail` vai à tela, então só texto vira `detalhe`.
  // Resposta de erro pode vir do gateway, de um proxy ou da página de erro do
  // contêiner — e colar na tela o corpo do que chegou seria o defeito.
  it.each([
    ['texto solto', '<html>502 Bad Gateway</html>'],
    ['número', '42'],
    ['objeto sem detail', '{"title":"Conflict","status":409}'],
    ['detail que não é texto', '{"detail":{"mensagem":"oi"}}'],
  ])('corpo sem detail textual não vira detalhe: %s', async (_nome, corpo) => {
    vi.stubGlobal('fetch', responderCom(409, corpo));

    const erro = await chamar('/api/v1/x', { token: 'abc' }).catch((causa: unknown) => causa);

    expect((erro as ErroDaApi).status).toBe(409);
    expect((erro as ErroDaApi).detalhe).toBeUndefined();
  });

  it('corpo de erro que não é JSON não esconde o status', async () => {
    vi.stubGlobal('fetch', responderCom(500, '<html>Internal Server Error</html>'));

    const erro = await chamar('/api/v1/x').catch((causa: unknown) => causa);

    expect((erro as ErroDaApi).status).toBe(500);
  });

  it('rede caída vira SemResposta, e não ErroDaApi', async () => {
    // A distinção importa: ErroDaApi significa que o servidor respondeu.
    // SemResposta é onde o CORS recusado também cai, e a tela precisa dizer
    // outra coisa.
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
    );

    const erro = await chamar('/api/v1/x').catch((causa: unknown) => causa);

    expect(erro).toBeInstanceOf(SemResposta);
    expect(erro).not.toBeInstanceOf(ErroDaApi);
  });

  // ── o 401 derruba a sessão ─────────────────────────────────────────────────

  it('401 apaga a sessão guardada e avisa quem escuta', async () => {
    sessionStorage.setItem(
      'delivery.sessao',
      JSON.stringify({ token: 'morto', expiraEm: Date.now() + 1_000_000 }),
    );
    const avisado = vi.fn();
    quandoASessaoCair(avisado);
    vi.stubGlobal('fetch', responderCom(401));

    await chamar('/api/v1/x', { token: 'morto' }).catch(() => undefined);

    expect(sessionStorage.getItem('delivery.sessao')).toBeNull();
    expect(avisado).toHaveBeenCalledOnce();
  });

  it('403 NÃO derruba a sessão — não ter acesso não é não estar autenticado', async () => {
    // Se o 403 apagasse a sessão, a recusa de uma loja deslogaria a pessoa de
    // todas. É a confusão entre autenticação e autorização, e ela é fácil de
    // escrever por engano num único `if (!resposta.ok)`.
    sessionStorage.setItem(
      'delivery.sessao',
      JSON.stringify({ token: 'vivo', expiraEm: Date.now() + 1_000_000 }),
    );
    const avisado = vi.fn();
    quandoASessaoCair(avisado);
    vi.stubGlobal('fetch', responderCom(403, '{"detail":"sem acesso a este estabelecimento"}'));

    await chamar('/api/v1/x', { token: 'vivo' }).catch(() => undefined);

    expect(sessionStorage.getItem('delivery.sessao')).not.toBeNull();
    expect(avisado).not.toHaveBeenCalled();
  });
});
