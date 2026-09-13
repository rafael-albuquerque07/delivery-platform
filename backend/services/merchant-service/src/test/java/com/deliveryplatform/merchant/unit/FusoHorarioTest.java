package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.exception.FusoHorarioInvalido;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FusoHorarioTest {

    /** Instante fixo: teste de fuso que depende de "agora" mede outra coisa. */
    private static final Instant INSTANTE = Instant.parse("2026-09-13T15:00:00Z");

    @Test
    void o_padrao_e_sao_paulo() {
        assertThat(FusoHorario.PADRAO.identificador())
                .as("ADR-025 §2: o campo vem preenchido, e quase ninguém vai trocar")
                .isEqualTo("America/Sao_Paulo");
    }

    @Test
    void zonas_brasileiras_com_deslocamentos_diferentes_sao_aceitas() {
        assertThat(deslocamentoDe("America/Noronha")).isEqualTo(ZoneOffset.ofHours(-2));
        assertThat(deslocamentoDe("America/Sao_Paulo")).isEqualTo(ZoneOffset.ofHours(-3));
        assertThat(deslocamentoDe("America/Manaus")).isEqualTo(ZoneOffset.ofHours(-4));
        assertThat(deslocamentoDe("America/Rio_Branco")).isEqualTo(ZoneOffset.ofHours(-5));
    }

    @Test
    void todo_identificador_aceito_existe_no_tzdb_e_volta_igual() {
        assertThat(FusoHorario.identificadoresAceitos())
                .as("as dezesseis zonas do Brasil; esta contagem é o que pega remoção acidental")
                .hasSize(16);

        assertThat(FusoHorario.identificadoresAceitos()).allSatisfy(identificador ->
                assertThat(FusoHorario.de(identificador).identificador())
                        .as("erro de digitação na lista não daria erro de compilação — "
                                + "daria uma zona que o ZoneId.of recusa em runtime")
                        .isEqualTo(identificador));
    }

    @Test
    void deslocamento_fixo_e_recusado() {
        assertThatThrownBy(() -> FusoHorario.de("-03:00"))
                .as("M16 pede identificador IANA: se o horário de verão voltar, "
                        + "Recife e São Paulo deixam de compartilhar o deslocamento")
                .isInstanceOf(FusoHorarioInvalido.class);

        assertThatThrownBy(() -> FusoHorario.de("UTC-03:00"))
                .isInstanceOf(FusoHorarioInvalido.class);
    }

    @Test
    void zona_estrangeira_e_recusada() {
        assertThatThrownBy(() -> FusoHorario.de("Europe/Lisbon"))
                .isInstanceOf(FusoHorarioInvalido.class);
        assertThatThrownBy(() -> FusoHorario.de("America/New_York"))
                .isInstanceOf(FusoHorarioInvalido.class);
    }

    @Test
    void apelido_de_compatibilidade_do_tzdb_e_recusado() {
        assertThatThrownBy(() -> FusoHorario.de("Brazil/East"))
                .as("o ZoneId.of aceita o link e devolve o id dele mesmo, não resolvido — "
                        + "aceitar daria duas grafias para a mesma zona")
                .isInstanceOf(FusoHorarioInvalido.class);
    }

    @Test
    void texto_que_nao_e_zona_e_invalido() {
        assertThatThrownBy(() -> FusoHorario.de("Pizzaria/Marli"))
                .isInstanceOf(FusoHorarioInvalido.class);
        assertThatThrownBy(() -> FusoHorario.de(""))
                .isInstanceOf(FusoHorarioInvalido.class);
    }

    @Test
    void nulo_e_invalido() {
        assertThatThrownBy(() -> FusoHorario.de(null))
                .as("M16: nunca nulo")
                .isInstanceOf(FusoHorarioInvalido.class);
    }

    @Test
    void espaco_em_volta_e_aparado() {
        assertThat(FusoHorario.de("  America/Recife  ").identificador())
                .isEqualTo("America/Recife");
    }

    @Test
    void o_construtor_canonico_valida_tambem() {
        assertThatThrownBy(() -> new FusoHorario(ZoneId.of("Europe/Lisbon")))
                .as("a validação não pode morar só na fábrica: a desserialização e o "
                        + "mapeador de persistência chamam o construtor")
                .isInstanceOf(FusoHorarioInvalido.class);

        assertThatThrownBy(() -> new FusoHorario(null))
                .isInstanceOf(FusoHorarioInvalido.class);
    }

    private static ZoneOffset deslocamentoDe(String identificador) {
        return FusoHorario.de(identificador).zona().getRules().getOffset(INSTANTE);
    }
}
