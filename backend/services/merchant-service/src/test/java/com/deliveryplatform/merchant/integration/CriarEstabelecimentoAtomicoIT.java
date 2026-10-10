package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.usecase.GerenciarEquipeService;
import com.deliveryplatform.merchant.support.IdentityDeMentira;
import com.deliveryplatform.merchant.support.Infraestrutura;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * O que o dublê do teste de unidade não alcança: <b>a transação é uma só</b>. Se o
 * registro do vínculo no outbox falha depois de a loja e o membro terem sido salvos,
 * nenhum dos dois pode ficar — loja sem dono é exatamente o que a fábrica
 * {@code Membro.fundador} existe para impedir.
 *
 * <p>Classe própria porque o espião troca o contexto do Spring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "delivery.outbox.habilitado=false")
class CriarEstabelecimentoAtomicoIT extends Infraestrutura {

    private static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();

    @DynamicPropertySource
    static void apontarParaOIdentityDeMentira(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", IDENTITY::jwksUri);
        registry.add("delivery.jwt.issuer", () -> IdentityDeMentira.EMISSOR);
        registry.add("delivery.jwt.audience", () -> IdentityDeMentira.AUDIENCIA);
    }

    @AfterAll
    static void derrubar() {
        IDENTITY.close();
    }

    @LocalServerPort
    int porta;

    @MockitoSpyBean
    GerenciarEquipeService equipes;

    @Autowired
    EstabelecimentoRepositorio lojas;

    @Test
    void falha_no_outbox_desfaz_a_loja_e_o_vinculo() {
        // O campo é o proxy transacional; o stub vai no espião que está atrás dele.
        // Pelo proxy, a própria chamada de stub passaria pelo MANDATORY e estouraria.
        GerenciarEquipeService espiao = AopTestUtils.getTargetObject(equipes);
        doThrow(new IllegalStateException("outbox fora do ar, de propósito"))
                .when(espiao).registrarVinculoNascido(any(), any());
        String nome = "Loja que não pode ficar " + UUID.randomUUID();

        RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build()
                .post().uri("/api/v1/me/estabelecimentos")
                .header("Authorization", "Bearer " + IDENTITY.tokenDe(UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(CriarEstabelecimentoIT.CORPO.formatted(nome, "123.456.789-09"))
                .exchange()
                .expectStatus().is5xxServerError();

        assertThat(lojas.todos())
                .as("a loja ficou no banco sem o vínculo e sem o evento")
                .noneMatch(loja -> loja.getIdentificacao().nome().equals(nome));
    }
}
