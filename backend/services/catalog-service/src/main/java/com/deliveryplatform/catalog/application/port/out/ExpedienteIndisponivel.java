package com.deliveryplatform.catalog.application.port.out;

/**
 * O {@code merchant} não respondeu sobre o expediente.
 *
 * <p>Irmã da {@code AutorizacaoIndisponivel}, e com a mesma política: <b>falha
 * fechada</b>. Se o serviço que sabe o dia operacional está fora do ar, o
 * catálogo não inventa um carimbo — ele recusa a marcação.
 *
 * <p>O preço disso está assumido na ADR-011: uma queda do {@code merchant} para
 * a plataforma inteira. A alternativa — carimbar com um palpite — grava no banco
 * um dado errado que ninguém vai revisar, e que decide se um produto volta ao
 * cardápio amanhã.
 *
 * <p><b>Não é a mesma coisa que "a loja não abre por horário"</b>, que é
 * {@code Optional.empty()} e resposta legítima. Esta é 503.
 */
public class ExpedienteIndisponivel extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExpedienteIndisponivel(String motivo, Throwable causa) {
        super(motivo, causa);
    }

    public ExpedienteIndisponivel(String motivo) {
        super(motivo);
    }
}
