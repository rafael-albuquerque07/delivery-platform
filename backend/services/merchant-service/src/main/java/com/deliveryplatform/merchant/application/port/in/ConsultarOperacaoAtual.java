package com.deliveryplatform.merchant.application.port.in;

import java.util.UUID;

/**
 * Se a loja está aberta agora, e como ela opera — a T02 e a I8 do {@code pedido.md} (ADR-087).
 *
 * <p>Exige <b>vínculo ativo</b> e nenhuma permissão, como o {@code expediente-corrente}: quem chama
 * confere a permissão do ato que vai praticar. Sem vínculo é 403, também quando a loja não existe.
 */
public interface ConsultarOperacaoAtual {

    OperacaoAtual de(UUID estabelecimentoId, UUID usuarioId);
}
