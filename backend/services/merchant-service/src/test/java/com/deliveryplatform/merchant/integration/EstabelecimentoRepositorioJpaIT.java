package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.domain.model.Documento;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.domain.model.Identificacao;
import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Primeiro Testcontainers do {@code merchant-service}. Sem H2 (ADR-014):
 * {@code NUMERIC(19,2)}, {@code CHECK} e chave composta em tabela de coleção são
 * comportamento do PostgreSQL.
 *
 * <p>{@code @SpringBootTest(webEnvironment = NONE)} e não {@code @DataJpaTest}:
 * o slice de JPA saiu do {@code spring-boot-test-autoconfigure} no Boot 4.1.1 —
 * a mesma nota que o {@code UsuarioRepositorioJpaIT} carrega.
 *
 * <p>Diferente do {@code identity}, aqui não há {@code @DynamicPropertySource}
 * para chave de assinatura: o {@code merchant-service} valida token, não emite,
 * e a validação é de contexto web — que este teste não sobe.
 *
 * <p><b>As duas propriedades de RabbitMQ não são decoração.</b> O
 * {@code merchant-service} aplica {@code delivery.messaging-conventions}, e o
 * {@code application.yml} traz {@code spring.rabbitmq.username:
 * ${RABBITMQ_USERNAME}} <i>sem valor padrão</i>. O {@code RabbitProperties}
 * liga no início do contexto, e um placeholder sem resolução derruba a subida
 * antes de qualquer teste rodar — nada a ver com o banco. Valor qualquer
 * resolve: este teste não fala com broker nenhum, e não há {@code @RabbitListener}
 * que force conexão. Quando o primeiro listener existir, isto vira um contêiner
 * de verdade.
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
                LojaDeTeste.troco()));
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
}
