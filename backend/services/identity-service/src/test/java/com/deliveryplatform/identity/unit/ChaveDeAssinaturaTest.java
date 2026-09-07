package com.deliveryplatform.identity.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.deliveryplatform.identity.config.JwtProperties;
import com.deliveryplatform.identity.infrastructure.security.ChaveDeAssinatura;
import com.deliveryplatform.identity.infrastructure.security.ChaveDeAssinaturaIndisponivel;
import com.deliveryplatform.identity.support.GeradorDeChaveDeTeste;

class ChaveDeAssinaturaTest {

    @TempDir
    Path diretorio;

    private ChaveDeAssinatura carregar(Path pem) {
        return new ChaveDeAssinatura(new JwtProperties(
                "http://localhost:8081", "delivery-platform", Duration.ofMinutes(30), pem.toString()));
    }

    @Test
    void carrega_pem_pkcs8_e_deriva_a_publica() throws Exception {
        Path pem = GeradorDeChaveDeTeste.escreverPem(diretorio.resolve("jwt-private.pem"));

        ChaveDeAssinatura chave = carregar(pem);

        assertThat(chave.par().toRSAPublicKey().getModulus().bitLength())
                .as("a ADR-037 fixou RSA 2048")
                .isEqualTo(2048);
        assertThat(chave.par().isPrivate())
                .as("o par carregado assina; é o JWKS que projeta só a metade pública")
                .isTrue();
    }

    @Test
    void o_kid_e_derivado_da_chave_e_por_isso_e_estavel() {
        Path pem = GeradorDeChaveDeTeste.escreverPem(diretorio.resolve("jwt-private.pem"));

        String primeiro = carregar(pem).par().getKeyID();
        String segundo = carregar(pem).par().getKeyID();

        assertThat(primeiro)
                .as("thumbprint do RFC 7638: mesma chave, mesmo kid, sem estado guardado")
                .isEqualTo(segundo)
                .isNotBlank();
    }

    @Test
    void chaves_diferentes_produzem_kid_diferente() {
        Path uma = GeradorDeChaveDeTeste.escreverPem(diretorio.resolve("a.pem"));
        Path outra = GeradorDeChaveDeTeste.escreverPem(diretorio.resolve("b.pem"));

        assertThat(carregar(uma).par().getKeyID())
                .as("kid derivado não descola do material que nomeia — é o que faz rotação funcionar")
                .isNotEqualTo(carregar(outra).par().getKeyID());
    }

    @Test
    void o_jwks_publicado_nao_leva_a_chave_privada() throws Exception {
        Path pem = GeradorDeChaveDeTeste.escreverPem(diretorio.resolve("jwt-private.pem"));

        String json = carregar(pem).jwkSetPublico().toString();

        assertThat(json)
                .as("d, p, q, dp, dq e qi são os campos privados de uma chave RSA em JWK")
                .doesNotContain("\"d\"", "\"p\"", "\"q\"", "\"dp\"", "\"dq\"", "\"qi\"");
        assertThat(json).contains("\"kid\"", "\"n\"", "\"e\"");
    }

    @Test
    void arquivo_inexistente_e_fatal() {
        assertThatThrownBy(() -> carregar(diretorio.resolve("nao-existe.pem")))
                .isInstanceOf(ChaveDeAssinaturaIndisponivel.class)
                .hasMessageContaining("nao-existe.pem");
    }

    @Test
    void arquivo_que_nao_e_pem_e_fatal() throws Exception {
        Path lixo = Files.writeString(diretorio.resolve("lixo.pem"), "isto não é uma chave");

        assertThatThrownBy(() -> carregar(lixo))
                .isInstanceOf(ChaveDeAssinaturaIndisponivel.class);
    }

    @Test
    void a_mensagem_de_erro_nao_vaza_o_conteudo_do_arquivo() throws Exception {
        Path pem = Files.writeString(diretorio.resolve("segredo.pem"), "MIIEvQIBADANBgkqh-conteudo-secreto");

        assertThatThrownBy(() -> carregar(pem))
                .isInstanceOf(ChaveDeAssinaturaIndisponivel.class)
                .hasMessageNotContaining("conteudo-secreto");
    }
}
