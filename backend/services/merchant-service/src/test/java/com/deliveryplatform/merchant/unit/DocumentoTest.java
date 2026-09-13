package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.exception.DocumentoInvalido;
import com.deliveryplatform.merchant.domain.model.Documento;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentoTest {

    @Test
    void cpf_de_onze_digitos_e_valido() {
        assertThat(new Documento("12345678909").numero()).isEqualTo("12345678909");
    }

    @Test
    void cnpj_de_quatorze_digitos_e_valido() {
        assertThat(new Documento("12345678000195").numero()).isEqualTo("12345678000195");
    }

    @Test
    void formatacao_e_descartada() {
        assertThat(new Documento("123.456.789-09").numero()).isEqualTo("12345678909");
        assertThat(new Documento("12.345.678/0001-95").numero()).isEqualTo("12345678000195");
    }

    @Test
    void grafias_diferentes_do_mesmo_documento_sao_o_mesmo_documento() {
        assertThat(new Documento("123.456.789-09"))
                .as("guardar só dígitos é o que impede a mesma inscrição existir duas vezes")
                .isEqualTo(new Documento("12345678909"));
    }

    @Test
    void digito_verificador_errado_e_aceito_de_proposito() {
        assertThat(new Documento("11111111111").numero())
                .as("ADR-029 §3: o documento é público no Brasil e não prova titularidade "
                        + "de nada. Conferir o dígito só faria o campo parecer mais "
                        + "confiável do que é")
                .isEqualTo("11111111111");
    }

    @Test
    void quantidade_de_digitos_fora_de_11_e_14_e_invalida() {
        assertThatThrownBy(() -> new Documento("123456789012"))
                .isInstanceOf(DocumentoInvalido.class);
        assertThatThrownBy(() -> new Documento("1234567890"))
                .isInstanceOf(DocumentoInvalido.class);
        assertThatThrownBy(() -> new Documento("123456789001950"))
                .isInstanceOf(DocumentoInvalido.class);
    }

    @Test
    void vazio_e_nulo_sao_invalidos() {
        assertThatThrownBy(() -> new Documento("")).isInstanceOf(DocumentoInvalido.class);
        assertThatThrownBy(() -> new Documento(null)).isInstanceOf(DocumentoInvalido.class);
        assertThatThrownBy(() -> new Documento("....-/")).isInstanceOf(DocumentoInvalido.class);
    }

    @Test
    void to_string_nao_mostra_o_numero() {
        assertThat(new Documento("12345678909").toString())
                .as("o CLAUDE.md manda registrar log sem documento, e toString é como "
                        + "um campo chega ao log sem ninguém ter decidido isso")
                .doesNotContain("12345678909")
                .isEqualTo("Documento[oculto]");
    }

    @Test
    void a_mensagem_do_erro_nao_carrega_o_documento() {
        assertThatThrownBy(() -> new Documento("123.456.789-0"))
                .hasMessageNotContaining("12345678")
                .hasMessageContaining("vieram 10");
    }
}
