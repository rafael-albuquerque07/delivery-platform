package com.deliveryplatform.merchant.application.usecase;

import com.deliveryplatform.merchant.application.port.out.AberturaDeExpedienteRepositorio;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.Outbox;
import com.deliveryplatform.merchant.domain.evento.ExpedienteAlteradoV1;
import com.deliveryplatform.merchant.domain.model.DiaOperacional;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Quem percebe que a loja abriu.
 *
 * <p>Nenhum outro lugar do sistema percebia. O {@code merchant} calcula
 * {@code abertaEm(instante)} na leitura e não guarda nada, de propósito — o
 * {@code estabelecimento.md} §4 diz que <i>"o registro diz o que foi feito; o
 * cálculo diz o que vale agora"</i>. Mas o {@code catalogo.md} §3 exige um
 * evento na transição fechado → aberto, e transição só existe para quem
 * observa. Esta classe é o observador (ADR-046).
 *
 * <p><b>O que ela grava não é estado.</b> É o registro de um ato: <i>publiquei
 * a abertura do expediente D para a loja X</i>. Nenhum campo aqui fala do
 * estado atual da loja, então a regra do §4 continua inteira.
 */
@Service
public class PublicarAberturaDeExpediente {

    private final EstabelecimentoRepositorio estabelecimentos;
    private final AberturaDeExpedienteRepositorio aberturas;
    private final Outbox outbox;
    private final Clock relogio;

    public PublicarAberturaDeExpediente(EstabelecimentoRepositorio estabelecimentos,
                                        AberturaDeExpedienteRepositorio aberturas,
                                        Outbox outbox,
                                        Clock relogio) {
        this.estabelecimentos = estabelecimentos;
        this.aberturas = aberturas;
        this.outbox = outbox;
        this.relogio = relogio;
    }

    /**
     * Uma passada: para cada loja dentro do horário, tenta marcar a abertura do
     * expediente corrente e, se marcou, grava o evento.
     *
     * @return quantas aberturas foram publicadas nesta passada — para o teste
     *         poder afirmar o efeito sem esperar o agendador
     */
    @Transactional
    public int umaPassada() {
        Instant agora = Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);
        List<Estabelecimento> lojas = estabelecimentos.todos();

        int publicadas = 0;
        for (Estabelecimento loja : lojas) {
            FusoHorario fuso = loja.getIdentificacao().fusoHorario();
            Optional<Instant> inicio = inicioDaFaixa(loja, agora, fuso);
            if (inicio.isEmpty()) {
                continue; // fora do horário
            }

            // O dia operacional do INÍCIO da faixa, não do instante (ADR-046,
            // emendada). Uma loja 22:00–06:00 às 04:30 está no expediente que
            // abriu às 22h da véspera; o dia operacional do instante já virou às
            // 04:00, e usá-lo publicaria uma segunda abertura no meio do turno.
            LocalDate expediente = DiaOperacional.de(inicio.get(), fuso);

            // O banco decide. `false` significa que a abertura já estava
            // registrada — por uma passada anterior ou por outra instância
            // rodando ao mesmo tempo — e então não há evento a publicar. É a
            // chave primária fazendo o trabalho que um `if` sobre uma leitura
            // anterior faria com uma janela no meio.
            if (!aberturas.registrar(loja.getId(), expediente, agora)) {
                continue;
            }

            outbox.registrar(ExpedienteAlteradoV1.abertura(loja.getId(), expediente, agora));
            publicadas++;
        }
        return publicadas;
    }

    /**
     * O início da faixa de horário que contém {@code agora} — vazio fora do
     * horário.
     *
     * <p><b>Dentro do horário, e não "aberta".</b>
     *
     * <p>{@code estaAberta} compõe horário <i>e</i> pausa. Se a pizzaria abre
     * às 18h e o dono pausou às 17h50 por uma hora, às 18h ela está dentro do
     * horário e pausada — e com {@code estaAberta} a abertura daquele
     * expediente <b>nunca seria publicada</b>: quando a pausa vencesse, a loja
     * já estaria aberta sem nenhuma transição para observar, e os produtos
     * ficariam esgotados o dia inteiro.
     *
     * <p>O {@code estabelecimento.md} §4 já resolve isso ao dizer que "pausar e
     * retomar acontecem <b>dentro</b> de um expediente e não abrem outro":
     * pausa pressupõe expediente. É o horário que abre.
     */
    private Optional<Instant> inicioDaFaixa(Estabelecimento loja, Instant agora, FusoHorario fuso) {
        return loja.getDisponibilidade().inicioDaFaixaEm(agora, fuso);
    }
}
