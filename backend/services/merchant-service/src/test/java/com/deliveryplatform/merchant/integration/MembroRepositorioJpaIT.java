package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Equipe;
import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.support.EquipeDeTeste;
import com.deliveryplatform.merchant.support.Infraestrutura;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O vínculo contra PostgreSQL de verdade — mesmas razões do
 * {@code EstabelecimentoRepositorioJpaIT}, e os mesmos contêineres, que vêm da
 * {@link Infraestrutura}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "delivery.outbox.habilitado=false")
@Transactional
class MembroRepositorioJpaIT extends Infraestrutura {

    @Autowired
    private MembroRepositorio membros;

    @Autowired
    private EstabelecimentoRepositorio lojas;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID loja;

    /** Truncado em microssegundos: é a resolução do {@code TIMESTAMPTZ}. */
    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @BeforeEach
    void criarALoja() {
        loja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        entityManager.flush();
    }

    @Test
    void salva_e_recupera_preservando_papel_estado_e_permissoes() {
        Membro salvo = membros.salvar(Membro.colaborador(
                UUID.randomUUID(), loja, EquipeDeTeste.DO_JUNIOR, agora()));
        entityManager.flush();
        entityManager.clear();

        Membro lido = membros
                .buscarPorUsuarioELoja(salvo.getUsuarioId(), loja)
                .orElseThrow();

        assertThat(lido.getId()).isEqualTo(salvo.getId());
        assertThat(lido.getPapel()).isEqualTo(Papel.COLABORADOR);
        assertThat(lido.getEstado()).isEqualTo(EstadoDoMembro.ATIVO);
        assertThat(lido.getPermissoes())
                .containsExactlyInAnyOrderElementsOf(EquipeDeTeste.DO_JUNIOR);
        assertThat(lido.getCriadoEm()).isEqualTo(salvo.getCriadoEm());
    }

    @Test
    void o_fundador_leva_as_dez_permissoes_de_ida_e_de_volta() {
        Membro marli = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora()));
        entityManager.flush();
        entityManager.clear();

        assertThat(membros.buscarPorUsuarioELoja(marli.getUsuarioId(), loja).orElseThrow()
                .getPermissoes())
                .as("a tabela de coleção precisa aguentar o conjunto inteiro, que é o "
                        + "caso de quem cadastrou a loja")
                .containsExactlyInAnyOrder(Permissao.values());
    }

    @Test
    void membro_sem_permissao_nenhuma_e_valido() {
        Membro sem = membros.salvar(Membro.colaborador(
                UUID.randomUUID(), loja, EnumSet.noneOf(Permissao.class), agora()));
        entityManager.flush();
        entityManager.clear();

        assertThat(membros.buscarPorUsuarioELoja(sem.getUsuarioId(), loja).orElseThrow()
                .getPermissoes())
                .as("é o estado de quem foi convidado sem nada marcado na tela — recusar "
                        + "aqui seria inventar regra que documento nenhum pede")
                .isEmpty();
    }

    @Test
    void o_mesmo_usuario_tem_vinculo_em_duas_lojas() {
        UUID outraLoja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        UUID usuario = UUID.randomUUID();

        membros.salvar(Membro.fundador(usuario, loja, agora()));
        membros.salvar(Membro.colaborador(usuario, outraLoja, EquipeDeTeste.DA_BIA, agora()));
        entityManager.flush();
        entityManager.clear();

        assertThat(membros.buscarPorUsuarioELoja(usuario, loja).orElseThrow().ehAdministrador())
                .as("administradora numa loja e atendente na outra — é M1 em uma linha, "
                        + "e o identity-service não precisa aprender nada disso")
                .isTrue();
        assertThat(membros.buscarPorUsuarioELoja(usuario, outraLoja).orElseThrow()
                .ehAdministrador())
                .isFalse();
    }

    @Test
    void dois_vinculos_do_mesmo_par_sao_recusados_pelo_banco() {
        UUID usuario = UUID.randomUUID();
        membros.salvar(Membro.fundador(usuario, loja, agora()));
        entityManager.flush();

        membros.salvar(Membro.colaborador(usuario, loja, EquipeDeTeste.DA_BIA, agora()));

        assertThatThrownBy(() -> entityManager.flush())
                .as("é o que faz REMOVIDO ser estado e não exclusão: recontratar reusa "
                        + "a linha em vez de criar uma segunda")
                .hasStackTraceContaining("uq_membro_usuario_loja");
    }

    @Test
    void vinculo_para_loja_inexistente_e_recusado_pelo_banco() {
        membros.salvar(Membro.fundador(UUID.randomUUID(), UUID.randomUUID(), agora()));

        assertThatThrownBy(() -> entityManager.flush())
                .as("a chave estrangeira para estabelecimento existe porque um vínculo "
                        + "para uma loja que não existe não é estado válido de agregado "
                        + "nenhum — é lixo")
                .hasStackTraceContaining("membro_estabelecimento_id_fkey");
    }

    @Test
    void usuario_inexistente_nao_e_recusado_e_isso_e_de_proposito() {
        membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora()));

        entityManager.flush();

        // Nenhuma exceção: `usuario` mora no banco do identity-service, e uma
        // chave estrangeira daqui para lá seria o primeiro passo para os dois
        // esquemas virarem um só.
    }

    @Test
    void a_equipe_para_alteracao_traz_todos_os_vinculos_da_loja_e_so_deles() {
        Membro marli = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora()));
        Membro bia = membros.salvar(Membro.colaborador(
                UUID.randomUUID(), loja, EquipeDeTeste.DA_BIA, agora()));

        UUID outraLoja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        membros.salvar(Membro.fundador(UUID.randomUUID(), outraLoja, agora()));
        entityManager.flush();
        entityManager.clear();

        Equipe equipe = membros.equipeParaAlteracao(loja);

        assertThat(equipe.getMembros()).hasSize(2);
        assertThat(equipe.doUsuario(marli.getUsuarioId())).isPresent();
        assertThat(equipe.doUsuario(bia.getUsuarioId())).isPresent();
        assertThat(equipe.administradoresAtivos()).isEqualTo(1);
    }

    /**
     * O cadeado precisa <b>rodar</b>, e não só existir.
     *
     * <p>Este teste não prova a exclusão mútua — isso exigiria duas conexões e
     * um relógio, e é o caso de contenção que o {@code APLICAR} descreve como o
     * único de risco desta rodada. O que ele prova é o que mais falharia em
     * silêncio: que a consulta nativa {@code for update} <b>é aceita pelo
     * PostgreSQL</b> sobre a linha de {@code estabelecimento}.
     *
     * <p>A forma idiomática — {@code @Lock(PESSIMISTIC_WRITE)} sobre a entidade
     * do estabelecimento — falharia aqui, e não por lentidão: aquela entidade
     * tem cinco {@code @ElementCollection} {@code EAGER}, a consulta sairia com
     * cinco {@code left join}, e o PostgreSQL recusa {@code FOR UPDATE} sobre o
     * lado anulável de um {@code LEFT JOIN}. Seria erro em tempo de execução,
     * só visível quando duas pessoas mexessem na equipe ao mesmo tempo.
     */
    @Test
    void o_cadeado_da_loja_e_aceito_pelo_banco() {
        membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora()));
        entityManager.flush();

        Equipe equipe = membros.equipeParaAlteracao(loja);

        assertThat(equipe.getEstabelecimentoId()).isEqualTo(loja);
        assertThat(equipe.getMembros()).hasSize(1);
    }

    @Test
    void equipe_de_loja_sem_ninguem_vem_vazia_em_vez_de_falhar() {
        Equipe equipe = membros.equipeParaAlteracao(loja);

        assertThat(equipe.getMembros()).isEmpty();
        assertThat(equipe.administradoresAtivos()).isZero();
    }

    @Test
    void a_alteracao_feita_pela_equipe_e_persistida() {
        Membro marli = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora()));
        Membro bia = membros.salvar(Membro.colaborador(
                UUID.randomUUID(), loja, EquipeDeTeste.DA_BIA, agora()));
        entityManager.flush();
        entityManager.clear();

        Equipe equipe = membros.equipeParaAlteracao(loja);
        Membro autor = equipe.doUsuario(marli.getUsuarioId()).orElseThrow();
        Membro alvo = equipe.doUsuario(bia.getUsuarioId()).orElseThrow();

        Instant quando = agora();
        equipe.alterarPermissoes(autor, alvo, EnumSet.of(Permissao.VER_VENDAS), quando);
        membros.salvar(alvo);
        entityManager.flush();
        entityManager.clear();

        Membro lido = membros.buscarPorUsuarioELoja(bia.getUsuarioId(), loja).orElseThrow();
        assertThat(lido.getPermissoes()).containsExactly(Permissao.VER_VENDAS);
        assertThat(lido.getAlteradoEm()).isEqualTo(quando);
    }

    @Test
    void remover_nao_apaga_a_linha() {
        Membro marli = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora()));
        Membro bia = membros.salvar(Membro.colaborador(
                UUID.randomUUID(), loja, EquipeDeTeste.DA_BIA, agora()));
        entityManager.flush();
        entityManager.clear();

        Equipe equipe = membros.equipeParaAlteracao(loja);
        equipe.remover(
                equipe.doUsuario(marli.getUsuarioId()).orElseThrow(),
                equipe.doUsuario(bia.getUsuarioId()).orElseThrow(),
                agora());
        membros.salvar(equipe.doUsuario(bia.getUsuarioId()).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(membros.buscarPorUsuarioELoja(bia.getUsuarioId(), loja).orElseThrow()
                .getEstado())
                .as("o vínculo é registro de responsabilidade: quem teve acesso àquela "
                        + "loja e quando")
                .isEqualTo(EstadoDoMembro.REMOVIDO);
    }
}
