package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.exception.LojaFicariaSemAdministrador;
import com.deliveryplatform.merchant.domain.exception.PermissaoNaoPossuida;
import com.deliveryplatform.merchant.domain.exception.SemAutoridadeSobreMembro;
import com.deliveryplatform.merchant.domain.model.Equipe;
import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.support.EquipeDeTeste;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.deliveryplatform.merchant.support.EquipeDeTeste.AGORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipeTest {

    private static final UUID LOJA = UUID.randomUUID();
    private static final Instant DEPOIS = AGORA.plusSeconds(3600);

    // ── montagem ────────────────────────────────────────────────────────────

    @Test
    void a_equipe_recusa_membro_de_outra_loja() {
        assertThatThrownBy(() -> Equipe.de(LOJA, List.of(EquipeDeTeste.bia(UUID.randomUUID()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operar_sobre_quem_nao_esta_na_equipe_e_recusado() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli);

        assertThatThrownBy(() -> equipe.remover(marli, bia, DEPOIS))
                .as("sem isso, a demonstração de A3 valeria sobre uma lista que não "
                        + "contém o alvo — correta sobre a equipe errada")
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── suspensão, remoção, reativação ──────────────────────────────────────

    @Test
    void a_dona_suspende_e_reativa_o_gerente() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, junior);

        equipe.suspender(marli, junior, DEPOIS);
        assertThat(junior.getEstado()).isEqualTo(EstadoDoMembro.SUSPENSO);
        assertThat(junior.getAlteradoEm()).isEqualTo(DEPOIS);

        equipe.reativar(marli, junior, DEPOIS);
        assertThat(junior.getEstado()).isEqualTo(EstadoDoMembro.ATIVO);
    }

    @Test
    void o_gerente_remove_a_atendente() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);

        EquipeDeTeste.pizzaria(LOJA, marli, junior, bia).remover(junior, bia, DEPOIS);

        assertThat(bia.getEstado()).isEqualTo(EstadoDoMembro.REMOVIDO);
    }

    @Test
    void removido_volta_pela_reativacao_e_nao_por_um_vinculo_novo() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, bia);

        equipe.remover(marli, bia, DEPOIS);
        equipe.reativar(marli, bia, DEPOIS);

        assertThat(bia.getEstado()).isEqualTo(EstadoDoMembro.ATIVO);
        assertThat(bia.getPermissoes())
                .as("a recontratação reusa o vínculo — o UNIQUE não deixaria criar outro")
                .containsAll(EquipeDeTeste.DA_BIA);
    }

    // ── A3: o teorema, e a prova ────────────────────────────────────────────

    @Test
    void a3_o_penultimo_administrador_sai_sem_problema() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro socio = Membro.fundador(UUID.randomUUID(), LOJA, AGORA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, socio);

        equipe.remover(socio, marli, DEPOIS);

        assertThat(equipe.administradoresAtivos()).isEqualTo(1);
    }

    /**
     * A prova de que A3 não precisa de checagem: com um administrador ativo só,
     * <b>não existe autor possível</b> para mexer nele. Este teste percorre
     * todos os candidatos que a equipe tem.
     */
    @Test
    void a3_e_teorema_ninguem_consegue_tocar_o_ultimo_administrador() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, junior, bia);

        assertThat(equipe.administradoresAtivos()).isEqualTo(1);

        for (Membro candidato : List.of(junior, bia)) {
            assertThatThrownBy(() -> equipe.remover(candidato, marli, DEPOIS))
                    .as("%s tentando remover a última administradora", candidato.getId())
                    .isInstanceOf(SemAutoridadeSobreMembro.class);
            assertThatThrownBy(() -> equipe.suspender(candidato, marli, DEPOIS))
                    .isInstanceOf(SemAutoridadeSobreMembro.class);
            assertThatThrownBy(() -> equipe.rebaixar(candidato, marli, DEPOIS))
                    .isInstanceOf(SemAutoridadeSobreMembro.class);
        }

        assertThatThrownBy(() -> equipe.remover(marli, marli, DEPOIS))
                .as("e ela própria também não — ninguém administra o próprio vínculo, "
                        + "que é a outra metade da demonstração")
                .isInstanceOf(SemAutoridadeSobreMembro.class);

        assertThat(marli.ehAdministradorAtivo()).isTrue();
        assertThat(equipe.administradoresAtivos()).isEqualTo(1);
    }

    @Test
    void a3_alvo_administrador_implica_dois_administradores_ativos() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro socio = Membro.fundador(UUID.randomUUID(), LOJA, AGORA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, socio);

        // A1 aprova: é o único caso em que um administrador pode ser alvo.
        marli.exigirAutoridadeSobre(socio);

        assertThat(equipe.administradoresAtivos())
                .as("autor administrador ativo + alvo administrador ativo + autor ≠ alvo "
                        + "⇒ pelo menos dois. É a demonstração inteira")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void a3_so_conta_administrador_ativo() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro socio = Membro.fundador(UUID.randomUUID(), LOJA, AGORA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, socio);

        assertThat(equipe.administradoresAtivos()).isEqualTo(2);
        equipe.suspender(marli, socio, DEPOIS);

        assertThat(equipe.administradoresAtivos())
                .as("suspenso não administra; a contagem é de quem pode agir, não de "
                        + "quem consta")
                .isEqualTo(1);
    }

    @Test
    void remover_quem_nao_e_administrador_nao_esbarra_em_nada() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);

        EquipeDeTeste.pizzaria(LOJA, marli, bia).remover(marli, bia, DEPOIS);

        assertThat(bia.getEstado()).isEqualTo(EstadoDoMembro.REMOVIDO);
    }

    // ── sair da própria loja: a única que verifica A3 ───────────────────────

    @Test
    void a_atendente_sai_sem_pedir_licenca_a_ninguem() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);

        EquipeDeTeste.pizzaria(LOJA, marli, bia).sair(bia, DEPOIS);

        assertThat(bia.getEstado())
                .as("sair não é administrar: exigir GERENCIAR_EQUIPE aqui prenderia a "
                        + "atendente a uma loja onde ela não trabalha mais")
                .isEqualTo(EstadoDoMembro.REMOVIDO);
        assertThat(bia.getAlteradoEm()).isEqualTo(DEPOIS);
    }

    @Test
    void quem_esta_suspenso_tambem_pode_sair() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, junior);

        equipe.suspender(marli, junior, DEPOIS);
        equipe.sair(junior, DEPOIS);

        assertThat(junior.getEstado())
                .as("a suspensão é ato da loja; sair é ato da pessoa, e uma coisa não "
                        + "cancela a outra")
                .isEqualTo(EstadoDoMembro.REMOVIDO);
    }

    @Test
    void quem_ja_saiu_nao_sai_de_novo() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, bia);

        equipe.sair(bia, DEPOIS);

        assertThatThrownBy(() -> equipe.sair(bia, DEPOIS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void um_administrador_entre_dois_sai_normalmente() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro socio = Membro.fundador(UUID.randomUUID(), LOJA, AGORA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, socio);

        equipe.sair(socio, DEPOIS);

        assertThat(equipe.administradoresAtivos()).isEqualTo(1);
    }

    /** A porta por onde a loja ficaria órfã — e a única checagem de A3 no sistema. */
    @Test
    void a3_o_ultimo_administrador_ativo_nao_consegue_sair() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, junior);

        assertThatThrownBy(() -> equipe.sair(marli, DEPOIS))
                .as("quem está saindo quase sempre não percebeu que era o último")
                .isInstanceOf(LojaFicariaSemAdministrador.class)
                .hasMessageContaining("promova alguém antes de sair");

        assertThat(marli.ehAdministradorAtivo()).isTrue();
        assertThat(equipe.administradoresAtivos()).isEqualTo(1);
    }

    @Test
    void a3_promover_alguem_antes_abre_a_porta_de_saida() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, junior);

        equipe.promover(marli, junior, DEPOIS);
        equipe.sair(marli, DEPOIS);

        assertThat(marli.getEstado())
                .as("é o caminho que a mensagem da recusa manda seguir, e ele precisa "
                        + "funcionar — senão a mensagem é conselho vazio")
                .isEqualTo(EstadoDoMembro.REMOVIDO);
        assertThat(equipe.administradoresAtivos()).isEqualTo(1);
    }

    @Test
    void a3_administrador_suspenso_nao_segura_a_loja_para_quem_quer_sair() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro socio = Membro.fundador(UUID.randomUUID(), LOJA, AGORA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, socio);

        equipe.suspender(marli, socio, DEPOIS);

        assertThatThrownBy(() -> equipe.sair(marli, DEPOIS))
                .as("o suspenso não administra nada; contá-lo deixaria a loja sem quem "
                        + "a administre com M6 satisfeita no papel")
                .isInstanceOf(LojaFicariaSemAdministrador.class);
    }

    @Test
    void a3_nao_impede_o_ultimo_colaborador_de_sair() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);

        EquipeDeTeste.pizzaria(LOJA, marli, bia).sair(bia, DEPOIS);

        assertThat(bia.getEstado()).isEqualTo(EstadoDoMembro.REMOVIDO);
    }

    @Test
    void sair_de_uma_equipe_a_que_nao_se_pertence_e_recusado() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro estranho = EquipeDeTeste.bia(LOJA);

        assertThatThrownBy(() -> EquipeDeTeste.pizzaria(LOJA, marli).sair(estranho, DEPOIS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── promoção e rebaixamento ─────────────────────────────────────────────

    @Test
    void so_um_administrador_promove_a_administrador() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);

        assertThatThrownBy(() -> EquipeDeTeste.pizzaria(LOJA, marli, junior, bia)
                .promover(junior, bia, DEPOIS))
                .as("senão o gerente fabrica um par que o alcance e sai pela porta de cima")
                .isInstanceOf(SemAutoridadeSobreMembro.class);

        EquipeDeTeste.pizzaria(LOJA, marli, junior, bia).promover(marli, bia, DEPOIS);
        assertThat(bia.getPapel()).isEqualTo(Papel.ADMINISTRADOR);
    }

    @Test
    void promover_nao_muda_as_permissoes() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);

        EquipeDeTeste.pizzaria(LOJA, marli, bia).promover(marli, bia, DEPOIS);

        assertThat(bia.getPermissoes())
                .as("papel e permissão são coisas diferentes; promover que concedesse "
                        + "tudo faria do papel um preset — e presets não se guardam")
                .containsExactlyInAnyOrderElementsOf(EquipeDeTeste.DA_BIA);
        assertThat(bia.pode(Permissao.GERENCIAR_EQUIPE)).isFalse();
    }

    // ── A2 nas duas direções ────────────────────────────────────────────────

    @Test
    void conceder_o_que_nao_se_tem_e_recusado() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);

        assertThatThrownBy(() -> EquipeDeTeste.pizzaria(LOJA, marli, junior, bia)
                .alterarPermissoes(junior, bia,
                        EnumSet.of(Permissao.VER_PEDIDO, Permissao.CRIAR_PRODUTO), DEPOIS))
                .isInstanceOf(PermissaoNaoPossuida.class)
                .hasMessageContaining("CRIAR_PRODUTO");
    }

    @Test
    void revogar_o_que_nao_se_tem_tambem_e_recusado() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro bia = Membro.colaborador(
                UUID.randomUUID(), LOJA,
                EnumSet.of(Permissao.VER_PEDIDO, Permissao.CRIAR_PRODUTO), AGORA);

        assertThatThrownBy(() -> EquipeDeTeste.pizzaria(LOJA, marli, junior, bia)
                .alterarPermissoes(junior, bia, EnumSet.of(Permissao.VER_PEDIDO), DEPOIS))
                .as("sem a simetria, um gerente rebaixa colegas até o conjunto vazio "
                        + "usando uma permissão que ele próprio não possui (M5)")
                .isInstanceOf(PermissaoNaoPossuida.class)
                .hasMessageContaining("CRIAR_PRODUTO");
    }

    @Test
    void o_que_nao_muda_nao_se_confere() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro bia = Membro.colaborador(
                UUID.randomUUID(), LOJA,
                EnumSet.of(Permissao.VER_PEDIDO, Permissao.CRIAR_PRODUTO), AGORA);

        EquipeDeTeste.pizzaria(LOJA, marli, junior, bia).alterarPermissoes(junior, bia,
                EnumSet.of(Permissao.VER_PEDIDO, Permissao.CRIAR_PRODUTO, Permissao.VER_VENDAS),
                DEPOIS);

        assertThat(bia.pode(Permissao.VER_VENDAS))
                .as("o CRIAR_PRODUTO que o Júnior não tem ficou parado, então não está "
                        + "sendo dado nem tirado — só a diferença simétrica se confere")
                .isTrue();
    }

    @Test
    void alterar_permissoes_grava_o_instante_recebido() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);

        EquipeDeTeste.pizzaria(LOJA, marli, bia)
                .alterarPermissoes(marli, bia, EnumSet.of(Permissao.VER_PEDIDO), DEPOIS);

        assertThat(bia.getPermissoes()).containsExactly(Permissao.VER_PEDIDO);
        assertThat(bia.getAlteradoEm()).isEqualTo(DEPOIS);
        assertThat(bia.getCriadoEm())
                .as("criadoEm não se mexe")
                .isEqualTo(AGORA);
    }

    // ── leitura ─────────────────────────────────────────────────────────────

    @Test
    void a_equipe_acha_o_vinculo_pelo_usuario() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);
        Equipe equipe = EquipeDeTeste.pizzaria(LOJA, marli, bia);

        assertThat(equipe.doUsuario(bia.getUsuarioId())).contains(bia);
        assertThat(equipe.doUsuario(UUID.randomUUID())).isEmpty();
    }
}
