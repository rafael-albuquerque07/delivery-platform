package com.deliveryplatform.identity.unit;

import com.deliveryplatform.identity.domain.exception.TelefoneInvalido;
import com.deliveryplatform.identity.domain.model.Telefone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelefoneTest {

    @Test
    void e164_passa_direto() {
        assertThat(Telefone.de("+5511987654321").numero()).isEqualTo("+5511987654321");
    }

    @Test
    void onze_digitos_sem_formatacao_ganha_mais_55() {
        assertThat(Telefone.de("11987654321").numero()).isEqualTo("+5511987654321");
    }

    @Test
    void dez_digitos_fixo_tambem_ganha_mais_55() {
        assertThat(Telefone.de("1132654321").numero()).isEqualTo("+551132654321");
    }

    @Test
    void treze_digitos_com_ddi_escrito_ganha_so_o_sinal() {
        assertThat(Telefone.de("5511987654321").numero()).isEqualTo("+5511987654321");
    }

    @Test
    void doze_digitos_fixo_com_ddi_escrito_ganha_so_o_sinal() {
        assertThat(Telefone.de("551133334444").numero()).isEqualTo("+551133334444");
    }

    @Test
    void onze_digitos_comecando_em_55_e_ddd_de_santa_maria_nao_ddi() {
        assertThat(Telefone.de("55987654321").numero()).isEqualTo("+5555987654321");
    }

    @Test
    void doze_digitos_que_nao_comecam_em_55_e_invalido() {
        assertThatThrownBy(() -> Telefone.de("123456789012"))
                .isInstanceOf(TelefoneInvalido.class);
    }

    @Test
    void ddi_sem_sinal_e_o_mesmo_telefone_que_com_sinal_e_formatado() {
        assertThat(Telefone.de("5511987654321")).isEqualTo(Telefone.de("+55 11 98765-4321"));
    }

    @Test
    void espaco_parenteses_e_hifen_sao_removidos_antes_da_presuncao_de_brasil() {
        assertThat(Telefone.de("(11) 98765-4321").numero()).isEqualTo("+5511987654321");
        assertThat(Telefone.de("11 98765-4321").numero()).isEqualTo("+5511987654321");
    }

    @Test
    void grafias_diferentes_do_mesmo_numero_sao_o_mesmo_telefone() {
        assertThat(Telefone.de("11 98765-4321")).isEqualTo(Telefone.de("+5511987654321"));
    }

    @Test
    void numero_curto_demais_e_invalido() {
        assertThatThrownBy(() -> Telefone.de("123"))
                .isInstanceOf(TelefoneInvalido.class);
    }

    @Test
    void letras_sao_invalidas() {
        assertThatThrownBy(() -> Telefone.de("nao-e-um-telefone"))
                .isInstanceOf(TelefoneInvalido.class);
    }

    @Test
    void nulo_e_invalido() {
        assertThatThrownBy(() -> Telefone.de(null))
                .isInstanceOf(TelefoneInvalido.class);
    }
}
