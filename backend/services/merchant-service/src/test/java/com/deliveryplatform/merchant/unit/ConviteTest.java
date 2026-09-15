package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.exception.ConviteInvalido;
import com.deliveryplatform.merchant.domain.model.Convite;
import com.deliveryplatform.merchant.domain.model.EstadoDoConvite;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.domain.model.Telefone;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConviteTest {

    private static final UUID LOJA = UUID.randomUUID();
    private static final UUID QUEM_CONVIDOU = UUID.randomUUID();
    private static final Telefone CONVIDADO = Telefone.de("11955554444");
    private static final Instant AGORA = Instant.parse("2026-09-15T12:00:00Z");
    private static final Set<Permissao> OFERECIDAS =
            EnumSet.of(Permissao.VER_PEDIDO, Permissao.ALTERAR_STATUS);

    private static Convite novo() {
        return Convite.novo(LOJA, CONVIDADO, OFERECIDAS, QUEM_CONVIDOU, AGORA);
    }

    // ── forma ───────────────────────────────────────────────────────────────

    @Test
    void nasce_pendente_sem_data_de_aceite_e_com_sete_dias_de_validade() {
        Convite convite = novo();

        assertThat(convite.getEstado()).isEqualTo(EstadoDoConvite.PENDENTE);
        assertThat(convite.getAceitoEm()).isNull();
        assertThat(Duration.between(convite.getCriadoEm(), convite.getExpiraEm()))
                .as("o documento diz que entre o convite e o aceite podem passar dias — "
                        + "horas não servem, e um mês é chave parada no bolso de quem "
                        + "já mudou de ideia")
                .isEqualTo(Convite.VALIDADE);
    }

    @Test
    void convidado_por_aponta_para_o_vinculo_e_nao_para_o_usuario() {
        assertThat(novo().getConvidadoPor())
                .as("M1: permissão pertence ao vínculo. É esse vínculo que A2 reconfere "
                        + "no aceite, e apontar para o usuário obrigaria a adivinhar qual "
                        + "dos vínculos dele era o relevante")
                .isEqualTo(QUEM_CONVIDOU);
    }

    @Test
    void o_token_e_longo_url_safe_e_nunca_se_repete() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            tokens.add(novo().getToken());
        }

        assertThat(tokens).hasSize(2_000);
        assertThat(novo().getToken())
                .as("32 bytes em base64url cabem numa URL sem escape e não se adivinham")
                .hasSizeGreaterThanOrEqualTo(40)
                .matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void as_permissoes_oferecidas_saem_como_copia() {
        Convite convite = novo();
        Set<Permissao> lidas = convite.getPermissoesOferecidas();

        lidas.add(Permissao.GERENCIAR_EQUIPE);

        assertThat(convite.getPermissoesOferecidas())
                .as("sem a cópia, quem lê o convite se promove")
                .doesNotContain(Permissao.GERENCIAR_EQUIPE);
    }

    @Test
    void convite_sem_permissao_nenhuma_e_valido() {
        assertThat(Convite.novo(LOJA, CONVIDADO, EnumSet.noneOf(Permissao.class),
                        QUEM_CONVIDOU, AGORA).getPermissoesOferecidas())
                .as("é o convite de quem entra para ser configurado depois — recusar "
                        + "aqui seria inventar regra que documento nenhum pede")
                .isEmpty();
    }

    // ── o estado e a data precisam concordar ────────────────────────────────

    @Test
    void aceito_sem_data_e_pendente_com_data_sao_recusados_na_reconstrucao() {
        assertThatThrownBy(() -> Convite.reconstituir(
                UUID.randomUUID(), LOJA, CONVIDADO, "t", OFERECIDAS, QUEM_CONVIDOU,
                EstadoDoConvite.ACEITO, AGORA, AGORA.plusSeconds(60), null))
                .as("um aceite sem quando")
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Convite.reconstituir(
                UUID.randomUUID(), LOJA, CONVIDADO, "t", OFERECIDAS, QUEM_CONVIDOU,
                EstadoDoConvite.PENDENTE, AGORA, AGORA.plusSeconds(60), AGORA))
                .as("um pendente que já foi aceito")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convite_que_expira_antes_de_nascer_e_recusado() {
        assertThatThrownBy(() -> Convite.reconstituir(
                UUID.randomUUID(), LOJA, CONVIDADO, "t", OFERECIDAS, QUEM_CONVIDOU,
                EstadoDoConvite.PENDENTE, AGORA, AGORA, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── expirar é derivado, nunca guardado ──────────────────────────────────

    @Test
    void o_instante_da_expiracao_ja_esta_fora() {
        Convite convite = novo();
        Instant expira = convite.getExpiraEm();

        assertThat(convite.pendente(expira.minusMillis(1))).isTrue();
        assertThat(convite.pendente(expira))
                .as("fim exclusivo, como a Faixa e o CodigoDeVerificacao")
                .isFalse();
        assertThat(convite.expirado(expira)).isTrue();
    }

    @Test
    void expirar_nao_mexe_no_estado_guardado() {
        Convite convite = novo();

        assertThat(convite.expirado(AGORA.plus(Duration.ofDays(30)))).isTrue();
        assertThat(convite.getEstado())
                .as("EXPIRADO não é estado: guardá-lo exigiria uma rotina varrendo a "
                        + "tabela, e uma rotina que ninguém escreveu é peça que nunca roda")
                .isEqualTo(EstadoDoConvite.PENDENTE);
    }

    // ── o token é a autorização ─────────────────────────────────────────────

    @Test
    void o_token_certo_passa_e_todo_o_resto_recebe_a_mesma_recusa() {
        Convite convite = novo();

        convite.exigirUtilizavel(convite.getToken(), AGORA);

        assertThatThrownBy(() -> convite.exigirUtilizavel("outro", AGORA))
                .isInstanceOf(ConviteInvalido.class);
        assertThatThrownBy(() -> convite.exigirUtilizavel(null, AGORA))
                .isInstanceOf(ConviteInvalido.class);
        assertThatThrownBy(() -> convite.exigirUtilizavel(
                convite.getToken(), AGORA.plus(Duration.ofDays(8))))
                .as("mesma recusa do token errado: a diferença entre \"expirado\" e "
                        + "\"não existe\" diria a quem adivinha que ele acertou o token")
                .isInstanceOf(ConviteInvalido.class);
    }

    // ── vazamento ───────────────────────────────────────────────────────────

    @Test
    void to_string_nao_traz_token_nem_telefone_nem_a_lista_de_permissoes() {
        Convite convite = novo();
        String texto = convite.toString();

        assertThat(texto).doesNotContain(convite.getToken());
        assertThat(texto).doesNotContain(CONVIDADO.numero());
        assertThat(texto).doesNotContain("ALTERAR_STATUS");
        assertThat(texto)
                .as("a lista desenharia o cargo da pessoa no log de quem quiser ler")
                .contains("permissoes=2");
    }
}
