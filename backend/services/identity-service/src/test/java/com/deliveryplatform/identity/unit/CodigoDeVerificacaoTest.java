package com.deliveryplatform.identity.unit;

import com.deliveryplatform.identity.domain.model.CodigoDeVerificacao;
import com.deliveryplatform.identity.domain.model.Telefone;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodigoDeVerificacaoTest {

    private static final Telefone TELEFONE = Telefone.de("11987654321");
    private static final Instant AGORA = Instant.parse("2026-09-14T12:00:00Z");

    private static CodigoDeVerificacao novo(String codigo) {
        return CodigoDeVerificacao.novo(TELEFONE, codigo, AGORA);
    }

    // ── forma ───────────────────────────────────────────────────────────────

    @Test
    void o_codigo_tem_exatamente_seis_digitos() {
        assertThatThrownBy(() -> novo("12345")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> novo("1234567")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> novo("abcdef")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> novo("12 345")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> novo(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void o_zero_a_esquerda_do_codigo_sobrevive() {
        assertThat(novo("004291").getCodigo())
                .as("como número viraria 4291, e o comerciante digitaria seis dígitos "
                        + "que nunca conferem")
                .isEqualTo("004291");
    }

    @Test
    void a_validade_e_contada_a_partir_do_instante_recebido_e_nao_do_relogio_da_jvm() {
        CodigoDeVerificacao codigo = novo("123456");

        assertThat(codigo.getCriadoEm()).isEqualTo(AGORA);
        assertThat(Duration.between(codigo.getCriadoEm(), codigo.getExpiraEm()))
                .isEqualTo(CodigoDeVerificacao.VALIDADE);
    }

    @Test
    void reconstituir_recusa_um_codigo_que_expira_antes_de_nascer() {
        assertThatThrownBy(() -> CodigoDeVerificacao.reconstituir(
                UUID.randomUUID(), TELEFONE, "123456", AGORA, AGORA.minusSeconds(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── validade no tempo ───────────────────────────────────────────────────

    @Test
    void o_instante_da_expiracao_ja_esta_fora() {
        CodigoDeVerificacao codigo = novo("123456");
        Instant expira = codigo.getExpiraEm();

        assertThat(codigo.valido(expira.minusMillis(1)))
                .as("um milissegundo antes ainda vale").isTrue();
        assertThat(codigo.valido(expira))
                .as("no instante exato já não vale — fim exclusivo, como a Faixa "
                        + "do merchant").isFalse();
        assertThat(codigo.expirado(expira)).isTrue();
    }

    @Test
    void codigo_expirado_nao_confere_nem_com_o_valor_certo() {
        CodigoDeVerificacao codigo = novo("123456");

        assertThat(codigo.confere("123456", AGORA.plus(CodigoDeVerificacao.VALIDADE))).isFalse();
    }

    @Test
    void tentativa_em_codigo_expirado_nao_gasta_tentativa() {
        CodigoDeVerificacao codigo = novo("123456");

        codigo.confere("000000", AGORA.plus(Duration.ofHours(1)));

        assertThat(codigo.getTentativas())
                .as("não há o que gastar num código que já morreu")
                .isZero();
    }

    // ── conferência ─────────────────────────────────────────────────────────

    @Test
    void o_codigo_certo_confere() {
        assertThat(novo("123456").confere("123456", AGORA)).isTrue();
    }

    @Test
    void conferir_gasta_uma_tentativa_mesmo_quando_acerta() {
        CodigoDeVerificacao codigo = novo("123456");

        codigo.confere("123456", AGORA);

        assertThat(codigo.getTentativas())
                .as("não há como conferir sem gastar — separar as duas coisas daria a "
                        + "quem chama a opção de varrer o espaço de graça")
                .isEqualTo(1);
    }

    @Test
    void codigo_errado_nao_confere_e_gasta_tentativa() {
        CodigoDeVerificacao codigo = novo("123456");

        assertThat(codigo.confere("654321", AGORA)).isFalse();
        assertThat(codigo.getTentativas()).isEqualTo(1);
    }

    @Test
    void informado_nulo_nao_confere() {
        CodigoDeVerificacao codigo = novo("123456");

        assertThat(codigo.confere(null, AGORA)).isFalse();
    }

    @Test
    void cinco_erros_queimam_o_codigo_e_o_sexto_acerto_nao_vale() {
        CodigoDeVerificacao codigo = novo("123456");

        for (int i = 0; i < CodigoDeVerificacao.TENTATIVAS_MAXIMAS; i++) {
            assertThat(codigo.confere("000000", AGORA)).isFalse();
        }

        assertThat(codigo.valido(AGORA)).isFalse();
        assertThat(codigo.confere("123456", AGORA))
                .as("sem este limite, seis dígitos se percorrem inteiros em minutos e "
                        + "a verificação não verifica nada")
                .isFalse();
        assertThat(codigo.getTentativas()).isEqualTo(CodigoDeVerificacao.TENTATIVAS_MAXIMAS);
    }

    // ── vazamento ───────────────────────────────────────────────────────────

    @Test
    void to_string_nao_traz_o_codigo_nem_o_telefone() {
        // Id fixo de propósito: com UUID aleatório, um "123456" por acaso dentro
        // do hexadecimal tornaria esta asserção instável uma vez em um milhão —
        // e um teste que falha uma vez em um milhão é pior que teste nenhum.
        String texto = CodigoDeVerificacao.reconstituir(
                        UUID.fromString("00000000-0000-4000-8000-000000000000"),
                        TELEFONE, "123456", AGORA, AGORA.plusSeconds(600), 0)
                .toString();

        assertThat(texto).doesNotContain("123456");
        assertThat(texto).doesNotContain(TELEFONE.numero());
        assertThat(texto).contains("tentativas");
    }
}
