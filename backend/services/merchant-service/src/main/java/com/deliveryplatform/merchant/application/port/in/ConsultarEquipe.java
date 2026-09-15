package com.deliveryplatform.merchant.application.port.in;

import com.deliveryplatform.merchant.domain.model.Equipe;

import java.util.UUID;

/**
 * A primeira consulta autorizada do {@code merchant}.
 *
 * <p><b>Recebe os dois identificadores, e a ordem dos argumentos não é
 * acidental:</b> o da URL primeiro, o do token depois — porque é o segundo que
 * decide se o primeiro vale. Nenhuma consulta deste serviço filtra por um
 * {@code estabelecimentoId} que não tenha sido confrontado com o vínculo de
 * quem pede.
 */
public interface ConsultarEquipe {

    Equipe daLoja(UUID estabelecimentoId, UUID usuarioAutenticado);
}
