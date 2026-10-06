package com.deliveryplatform.catalog.application.port.out;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Qual expediente carimbar — perguntado ao {@code merchant}, nunca calculado.
 *
 * <p>A ADR-046 §6 diz que o dia operacional <i>"continua sendo o único lugar
 * que a calcula"</i>, e a {@code Disponibilidade} deste serviço não tem
 * {@code FusoHorario} justamente para que não haja como derivá-lo por engano.
 * Esta porta é a consequência estrutural daquela frase.
 *
 * <h2>Vazio é resposta, e não falha</h2>
 *
 * <p>{@code Optional.empty()} significa <b>a loja não abre por horário</b> — o
 * 409 que a rota do {@code merchant} devolve (ADR-049 §5). É um estado legítimo:
 * horário vazio é válido e quer dizer "nunca abre por horário". Quem chama
 * decide o que fazer, e o {@code MarcarDisponibilidadeService} decide duas
 * coisas diferentes conforme o estado pedido.
 *
 * <p>Falha de infraestrutura — tempo esgotado, 5xx, conexão recusada — é
 * {@link ExpedienteIndisponivel}, e <b>não</b> vazio. Confundir as duas faria o
 * sistema tratar uma queda do {@code merchant} como "esta loja não abre", que é
 * a forma mais silenciosa de errar que existe neste repositório.
 *
 * <h2>Sem cache, e isto é decisão escrita</h2>
 *
 * <p>O {@code estabelecimento.md} §3 registra esta porta com cache
 * <b>nenhum</b>: a resposta muda de valor quando a faixa fecha — às 01h59 a
 * pizzaria responde terça, às 02h01 responde quarta. Sessenta segundos de cache
 * carimbariam com terça o que se marca ao limpar o balcão.
 */
@FunctionalInterface
public interface ExpedienteCorrentePort {

    Optional<LocalDate> de(UUID estabelecimentoId);
}
