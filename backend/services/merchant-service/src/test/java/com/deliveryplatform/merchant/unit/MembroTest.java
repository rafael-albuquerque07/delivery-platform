package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.exception.PermissaoNaoPossuida;
import com.deliveryplatform.merchant.domain.exception.SemAutoridadeSobreMembro;
import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.support.EquipeDeTeste;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static com.deliveryplatform.merchant.support.EquipeDeTeste.AGORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembroTest {

    private static final UUID LOJA = UUID.randomUUID();

    // ── o fundador ──────────────────────────────────────────────────────────

    @Test
    void o_fundador_nasce_administrador_ativo_e_com_todas_as_permissoes() {
        Membro marli = EquipeDeTeste.marli(LOJA);

        assertThat(marli.getPapel()).isEqualTo(Papel.ADMINISTRADOR);
        assertThat(marli.getEstado()).isEqualTo(EstadoDoMembro.ATIVO);
        assertThat(marli.getPermissoes())
                .as("papel não é lista de permissão nenhuma: administrador com o conjunto "
                        + "vazio satisfaz M6 e não consegue fazer nada")
                .containsExactlyInAnyOrder(Permissao.values());
    }

    @Test
    void o_colaborador_nasce_ativo_e_nunca_administrador() {
        Membro bia = EquipeDeTeste.bia(LOJA);

        assertThat(bia.getPapel())
                .as("convite nunca concede ADMINISTRADOR — promoção é ato separado")
                .isEqualTo(Papel.COLABORADOR);
        assertThat(bia.ativo()).isTrue();
    }

    // ── a pergunta que todo serviço faz ─────────────────────────────────────

    @Test
    void pode_exige_permissao_e_vinculo_ativo() {
        Membro bia = EquipeDeTeste.bia(LOJA);

        assertThat(bia.pode(Permissao.VER_PEDIDO)).isTrue();
        assertThat(bia.pode(Permissao.VER_VENDAS)).isFalse();
    }

    @Test
    void membro_suspenso_nao_pode_nada_e_nao_perde_o_que_tinha() {
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro marli = EquipeDeTeste.marli(LOJA);
        EquipeDeTeste.pizzaria(LOJA, marli, junior).suspender(marli, junior, AGORA);

        assertThat(junior.pode(Permissao.VER_VENDAS))
                .as("é o estado que decide, não a lista")
                .isFalse();
        assertThat(junior.getPermissoes())
                .as("zerar a lista pareceria mais seguro e destruiria a informação "
                        + "necessária para reativar alguém do jeito que ele era")
                .containsAll(EquipeDeTeste.DO_JUNIOR);
    }

    @Test
    void a_lista_devolvida_e_copia() {
        Membro bia = EquipeDeTeste.bia(LOJA);
        Set<Permissao> lidas = bia.getPermissoes();

        lidas.add(Permissao.VER_VENDAS);

        assertThat(bia.pode(Permissao.VER_VENDAS))
                .as("sem a cópia, quem lê o vínculo concede permissão a si mesmo")
                .isFalse();
    }

    // ── A1 ──────────────────────────────────────────────────────────────────

    @Test
    void quem_tem_gerenciar_equipe_administra_colaborador() {
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);

        junior.exigirAutoridadeSobre(bia);
    }

    @Test
    void a1_gerenciar_equipe_sozinha_nao_alcanca_administrador() {
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro marli = EquipeDeTeste.marli(LOJA);

        assertThatThrownBy(() -> junior.exigirAutoridadeSobre(marli))
                .as("é a escalada de privilégio que H2.2 nomeia: o gerente remove a dona")
                .isInstanceOf(SemAutoridadeSobreMembro.class)
                .hasMessageContaining("M3");
    }

    @Test
    void administrador_com_gerenciar_equipe_alcanca_outro_administrador() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro socio = Membro.fundador(UUID.randomUUID(), LOJA, AGORA);

        marli.exigirAutoridadeSobre(socio);
    }

    @Test
    void sem_gerenciar_equipe_nao_se_administra_ninguem() {
        Membro bia = EquipeDeTeste.bia(LOJA);
        Membro outra = EquipeDeTeste.bia(LOJA);

        assertThatThrownBy(() -> bia.exigirAutoridadeSobre(outra))
                .as("nem o papel concede sozinho — toda autorização real é item a item")
                .isInstanceOf(SemAutoridadeSobreMembro.class)
                .hasMessageContaining("GERENCIAR_EQUIPE");
    }

    @Test
    void administrador_sem_gerenciar_equipe_tambem_nao() {
        Membro semAPermissao = Membro.reconstituir(
                UUID.randomUUID(), UUID.randomUUID(), LOJA,
                Papel.ADMINISTRADOR, EnumSet.of(Permissao.VER_VENDAS),
                EstadoDoMembro.ATIVO, AGORA, AGORA);

        assertThatThrownBy(() -> semAPermissao.exigirAutoridadeSobre(EquipeDeTeste.bia(LOJA)))
                .isInstanceOf(SemAutoridadeSobreMembro.class);
    }

    @Test
    void suspenso_nao_administra_ninguem() {
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro bia = EquipeDeTeste.bia(LOJA);
        EquipeDeTeste.pizzaria(LOJA, marli, junior, bia).suspender(marli, junior, AGORA);

        assertThatThrownBy(() -> junior.exigirAutoridadeSobre(bia))
                .as("suspenso que ainda administra equipe faz da suspensão um enfeite")
                .isInstanceOf(SemAutoridadeSobreMembro.class);
    }

    @Test
    void ninguem_administra_o_proprio_vinculo() {
        Membro junior = EquipeDeTeste.junior(LOJA);

        assertThatThrownBy(() -> junior.exigirAutoridadeSobre(junior))
                .isInstanceOf(SemAutoridadeSobreMembro.class);
    }

    @Test
    void nao_se_administra_membro_de_outra_loja() {
        Membro junior = EquipeDeTeste.junior(LOJA);
        Membro deOutraLoja = EquipeDeTeste.bia(UUID.randomUUID());

        assertThatThrownBy(() -> junior.exigirAutoridadeSobre(deOutraLoja))
                .as("é o vazamento entre lojas que M1 existe para impedir")
                .isInstanceOf(SemAutoridadeSobreMembro.class);
    }

    // ── A2 ──────────────────────────────────────────────────────────────────

    @Test
    void exigir_que_possui_aceita_subconjunto_e_recusa_o_resto() {
        Membro junior = EquipeDeTeste.junior(LOJA);

        junior.exigirQuePossui(EnumSet.of(Permissao.VER_VENDAS));
        junior.exigirQuePossui(EnumSet.noneOf(Permissao.class));

        assertThatThrownBy(() -> junior.exigirQuePossui(EnumSet.of(Permissao.CRIAR_PRODUTO)))
                .isInstanceOf(PermissaoNaoPossuida.class)
                .hasMessageContaining("CRIAR_PRODUTO");
    }

    @Test
    void a_recusa_nomeia_todas_as_faltantes_de_uma_vez() {
        assertThatThrownBy(() -> EquipeDeTeste.junior(LOJA).exigirQuePossui(
                EnumSet.of(Permissao.CRIAR_PRODUTO, Permissao.DESATIVAR_PRODUTO)))
                .as("corrigir um erro por vez é como o convite errado volta cinco vezes")
                .isInstanceOf(PermissaoNaoPossuida.class)
                .hasMessageContaining("CRIAR_PRODUTO")
                .hasMessageContaining("DESATIVAR_PRODUTO");
    }

    // ── vazamento ───────────────────────────────────────────────────────────

    @Test
    void to_string_nao_lista_permissao_nem_conhece_pessoa() {
        String texto = EquipeDeTeste.junior(LOJA).toString();

        assertThat(texto).doesNotContain("GERENCIAR_EQUIPE");
        assertThat(texto).contains("permissoes=4");
    }
}
