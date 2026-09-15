package com.deliveryplatform.merchant.application.port.out;

import com.deliveryplatform.merchant.domain.model.Convite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída do convite.
 *
 * <p><b>A busca é pelo token, e não pelo id.</b> Quem aceita tem o token na
 * mão — é tudo o que recebeu — e procurar por id exigiria que o convidado
 * conhecesse um identificador que não lhe foi dado. Um {@code buscarPorId}
 * entraria no dia em que a tela de equipe listar convites pendentes para
 * cancelar, e não antes.
 */
public interface ConviteRepositorio {

    Convite salvar(Convite convite);

    Optional<Convite> buscarPorToken(String token);

    /**
     * Os convites que ainda esperam alguém. <b>Não filtra expirados</b>: quem
     * decide isso é o {@code Convite}, com o instante na mão, e um filtro no
     * SQL seria uma segunda definição de "pendente" — que envelheceria sozinha
     * no dia em que a validade mudasse.
     */
    List<Convite> pendentesDe(UUID estabelecimentoId);
}
