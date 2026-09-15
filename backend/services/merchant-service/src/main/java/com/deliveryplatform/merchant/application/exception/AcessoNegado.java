package com.deliveryplatform.merchant.application.exception;

/**
 * M7, e é uma exceção só para três situações que o cliente não deve conseguir
 * distinguir:
 *
 * <ul>
 *   <li>a loja da URL não existe;</li>
 *   <li>existe, e quem pede não tem vínculo nela;</li>
 *   <li>tem vínculo, e o vínculo não tem a permissão exigida.</li>
 * </ul>
 *
 * <p><b>Respostas diferentes transformariam a rota num scanner de
 * estabelecimentos.</b> Um 404 para "não existe" e um 403 para "não é seu"
 * dizem, juntos, quais identificadores são lojas de verdade — e o
 * {@code estabelecimentoId} da URL é entrada do atacante, não contexto
 * confiável (invariante 9, `estabelecimento.md` §3).
 *
 * <p>Não carrega qual das três foi, e não deve carregar. É a mesma forma da
 * {@code CredenciaisInvalidas} do {@code identity} e da {@code CadastroRecusado}
 * da ADR-042: uma recusa, um motivo público.
 */
public class AcessoNegado extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AcessoNegado() {
        super("sem acesso a este estabelecimento");
    }
}
