package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.model.Disponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O carimbo, o que ele recusa, e a pergunta da reativação. */
class DisponibilidadeTest {

    private static final Instant SABADO_A_01H = Instant.parse("2026-09-26T04:00:00Z");
    private static final LocalDate SEXTA = LocalDate.of(2026, 9, 25);
    private static final LocalDate SABADO = LocalDate.of(2026, 9, 26);

    @Nested
    class OCarimbo {

        @Test
        void inicial_nao_tem_carimbo() {
            Disponibilidade d = Disponibilidade.inicial();

            assertThat(d.estado()).isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);
            assertThat(d.marcadoEm()).isNull();
            assertThat(d.expedienteDeReferencia()).isNull();
        }

        @Test
        @DisplayName("instante sem expediente é carimbo pela metade")
        void so_o_instante() {
            assertThatThrownBy(() -> new Disponibilidade(
                    EstadoDeDisponibilidade.ACABANDO, SABADO_A_01H, null))
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("pela metade");
        }

        @Test
        void so_o_expediente() {
            assertThatThrownBy(() -> new Disponibilidade(
                    EstadoDeDisponibilidade.ACABANDO, null, SEXTA))
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("pela metade");
        }

        @Test
        @DisplayName("ESGOTADO_HOJE sem expediente nunca reativaria — e por isso não nasce")
        void esgotado_hoje_exige_expediente() {
            assertThatThrownBy(() -> new Disponibilidade(
                    EstadoDeDisponibilidade.ESGOTADO_HOJE, null, null))
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("nunca reativa");
        }

        @Test
        @DisplayName("o instante e o expediente divergem toda madrugada — e é de propósito")
        void a_madrugada() {
            Disponibilidade d = Disponibilidade.esgotadoHoje(SABADO_A_01H, SEXTA);

            assertThat(d.marcadoEm().toString()).startsWith("2026-09-26");
            assertThat(d.expedienteDeReferencia())
                    .as("01:00 de sábado ainda é o expediente de sexta (hora de corte 04:00). "
                            + "Quem usasse a data do instante reativaria a calabresa que acabou "
                            + "às 23h, no meio da noite, com a pizzaria vendendo")
                    .isEqualTo(SEXTA);
        }
    }

    @Nested
    class AReativacao {

        @Test
        void c11_esgotado_ontem_reativa_hoje() {
            Disponibilidade d = Disponibilidade.esgotadoHoje(SABADO_A_01H, SEXTA);

            assertThat(d.deveReativarNoExpediente(SABADO)).isTrue();
        }

        @Test
        @DisplayName("o mesmo expediente não reativa — a padaria que abre duas vezes")
        void c11_mesmo_expediente_nao_reativa() {
            Disponibilidade d = Disponibilidade.esgotadoHoje(SABADO_A_01H, SEXTA);

            assertThat(d.deveReativarNoExpediente(SEXTA))
                    .as("o pão que acabou no almoço continua acabado no jantar")
                    .isFalse();
        }

        @Test
        @DisplayName("ESGOTADO_INDETERMINADO não volta sozinho, nem em expediente novo")
        void esgotado_indeterminado_nao_reativa() {
            Disponibilidade d = Disponibilidade.esgotadoIndeterminado(SABADO_A_01H, SEXTA);

            assertThat(d.deveReativarNoExpediente(SABADO)).isFalse();
        }

        @Test
        void disponivel_e_acabando_nao_tem_o_que_reativar() {
            assertThat(Disponibilidade.acabando(SABADO_A_01H, SEXTA)
                    .deveReativarNoExpediente(SABADO)).isFalse();
            assertThat(Disponibilidade.inicial()
                    .deveReativarNoExpediente(SABADO)).isFalse();
        }
    }

    @Nested
    class NoProduto {

        @Test
        @DisplayName("SEM_CONTROLE não acaba: o refrigerante em lata recusa ESGOTADO_HOJE")
        void sem_controle_recusa() {
            Produto lata = ProdutoDeTeste.refrigerante();

            assertThatThrownBy(() -> lata.marcar(
                    Disponibilidade.esgotadoHoje(SABADO_A_01H, SEXTA)))
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("SEM_CONTROLE");

            assertThat(lata.getDisponibilidade().estado())
                    .as("sem a regra, um engano de tela sumiria com o produto do cardápio "
                            + "até a abertura do próximo expediente — por um estado que o "
                            + "modo de controle dele diz não existir")
                    .isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);
        }

        @Test
        void qualitativo_aceita_os_quatro() {
            Produto p = ProdutoDeTeste.margherita();

            p.marcar(Disponibilidade.acabando(SABADO_A_01H, SEXTA));
            assertThat(p.getDisponibilidade().estado()).isEqualTo(EstadoDeDisponibilidade.ACABANDO);

            p.marcar(Disponibilidade.esgotadoHoje(SABADO_A_01H, SEXTA));
            assertThat(p.getDisponibilidade().estado())
                    .isEqualTo(EstadoDeDisponibilidade.ESGOTADO_HOJE);
        }

        @Test
        @DisplayName("marcar não olha publicação: as duas telas são de gente diferente")
        void marcar_rascunho_e_inofensivo() {
            Produto p = ProdutoDeTeste.margherita();

            p.marcar(Disponibilidade.esgotadoHoje(SABADO_A_01H, SEXTA));

            assertThat(p.vendavel()).isFalse();
        }
    }
}
