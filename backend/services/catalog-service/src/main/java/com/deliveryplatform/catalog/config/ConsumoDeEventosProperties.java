package com.deliveryplatform.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Os números da ADR-026 para o consumo do {@code ExpedienteAlteradoV1}.
 *
 * <p><b>Por que eles são nossos, e não do {@code spring.rabbitmq.listener.simple.retry}.</b>
 * Aquele bloco do YAML configura a fábrica <b>auto-configurada</b> de contêineres — a
 * que o {@code OuvinteDeVinculoAlterado} usa, e que não quer retentativa nenhuma: ele
 * confirma sempre, de propósito. E o {@code max-interval} daquele bloco tem <b>10 s</b>
 * de padrão no Boot 4.1.1 (medido no jar, G-C3b), que cortaria os 16 s da terceira
 * espera da ADR-026 sem avisar. Os números saem daqui, ligados só à fábrica deste
 * consumo.
 *
 * <p>A forma é a das vizinhas — {@code record} com {@code @DefaultValue} —, e valida no
 * construtor como a {@code ReativacaoProperties}: um valor inválido derruba a subida,
 * em vez de ser trocado em silêncio por outro.
 *
 * <p><b>Os padrões são a ADR-026, e há um teste que os lê</b>
 * ({@code ConsumoDeEventosPropertiesTest}) — porque o teste de integração os sobrescreve
 * para milissegundos, e sem aquele teste nada no repositório olharia para 1 s, 4 s e 16 s.
 *
 * @param intervalo primeira espera (ADR-026: 1 s)
 * @param multiplicador fator entre esperas (ADR-026: 4 — 1 s, 4 s, 16 s)
 * @param tetoDoIntervalo espera máxima; tem de caber os 16 s
 * @param tentativas total de entregas ao ouvinte, incluindo a primeira (ADR-026: 4)
 * @param fila o nome da fila durável; a fila morta é ela com {@code .morta} no fim
 *              (ADR-026 §2, uma por fila de trabalho). Configurável por um motivo de
 *              teste que é real: o contexto do Spring fica em cache entre as classes de
 *              teste, cada contexto liga um ouvinte, e com o mesmo nome todos disputariam
 *              a mesma fila. Os testes do consumo usam um nome próprio.
 */
@ConfigurationProperties(prefix = "delivery.consumo-de-eventos")
public record ConsumoDeEventosProperties(

        @DefaultValue("catalog.expediente-alterado") String fila,

        @DefaultValue("1s") Duration intervalo,

        @DefaultValue("4") double multiplicador,

        @DefaultValue("16s") Duration tetoDoIntervalo,

        @DefaultValue("4") int tentativas
) {

    public ConsumoDeEventosProperties {
        if (fila == null || fila.isBlank()) {
            throw new IllegalStateException("delivery.consumo-de-eventos.fila não pode ser vazia");
        }
        if (intervalo == null || intervalo.isNegative() || intervalo.isZero()) {
            throw new IllegalStateException(
                    "delivery.consumo-de-eventos.intervalo precisa ser positivo");
        }
        if (multiplicador < 1.0) {
            throw new IllegalStateException(
                    "delivery.consumo-de-eventos.multiplicador menor que 1 encurtaria as esperas");
        }
        if (tetoDoIntervalo == null || tetoDoIntervalo.compareTo(intervalo) < 0) {
            throw new IllegalStateException(
                    "delivery.consumo-de-eventos.teto-do-intervalo menor que o intervalo "
                            + "cortaria a primeira espera");
        }
        if (tentativas < 1) {
            throw new IllegalStateException(
                    "delivery.consumo-de-eventos.tentativas precisa ser pelo menos 1 — "
                            + "zero faria a mensagem ir para a fila morta sem ser entregue");
        }
    }

    /** Uma fila morta por fila de trabalho (ADR-026 §2). */
    public String filaMorta() {
        return fila + ".morta";
    }
}
