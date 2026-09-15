package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.exception.ConviteInvalido;
import com.deliveryplatform.merchant.domain.exception.JaPertenceAEquipe;
import com.deliveryplatform.merchant.domain.exception.PermissaoNaoPossuida;
import com.deliveryplatform.merchant.domain.exception.SemAutoridadeSobreMembro;
import com.deliveryplatform.merchant.domain.model.Convite;
import com.deliveryplatform.merchant.domain.model.EstadoDoConvite;
import com.deliveryplatform.merchant.domain.model.Equipe;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.domain.model.Telefone;
import com.deliveryplatform.merchant.support.EquipeDeTeste;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import static com.deliveryplatform.merchant.support.EquipeDeTeste.AGORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O convite visto da equipe: quem pode emitir, quem pode cancelar, e o aceite —
 * que é onde A2 é verificada <b>pela segunda vez</b>.
 */
class EquipeConviteTest {

    private static final UUID LOJA = UUID.randomUUID();
    private static final Instant DEPOIS = AGORA.plusSeconds(3600);

    /** O telefone do Rodrigo, que a Marli quer chamar para o balcão. */
    private static final Telefone RODRIGO = Telefone.de("11955554444");

    // ── convidar: A2 pela primeira vez ──────────────────────────────────────

    @Test
    void o_gerente_convida_dentro_do_que_ele_proprio_tem() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);

        Convite convite = EquipeDeTeste.pizzaria(LOJA, marli, junior)
                .convidar(junior, RODRIGO, EnumSet.of(Permissao.VER_VENDAS), AGORA);

        assertThat(convite.getEstabelecimentoId()).isEqualTo(LOJA);
        assertThat(convite.getConvidadoPor()).isEqualTo(junior.getId());
        assertThat(convite.getPermissoesOferecidas()).containsExactly(Permissao.VER_VENDAS);
    }

    @Test
    void a2_ninguem_convida_oferecendo_o_que_nao_tem() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, junior);

        assertThatThrownBy(() -> equipe.convidar(
                junior, RODRIGO, EnumSet.of(Permissao.CRIAR_PRODUTO), AGORA))
                .isInstanceOf(PermissaoNaoPossuida.class)
                .hasMessageContaining("CRIAR_PRODUTO");
    }

    @Test
    void sem_gerenciar_equipe_nao_se_convida_ninguem() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, bia);

        assertThatThrownBy(() -> equipe.convidar(
                bia, RODRIGO, EnumSet.noneOf(Permissao.class), AGORA))
                .isInstanceOf(SemAutoridadeSobreMembro.class);
    }

    @Test
    void suspenso_nao_convida() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, junior);

        equipe.suspender(marli, junior, DEPOIS);

        assertThatThrownBy(() -> equipe.convidar(
                junior, RODRIGO, EnumSet.of(Permissao.VER_VENDAS), DEPOIS))
                .as("suspenso que ainda enche a loja de convites faz da suspensão um enfeite")
                .isInstanceOf(SemAutoridadeSobreMembro.class);
    }

    // ── aceitar ─────────────────────────────────────────────────────────────

    @Test
    void o_aceite_cria_o_vinculo_colaborador_e_consome_o_convite() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli);
        Convite convite = equipe.convidar(marli, RODRIGO, EquipeDeTeste.DA_BIA, AGORA);
        UUID rodrigo = UUID.randomUUID();

        Membro vinculo = equipe.aceitar(convite, convite.getToken(), rodrigo, DEPOIS);

        assertThat(vinculo.getPapel())
                .as("convite nunca concede ADMINISTRADOR, e não por checagem: o aceite "
                        + "chama Membro.colaborador e não há outro caminho")
                .isEqualTo(Papel.COLABORADOR);
        assertThat(vinculo.ativo()).isTrue();
        assertThat(vinculo.getPermissoes())
                .containsExactlyInAnyOrderElementsOf(EquipeDeTeste.DA_BIA);
        assertThat(vinculo.getCriadoEm()).isEqualTo(DEPOIS);

        assertThat(convite.getEstado()).isEqualTo(EstadoDoConvite.ACEITO);
        assertThat(convite.getAceitoEm()).isEqualTo(DEPOIS);
        assertThat(equipe.doUsuario(rodrigo)).contains(vinculo);
        assertThat(equipe.administradoresAtivos()).isEqualTo(1);
    }

    @Test
    void token_errado_expirado_ou_ja_usado_recebem_a_mesma_recusa() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli);
        Convite convite = equipe.convidar(marli, RODRIGO, EquipeDeTeste.DA_BIA, AGORA);

        assertThatThrownBy(() -> equipe.aceitar(convite, "chute", UUID.randomUUID(), DEPOIS))
                .isInstanceOf(ConviteInvalido.class);
        assertThatThrownBy(() -> equipe.aceitar(convite, convite.getToken(),
                UUID.randomUUID(), AGORA.plus(Duration.ofDays(8))))
                .isInstanceOf(ConviteInvalido.class);

        assertThat(convite.getEstado())
                .as("uma recusa não pode gastar o convite de quem tem o token certo")
                .isEqualTo(EstadoDoConvite.PENDENTE);

        equipe.aceitar(convite, convite.getToken(), UUID.randomUUID(), DEPOIS);
        assertThatThrownBy(() -> equipe.aceitar(
                convite, convite.getToken(), UUID.randomUUID(), DEPOIS))
                .as("uso único: o segundo a chegar com o mesmo token não entra")
                .isInstanceOf(ConviteInvalido.class);
    }

    @Test
    void convite_de_outra_loja_nao_entra_por_esta_equipe() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Convite deOutraLoja = Convite.novo(
                UUID.randomUUID(), RODRIGO, EquipeDeTeste.DA_BIA, marli.getId(), AGORA);

        assertThatThrownBy(() -> EquipeDeTeste.pizzaria(LOJA, marli)
                .aceitar(deOutraLoja, deOutraLoja.getToken(), UUID.randomUUID(), DEPOIS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── A2 pela segunda vez: a que se esquece ───────────────────────────────

    @Test
    void a2_o_convidante_perdeu_a_permissao_entre_o_convite_e_o_aceite() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, junior);

        Convite convite = equipe.convidar(junior, RODRIGO, EnumSet.of(Permissao.VER_VENDAS), AGORA);
        equipe.alterarPermissoes(marli, junior, EnumSet.of(Permissao.GERENCIAR_EQUIPE), DEPOIS);

        assertThatThrownBy(() -> equipe.aceitar(
                convite, convite.getToken(), UUID.randomUUID(), DEPOIS))
                .as("validar só na emissão deixa um convite virar um privilégio que "
                        + "ninguém mais tem autoridade para dar")
                .isInstanceOf(PermissaoNaoPossuida.class)
                .hasMessageContaining("VER_VENDAS");
    }

    @Test
    void a2_o_convidante_foi_suspenso_ou_saiu() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, junior);
        Convite convite = equipe.convidar(junior, RODRIGO, EnumSet.of(Permissao.VER_VENDAS), AGORA);

        equipe.suspender(marli, junior, DEPOIS);
        assertThatThrownBy(() -> equipe.aceitar(
                convite, convite.getToken(), UUID.randomUUID(), DEPOIS))
                .isInstanceOf(SemAutoridadeSobreMembro.class);

        Membro outraMarli = EquipeDeTeste.marli(LOJA);
        Membro outroJunior = EquipeDeTeste.junior(LOJA);
        Equipe outra = EquipeDeTeste.pizzaria(LOJA, outraMarli, outroJunior);
        Convite doQueSaiu = outra.convidar(
                outroJunior, RODRIGO, EnumSet.of(Permissao.VER_VENDAS), AGORA);

        outra.remover(outraMarli, outroJunior, DEPOIS);
        assertThatThrownBy(() -> outra.aceitar(
                doQueSaiu, doQueSaiu.getToken(), UUID.randomUUID(), DEPOIS))
                .as("o convite continuaria de pé, assinado por um vínculo que já não existe")
                .isInstanceOf(SemAutoridadeSobreMembro.class);
    }

    // ── quem volta, volta colaborador ───────────────────────────────────────

    @Test
    void reconvidar_quem_saiu_reusa_o_vinculo_e_o_rebaixa() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro exAdministrador = Membro.fundador(UUID.randomUUID(), LOJA, AGORA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, junior, exAdministrador);

        equipe.remover(marli, exAdministrador, DEPOIS);
        Convite convite = equipe.convidar(junior, RODRIGO, EnumSet.of(Permissao.VER_VENDAS), DEPOIS);
        Membro voltou = equipe.aceitar(
                convite, convite.getToken(), exAdministrador.getUsuarioId(), DEPOIS);

        assertThat(voltou.getId())
                .as("o UNIQUE (usuario_id, estabelecimento_id) não deixaria criar outro")
                .isEqualTo(exAdministrador.getId());
        assertThat(voltou.ativo()).isTrue();
        assertThat(voltou.getPapel())
                .as("sem o rebaixamento, um gerente restaura um ADMINISTRADOR removido "
                        + "sem nenhum administrador na jogada — pareceria gentileza e "
                        + "seria escalada")
                .isEqualTo(Papel.COLABORADOR);
        assertThat(voltou.getPermissoes()).containsExactly(Permissao.VER_VENDAS);
        assertThat(equipe.getMembros()).hasSize(3);
    }

    @Test
    void quem_ja_tem_vinculo_ativo_nao_aceita_convite() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, bia);
        Convite convite = equipe.convidar(marli, RODRIGO, EquipeDeTeste.DA_BIA, AGORA);

        assertThatThrownBy(() -> equipe.aceitar(
                convite, convite.getToken(), bia.getUsuarioId(), DEPOIS))
                .as("a colisão só aparece aqui: o convite endereça um telefone, e "
                        + "traduzir telefone em usuário é dado do identity-service")
                .isInstanceOf(JaPertenceAEquipe.class);

        assertThat(convite.getEstado()).isEqualTo(EstadoDoConvite.PENDENTE);
    }

    // ── cancelar ────────────────────────────────────────────────────────────

    @Test
    void cancelar_impede_o_aceite_e_so_acontece_uma_vez() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli);
        Convite convite = equipe.convidar(marli, RODRIGO, EquipeDeTeste.DA_BIA, AGORA);

        equipe.cancelarConvite(marli, convite);

        assertThat(convite.getEstado()).isEqualTo(EstadoDoConvite.CANCELADO);
        assertThatThrownBy(() -> equipe.aceitar(
                convite, convite.getToken(), UUID.randomUUID(), DEPOIS))
                .isInstanceOf(ConviteInvalido.class);
        assertThatThrownBy(() -> equipe.cancelarConvite(marli, convite))
                .isInstanceOf(ConviteInvalido.class);
    }

    @Test
    void sem_gerenciar_equipe_nao_se_cancela_convite() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, bia);
        Convite convite = equipe.convidar(marli, RODRIGO, EquipeDeTeste.DA_BIA, AGORA);

        assertThatThrownBy(() -> equipe.cancelarConvite(bia, convite))
                .isInstanceOf(SemAutoridadeSobreMembro.class);
    }

    // ── A1 não mudou ────────────────────────────────────────────────────────

    @Test
    void a_extracao_de_exigirPodeGerenciarEquipeDe_nao_afrouxou_a1() {
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro marli = EquipeDeTeste.marli(LOJA);

        assertThatThrownBy(() -> junior.exigirAutoridadeSobre(marli))
                .isInstanceOf(SemAutoridadeSobreMembro.class)
                .hasMessageContaining("M3");
        assertThatThrownBy(() -> junior.exigirAutoridadeSobre(junior))
                .isInstanceOf(SemAutoridadeSobreMembro.class);
        assertThatThrownBy(() -> junior.exigirAutoridadeSobre(
                EquipeDeTeste.bia(UUID.randomUUID())))
                .isInstanceOf(SemAutoridadeSobreMembro.class);

        junior.exigirAutoridadeSobre(EquipeDeTeste.bia(LOJA));
    }
}
