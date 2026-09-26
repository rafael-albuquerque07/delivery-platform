package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.AberturaDeExpedienteRepositorio;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.support.Infraestrutura;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Duas instâncias da varredura, uma abertura.
 *
 * <p>Mesmo formato do {@code SaidaConcorrenteIT} da B1 e do
 * {@code CadastroConcorrenteIT} da C-B, e pelo mesmo motivo: <b>corrida não se
 * testa com dublê</b>, porque o dublê não tem a chave primária que decide quem
 * ganha.
 *
 * <p>É o que sustenta a decisão de não haver {@code jaFoiPublicada(...)} na
 * porta. Um par consultar-e-então-inserir teria janela entre as duas chamadas,
 * e no MVP — uma instância por serviço — a janela seria invisível para sempre,
 * até o dia em que alguém subisse a segunda réplica e o catálogo passasse a
 * reativar duas vezes por abertura.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "delivery.outbox.habilitado=false",
        "delivery.expediente.varredura-habilitada=false"
})
class AberturaConcorrenteIT extends Infraestrutura {

    @Autowired EstabelecimentoRepositorio lojas;
    @Autowired AberturaDeExpedienteRepositorio aberturas;
    @Autowired TransactionTemplate transacao;

    @Test
    void duas_passadas_ao_mesmo_tempo_e_so_uma_registra() throws Exception {
        UUID loja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        LocalDate expediente = LocalDate.of(2026, 9, 25);
        Instant agora = Instant.parse("2026-09-25T22:00:00Z");

        var largada = new CountDownLatch(1);
        var chegada = new CountDownLatch(2);
        var ganharam = new AtomicInteger();
        var estouraram = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            Thread.ofPlatform().start(() -> {
                try {
                    largada.await();
                    // Transação de verdade em cada thread: sem isso as duas
                    // rodariam na transação do teste e a corrida não existiria.
                    Boolean registrou = transacao.execute(
                            status -> aberturas.registrar(loja, expediente, agora));
                    if (Boolean.TRUE.equals(registrou)) {
                        ganharam.incrementAndGet();
                    }
                } catch (Exception e) {
                    estouraram.incrementAndGet();
                } finally {
                    chegada.countDown();
                }
            });
        }

        largada.countDown();
        assertThat(chegada.await(30, TimeUnit.SECONDS))
                .as("travar aqui seria deadlock, e deadlock numa varredura de minuto "
                        + "em minuto derruba o serviço")
                .isTrue();

        assertThat(ganharam.get())
                .as("o ON CONFLICT DO NOTHING devolve zero linhas para quem perdeu — "
                        + "quem decide é a chave primária, não o código")
                .isEqualTo(1);
        assertThat(estouraram.get())
                .as("perder a corrida não é erro: é a resposta normal de quem chegou "
                        + "depois, e não pode virar exceção num laço agendado")
                .isZero();
    }
}
