package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.domain.model.AreaDeEntrega;
import com.deliveryplatform.merchant.domain.model.Disponibilidade;
import com.deliveryplatform.merchant.domain.model.Documento;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.Faixa;
import com.deliveryplatform.merchant.domain.model.FaixaDeCep;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.domain.model.Identificacao;
import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Pausa;
import com.deliveryplatform.merchant.domain.model.Telefone;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import com.deliveryplatform.valuetypes.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.deliveryplatform.merchant.support.LojaDeTeste.emSaoPaulo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Primeiro Testcontainers do {@code merchant-service}. Sem H2 (ADR-014):
 * {@code NUMERIC(19,2)}, {@code TIME}, {@code CHECK} e chave composta em tabela
 * de coleção são comportamento do PostgreSQL.
 *
 * <p>{@code @SpringBootTest(webEnvironment = NONE)} e não {@code @DataJpaTest}:
 * o slice de JPA saiu do {@code spring-boot-test-autoconfigure} no Boot 4.1.1 —
 * a mesma nota que o {@code UsuarioRepositorioJpaIT} carrega.
 *
 * <p><b>As duas propriedades de RabbitMQ não são decoração.</b> O
 * {@code application.yml} traz {@code spring.rabbitmq.username:
 * ${RABBITMQ_USERNAME}} sem valor padrão, e placeholder sem resolução derruba a
 * subida do contexto antes de qualquer teste rodar. Este teste não fala com
 * broker nenhum; quando o primeiro {@code @RabbitListener} existir, isto vira um
 * contêiner de verdade.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.rabbitmq.username=teste",
        "spring.rabbitmq.password=teste"
})
@Testcontainers
@Transactional
class EstabelecimentoRepositorioJpaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private EstabelecimentoRepositorio repositorio;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void salva_e_recupera_preservando_os_value_objects() {
        Estabelecimento salvo = repositorio.salvar(LojaDeTeste.pizzaria());
        entityManager.flush();
        entityManager.clear();

        Optional<Estabelecimento> recuperado = repositorio.buscarPorId(salvo.getId());

        assertThat(recuperado).isPresent();
        Identificacao identificacao = recuperado.get().getIdentificacao();
        assertThat(identificacao.documento()).isEqualTo(new Documento("12345678000195"));
        assertThat(identificacao.telefone()).isEqualTo(Telefone.de("+5511987654321"));
        assertThat(identificacao.fusoHorario()).isEqualTo(FusoHorario.PADRAO);
        assertThat(identificacao.bairro()).isEqualTo("Boa Viagem");
    }

    @Test
    void a_matriz_de_metodos_volta_agrupada_por_modalidade() {
        Estabelecimento salvo = repositorio.salvar(LojaDeTeste.pizzaria());
        entityManager.flush();
        entityManager.clear();

        Estabelecimento recuperado = repositorio.buscarPorId(salvo.getId()).orElseThrow();

        assertThat(recuperado.metodosDe(Modalidade.ENTREGA))
                .as("a tabela guarda pares planos; quem reagrupa é o mapper")
                .containsExactlyInAnyOrder(
                        MetodoPagamento.DINHEIRO, MetodoPagamento.CARTAO, MetodoPagamento.PIX);
        assertThat(recuperado.metodosDe(Modalidade.RETIRADA))
                .containsExactlyInAnyOrder(MetodoPagamento.DINHEIRO, MetodoPagamento.CARTAO);
    }

    @Test
    void o_dinheiro_volta_com_escala_dois_e_em_reais() {
        Estabelecimento salvo = repositorio.salvar(LojaDeTeste.pizzaria());
        entityManager.flush();
        entityManager.clear();

        Estabelecimento recuperado = repositorio.buscarPorId(salvo.getId()).orElseThrow();

        assertThat(recuperado.pedidoMinimoDe(Modalidade.ENTREGA)).isEqualTo(Money.de("25.00"));
        assertThat(recuperado.pedidoMinimoDe(Modalidade.RETIRADA)).isEqualTo(Money.ZERO);
        assertThat(recuperado.getOperacao().descontoDeRetirada()).isEqualTo(Money.de("5.00"));
        assertThat(recuperado.getPoliticaDeTroco().fundoMaximoDeTroco())
                .isEqualTo(Money.de("50.00"));
        assertThat(recuperado.getPoliticaDeTroco().fundoMaximoDeTroco().valor().scale())
                .as("NUMERIC(19,2) na ida, escala 2 na volta — a ADR-009 pede as duas pontas")
                .isEqualTo(2);
    }

    @Test
    void o_horario_volta_agrupado_por_dia_e_ordenado_por_inicio() {
        Estabelecimento salvo = repositorio.salvar(LojaDeTeste.pizzaria());
        entityManager.flush();
        entityManager.clear();

        Disponibilidade recuperada =
                repositorio.buscarPorId(salvo.getId()).orElseThrow().getDisponibilidade();

        assertThat(recuperada.faixasDe(DayOfWeek.TUESDAY))
                .as("a tabela guarda trios planos (dia, início, fim)")
                .containsExactly(Faixa.de("18:00", "02:00"));
        assertThat(recuperada.faixasDe(DayOfWeek.SATURDAY))
                .as("a ordem por início é reposta na construção, não vem do banco")
                .containsExactly(Faixa.de("11:00", "14:00"), Faixa.de("18:00", "23:00"));
        assertThat(recuperada.faixasDe(DayOfWeek.MONDAY)).isEmpty();
    }

    @Test
    void a_loja_continua_aberta_a_uma_da_manha_depois_de_ir_e_voltar_do_banco() {
        Estabelecimento salvo = repositorio.salvar(LojaDeTeste.pizzaria());
        entityManager.flush();
        entityManager.clear();

        Estabelecimento recuperado = repositorio.buscarPorId(salvo.getId()).orElseThrow();

        assertThat(recuperado.estaAberta(emSaoPaulo("2026-09-16T01:00")))
                .as("a faixa que cruza a meia-noite sobrevive ao TIME do PostgreSQL")
                .isTrue();
        assertThat(recuperado.estaAberta(emSaoPaulo("2026-09-16T02:00"))).isFalse();
    }

    @Test
    void a_pausa_com_prazo_sobrevive_a_ida_e_volta() {
        Instant ate = emSaoPaulo("2026-09-15T21:00");
        Estabelecimento pausada = Estabelecimento.novo(
                LojaDeTeste.identificacao(FusoHorario.PADRAO),
                LojaDeTeste.operacao(),
                LojaDeTeste.troco(),
                LojaDeTeste.disponibilidade().com(Pausa.ate(ate, "cozinha atolou")),
                LojaDeTeste.areas());

        Estabelecimento salvo = repositorio.salvar(pausada);
        entityManager.flush();
        entityManager.clear();

        Pausa recuperada =
                repositorio.buscarPorId(salvo.getId()).orElseThrow().getDisponibilidade().pausa();

        assertThat(recuperada.ativa()).isTrue();
        assertThat(recuperada.motivo()).isEqualTo("cozinha atolou");
        assertThat(recuperada.pausadoAte()).isEqualTo(ate);
        assertThat(recuperada.ativaEm(emSaoPaulo("2026-09-15T20:00"))).isTrue();
        assertThat(recuperada.ativaEm(emSaoPaulo("2026-09-15T21:30"))).isFalse();
    }

    @Test
    void duas_lojas_do_mesmo_dono_repetem_o_documento_e_isso_e_permitido() {
        repositorio.salvar(LojaDeTeste.pizzaria());
        entityManager.flush();

        // Mesmo documento, outra loja. estabelecimento.md §9: não há limite de
        // estabelecimentos por usuário, e um MEI com dois pontos de venda repete
        // o CPF. UNIQUE aqui recusaria a segunda em silêncio.
        repositorio.salvar(Estabelecimento.novo(
                new Identificacao(
                        "Pizzaria da Marli — Centro",
                        LojaDeTeste.documento(),
                        Telefone.de("(11) 3265-4321"),
                        "Av. Central, 20",
                        "Centro",
                        FusoHorario.PADRAO),
                LojaDeTeste.operacao(),
                LojaDeTeste.troco(),
                Disponibilidade.semHorario(),
                List.of()));
        entityManager.flush();
    }

    @Test
    void o_check_de_m15_esta_na_migration_e_nao_so_no_agregado() {
        repositorio.salvar(LojaDeTeste.pizzaria());
        entityManager.flush();

        // O agregado recusa antes de chegar aqui, então a única forma de provar
        // que a constraint existe é escrever por baixo dele. Sem esta asserção,
        // a migration poderia perder o CHECK e nada acusaria.
        assertThatThrownBy(() -> {
            entityManager
                    .createNativeQuery("UPDATE estabelecimento SET desconto_de_retirada = -1")
                    .executeUpdate();
            entityManager.flush();
        })
                .hasStackTraceContaining("ck_estabelecimento_desconto_de_retirada_nao_negativo");
    }

    @Test
    void o_check_da_pausa_sem_motivo_esta_na_migration() {
        repositorio.salvar(LojaDeTeste.pizzaria());
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager
                    .createNativeQuery("UPDATE estabelecimento SET pausa_ativa = TRUE")
                    .executeUpdate();
            entityManager.flush();
        })
                .as("pausa sem motivo é a que ninguém consegue explicar no dia seguinte")
                .hasStackTraceContaining("ck_estabelecimento_pausa_ativa_exige_motivo");
    }
    @Test
    void as_areas_voltam_com_a_faixa_de_cep_na_area_certa() {
        Estabelecimento salvo = repositorio.salvar(LojaDeTeste.pizzaria());
        entityManager.flush();
        entityManager.clear();

        Estabelecimento recuperado = repositorio.buscarPorId(salvo.getId()).orElseThrow();

        assertThat(recuperado.getAreasDeEntrega())
                .extracting(AreaDeEntrega::nome)
                .containsExactlyInAnyOrder("Boa Viagem", "Centro", "Pina");
        assertThat(recuperado.areaPorNome("Boa Viagem").orElseThrow().faixasDeCep())
                .as("as faixas moram numa tabela separada e voltam para a área certa "
                        + "pelo identificador normalizado")
                .containsExactly(FaixaDeCep.de("51000000", "51999999"));
        assertThat(recuperado.areaPorNome("Centro").orElseThrow().faixasDeCep())
                .as("área sem faixa nenhuma é normal")
                .isEmpty();
    }

    @Test
    void o_cep_e_o_desativado_continuam_valendo_depois_do_banco() {
        Estabelecimento salvo = repositorio.salvar(LojaDeTeste.pizzaria());
        entityManager.flush();
        entityManager.clear();

        Estabelecimento recuperado = repositorio.buscarPorId(salvo.getId()).orElseThrow();

        assertThat(recuperado.areaPara("51500-000").orElseThrow().taxa())
                .isEqualTo(Money.de("7.00"));
        assertThat(recuperado.areaPara("50500-000"))
                .as("o Pina foi salvo desativado e continua fora das consultas")
                .isEmpty();
        assertThat(recuperado.areaPorNome("Centro").orElseThrow().taxa()).isEqualTo(Money.ZERO);
    }

    @Test
    void a_chave_primaria_faz_m9_valer_no_banco_tambem() {
        repositorio.salvar(LojaDeTeste.pizzaria());
        entityManager.flush();

        // Sem parâmetro: há uma loja só na transação, e o SELECT pega o id dela.
        // O agregado recusaria antes, então a única forma de provar que a chave
        // primária existe é escrever por baixo dele.
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "INSERT INTO estabelecimento_area_entrega "
                                    + "(estabelecimento_id, identificador_normalizado, nome, taxa, ativa) "
                                    + "SELECT id, 'BOA VIAGEM', 'Boa viagem de novo', 5.00, TRUE "
                                    + "FROM estabelecimento")
                    .executeUpdate();
            entityManager.flush();
        })
                .as("E1: a mesma área duas vezes com taxas divergentes")
                .hasStackTraceContaining("estabelecimento_area_entrega");
    }
}
