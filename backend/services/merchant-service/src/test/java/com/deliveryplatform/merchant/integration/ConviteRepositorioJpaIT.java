package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.ConviteRepositorio;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Convite;
import com.deliveryplatform.merchant.domain.model.EstadoDoConvite;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.domain.model.Telefone;
import com.deliveryplatform.merchant.support.EquipeDeTeste;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O convite contra PostgreSQL de verdade — mesmas razões e mesmas duas
 * propriedades de RabbitMQ do {@code EstabelecimentoRepositorioJpaIT}.
 *
 * <p>As asserções de constraint usam {@code hasStackTraceContaining} e não
 * {@code DataIntegrityViolationException}: um {@code entityManager.flush()}
 * chamado direto não passa pela tradução de exceção do Spring, que só age sobre
 * beans {@code @Repository}. É a lição que a B1 pagou.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.rabbitmq.username=teste",
        "spring.rabbitmq.password=teste"
})
@Testcontainers
@Transactional
class ConviteRepositorioJpaIT {

    private static final Telefone RODRIGO = Telefone.de("11955554444");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private ConviteRepositorio convites;

    @Autowired
    private MembroRepositorio membros;

    @Autowired
    private EstabelecimentoRepositorio lojas;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID loja;
    private UUID quemConvidou;

    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @BeforeEach
    void umaLojaComUmaAdministradora() {
        loja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        quemConvidou = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora())).getId();
        entityManager.flush();
    }

    private Convite convite() {
        return Convite.novo(loja, RODRIGO, EquipeDeTeste.DA_BIA, quemConvidou, agora());
    }

    @Test
    void salva_e_recupera_pelo_token_preservando_tudo() {
        Convite salvo = convites.salvar(convite());
        entityManager.flush();
        entityManager.clear();

        Convite lido = convites.buscarPorToken(salvo.getToken()).orElseThrow();

        assertThat(lido.getId()).isEqualTo(salvo.getId());
        assertThat(lido.getEstabelecimentoId()).isEqualTo(loja);
        assertThat(lido.getTelefone()).isEqualTo(RODRIGO);
        assertThat(lido.getConvidadoPor()).isEqualTo(quemConvidou);
        assertThat(lido.getEstado()).isEqualTo(EstadoDoConvite.PENDENTE);
        assertThat(lido.getAceitoEm()).isNull();
        assertThat(lido.getPermissoesOferecidas())
                .containsExactlyInAnyOrderElementsOf(EquipeDeTeste.DA_BIA);
        assertThat(lido.getExpiraEm()).isEqualTo(salvo.getExpiraEm());
    }

    @Test
    void token_desconhecido_e_token_vazio_devolvem_vazio() {
        convites.salvar(convite());
        entityManager.flush();

        assertThat(convites.buscarPorToken("nao-existe")).isEmpty();
        assertThat(convites.buscarPorToken(""))
                .as("procurar por vazio é erro de quem chama, e devolver o primeiro "
                        + "convite da tabela seria pior que não achar nada")
                .isEmpty();
        assertThat(convites.buscarPorToken(null)).isEmpty();
    }

    @Test
    void convite_sem_permissao_nenhuma_atravessa_o_banco() {
        Convite vazio = convites.salvar(Convite.novo(
                loja, RODRIGO, EnumSet.noneOf(Permissao.class), quemConvidou, agora()));
        entityManager.flush();
        entityManager.clear();

        assertThat(convites.buscarPorToken(vazio.getToken()).orElseThrow()
                .getPermissoesOferecidas())
                .isEmpty();
    }

    // ── o que o banco recusa ────────────────────────────────────────────────

    @Test
    void dois_pendentes_para_o_mesmo_telefone_na_mesma_loja_sao_recusados() {
        convites.salvar(convite());
        entityManager.flush();

        convites.salvar(convite());

        assertThatThrownBy(() -> entityManager.flush())
                .as("senão a pessoa recebe três tokens válidos e usa o que achar primeiro")
                .hasStackTraceContaining("uq_convite_pendente_por_telefone");
    }

    @Test
    void o_indice_parcial_deixa_reconvidar_depois_de_aceito() {
        Convite primeiro = convite();
        Instant quando = agora();
        convites.salvar(Convite.reconstituir(
                primeiro.getId(), loja, RODRIGO, primeiro.getToken(),
                EquipeDeTeste.DA_BIA, quemConvidou,
                EstadoDoConvite.ACEITO, quando, quando.plus(Duration.ofDays(7)), quando));
        entityManager.flush();

        convites.salvar(convite());
        entityManager.flush();

        assertThat(convites.pendentesDe(loja))
                .as("o histórico de quem foi chamado e quando continua na tabela; só a "
                        + "pendência é única")
                .hasSize(1);
    }

    @Test
    void o_token_e_unico_no_repositorio_inteiro() {
        Convite primeiro = convites.salvar(convite());
        entityManager.flush();

        UUID outraLoja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        UUID outroMembro = membros.salvar(
                Membro.fundador(UUID.randomUUID(), outraLoja, agora())).getId();
        Instant quando = agora();
        convites.salvar(Convite.reconstituir(
                UUID.randomUUID(), outraLoja, RODRIGO, primeiro.getToken(),
                EquipeDeTeste.DA_BIA, outroMembro,
                EstadoDoConvite.PENDENTE, quando, quando.plus(Duration.ofDays(7)), null));

        assertThatThrownBy(() -> entityManager.flush())
                .as("o token é a autorização; repetido, ele abre duas lojas")
                .hasStackTraceContaining("uq_convite_token");
    }

    @Test
    void convite_de_um_vinculo_que_nao_existe_e_recusado() {
        convites.salvar(Convite.novo(
                loja, RODRIGO, EquipeDeTeste.DA_BIA, UUID.randomUUID(), agora()));

        assertThatThrownBy(() -> entityManager.flush())
                .as("quem convidou tem de ser da casa, e o banco confere isso sozinho")
                .hasStackTraceContaining("convite_convidado_por_fkey");
    }

    @Test
    void o_check_de_aceito_sem_data_esta_na_migration() {
        convites.salvar(convite());
        entityManager.flush();

        // O domínio recusa antes de chegar aqui, então a única forma de provar
        // que a constraint existe é escrever por baixo dele.
        assertThatThrownBy(() -> {
            entityManager
                    .createNativeQuery("UPDATE convite SET estado = 'ACEITO'")
                    .executeUpdate();
            entityManager.flush();
        })
                .as("um aceite sem quando")
                .hasStackTraceContaining("ck_convite_aceito_tem_data");
    }

    // ── o que o repositório não decide ──────────────────────────────────────

    @Test
    void pendentes_nao_filtra_expirado_porque_isso_e_do_dominio() {
        Instant velho = agora().minus(Duration.ofDays(30));
        convites.salvar(Convite.reconstituir(
                UUID.randomUUID(), loja, RODRIGO, "token-velho", EquipeDeTeste.DA_BIA,
                quemConvidou, EstadoDoConvite.PENDENTE, velho,
                velho.plus(Duration.ofDays(7)), null));
        entityManager.flush();
        entityManager.clear();

        Convite lido = convites.pendentesDe(loja).getFirst();

        assertThat(lido.getEstado()).isEqualTo(EstadoDoConvite.PENDENTE);
        assertThat(lido.expirado(Instant.now()))
                .as("um filtro no SQL seria uma segunda definição de \"pendente\", que "
                        + "envelheceria sozinha no dia em que a validade mudasse")
                .isTrue();
    }

    @Test
    void o_aceite_atravessa_o_banco_com_o_vinculo_novo() {
        Convite salvo = convites.salvar(convite());
        entityManager.flush();
        entityManager.clear();

        Instant quando = agora();
        var equipe = membros.equipeParaAlteracao(loja);
        Convite lido = convites.buscarPorToken(salvo.getToken()).orElseThrow();
        UUID rodrigo = UUID.randomUUID();

        Membro vinculo = equipe.aceitar(lido, lido.getToken(), rodrigo, quando);
        membros.salvar(vinculo);
        convites.salvar(lido);
        entityManager.flush();
        entityManager.clear();

        assertThat(membros.buscarPorUsuarioELoja(rodrigo, loja).orElseThrow().getPermissoes())
                .containsExactlyInAnyOrderElementsOf(EquipeDeTeste.DA_BIA);
        assertThat(convites.buscarPorToken(salvo.getToken()).orElseThrow().getEstado())
                .isEqualTo(EstadoDoConvite.ACEITO);
        assertThat(convites.pendentesDe(loja)).isEmpty();
    }
}
