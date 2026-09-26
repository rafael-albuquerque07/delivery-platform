package com.deliveryplatform.merchant.application.port.out;

import com.deliveryplatform.merchant.domain.model.Estabelecimento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída para persistência do {@code Estabelecimento}. O adaptador mora
 * em {@code infrastructure/persistence}.
 *
 * <p><b>Por que aqui e não em {@code domain/repository}.</b> O {@code README.md}
 * diz que <i>"application orquestra casos de uso e depende de interfaces
 * ({@code port/out})"</i>, e é onde o {@code identity-service} pôs o
 * {@code UsuarioRepositorio}. O {@code CLAUDE.md} dizia o contrário — <i>"as
 * interfaces de repositório ficam em {@code domain}"</i> — e era a linha errada;
 * ela e os oito diretórios {@code domain/repository/} vazios saem nesta rodada.
 *
 * <p>Só há os métodos que têm chamador. Buscar por documento não entra: não
 * há unicidade de documento (uma pessoa pode ter duas lojas) e ninguém pergunta.
 */
public interface EstabelecimentoRepositorio {

    Estabelecimento salvar(Estabelecimento estabelecimento);

    Optional<Estabelecimento> buscarPorId(UUID id);

    /**
     * Todos os estabelecimentos, sem paginação. O único chamador é a varredura de
     * abertura (ADR-046), que precisa perguntar a cada loja se está dentro do
     * horário. É exceção deliberada à paginação obrigatória, com custo e gatilho
     * na ADR-046: 1+5 consultas por passada, qualquer que seja o número de lojas.
     */
    List<Estabelecimento> todos();
}
