package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.support.Infraestrutura;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste que teria pegado o produto cartesiano — e que o pega se ele voltar.
 *
 * <p>A A2b mediu o defeito lendo o SQL num relatório de teste: <b>um</b>
 * {@code select} com cinco {@code left join}, trafegando o produto das cinco
 * coleções. Uma medida lida à mão não protege nada depois do dia em que foi
 * lida; esta classe transforma a medida em asserção.
 *
 * <p>A estatística do Hibernate conta consultas preparadas, não linhas. Mas a
 * contagem é assinatura suficiente: <b>uma</b> consulta significa que as cinco
 * coleções voltaram para o mesmo {@code join}, e é exatamente a regressão que
 * se quer impedir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "delivery.outbox.habilitado=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
class LeituraDoAgregadoIT extends Infraestrutura {

    /** Uma para a loja, uma para cada uma das cinco coleções. */
    private static final long CONSULTAS_ESPERADAS = 6;

    @Autowired
    private EstabelecimentoRepositorio lojas;

    @Autowired
    private EntityManagerFactory fabrica;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void buscar_por_id_nao_junta_as_cinco_colecoes_na_mesma_consulta() {
        UUID id = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        entityManager.flush();
        entityManager.clear();

        Statistics estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        Estabelecimento lido = lojas.buscarPorId(id).orElseThrow();

        assertThat(estatisticas.getPrepareStatementCount())
                .as("uma consulta só significa que as cinco @ElementCollection voltaram "
                        + "para o mesmo left join — o produto cartesiano que a A2b mediu. "
                        + "Se este número vier diferente de %d, relate o valor em vez de "
                        + "ajustar a asserção: ele diz o que o Hibernate está fazendo",
                        CONSULTAS_ESPERADAS)
                .isEqualTo(CONSULTAS_ESPERADAS);

        assertThat(lido.getAreasDeEntrega())
                .as("e o agregado continua completo — SUBSELECT muda o caminho, não o "
                        + "resultado")
                .hasSize(LojaDeTeste.pizzaria().getAreasDeEntrega().size());
        assertThat(lido.getDisponibilidade().horarioDeFuncionamento())
                .as("e o horário também — inclusive a faixa que cruza a meia-noite")
                .isEqualTo(LojaDeTeste.pizzaria().getDisponibilidade().horarioDeFuncionamento());
    }
}
