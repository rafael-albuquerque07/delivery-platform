package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.api.error.TratadorDeErros;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada.Motivo;
import com.deliveryplatform.catalog.domain.model.Categoria;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.ProblemDetail;

import java.util.List;

import static com.deliveryplatform.catalog.support.ProdutoDeTeste.CATEGORIA;
import static com.deliveryplatform.catalog.support.ProdutoDeTeste.LOJA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * O erro diz de quem é a culpa (ADR-063): regra do comerciante é 400 com a frase
 * escrita pelo tratador; defeito nosso é 500, e não conta nada. A recusa de estado —
 * o rascunho que não se inativa — é 409 desde a E-8 (ADR-053 emendada).
 *
 * <p><b>Cada exceção daqui sai do domínio de verdade</b> — um produto montado com o
 * defeito, um {@code publicar()} que recusa —, e não de um {@code new} no teste. Um
 * dublê da exceção provaria que o tratador lê o que o teste escreveu; este prova que
 * ele lê o que o agregado lança.
 *
 * <p><b>Integridade se prova aqui, e não por HTTP</b>: a E-3b mediu que nenhuma rota de
 * hoje alcança um ponto de {@code INTEGRIDADE} — que é a definição dele.
 */
class ErroDoCatalogoTest {

    private final TratadorDeErros tratador = new TratadorDeErros();

    // ── A1 · cada motivo de regra tem a sua frase ──────────────────────────

    @Test
    @DisplayName("TEXTO_OBRIGATORIO nomeia o campo")
    void texto_obrigatorio() {
        assertThat(detalheDe(() -> Categoria.nova(LOJA, "  ", 0), Motivo.TEXTO_OBRIGATORIO))
                .isEqualTo("o nome da categoria é obrigatório");
    }

    @Test
    @DisplayName("LIMITES_DO_GRUPO nomeia o grupo")
    void limites_do_grupo() {
        assertThat(detalheDe(() -> GrupoDeOpcoes.novo("Borda", 2, 1, 0, List.of()),
                Motivo.LIMITES_DO_GRUPO))
                .startsWith("o grupo «Borda» tem limites de escolha impossíveis");
    }

    @Test
    @DisplayName("PRECO_BASE_INVALIDO explica onde fica o desconto")
    void preco_base_invalido() {
        assertThat(detalheDe(() -> Produto.rascunho(LOJA, CATEGORIA, "Pizza",
                Precos.reais("-1.00"), ModoDeControle.QUALITATIVO, 0), Motivo.PRECO_BASE_INVALIDO))
                .isEqualTo("o preço do produto não pode ser negativo; para dar desconto, "
                        + "use um acréscimo negativo numa opção");
    }

    @Test
    @DisplayName("PUBLICACAO_SEM_PRECO nomeia o produto")
    void publicacao_sem_preco() {
        Produto gratis = Produto.rascunho(LOJA, CATEGORIA, "Pizza doce", Precos.zero(),
                ModoDeControle.QUALITATIVO, 0);
        assertThat(detalheDe(gratis::publicar, Motivo.PUBLICACAO_SEM_PRECO))
                .isEqualTo("«Pizza doce» está com preço zero e não vai ao cardápio; "
                        + "ponha o preço e publique de novo");
    }

    @Test
    @DisplayName("PUBLICACAO_TETO_INALCANCAVEL nomeia o grupo")
    void publicacao_teto_inalcancavel() {
        Produto p = pizza();
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Adicionais", 0, 3, 0,
                List.of(Opcao.nova("Bacon", Precos.reais("5.00"), 0))));
        assertThat(detalheDe(p::publicar, Motivo.PUBLICACAO_TETO_INALCANCAVEL))
                .isEqualTo("o grupo «Adicionais» deixa escolher mais opções do que tem; "
                        + "diminua o máximo ou acrescente opções");
    }

    @Test
    @DisplayName("PUBLICACAO_PRECO_NAO_POSITIVO diz o mínimo em reais")
    void publicacao_preco_nao_positivo() {
        Produto p = Produto.rascunho(LOJA, CATEGORIA, "Pizza", Precos.reais("10.00"),
                ModoDeControle.QUALITATIVO, 0);
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Tamanho", 1, 1, 0,
                List.of(Opcao.nova("Broto", Precos.reais("-12.50"), 0))));
        assertThat(detalheDe(p::publicar, Motivo.PUBLICACAO_PRECO_NAO_POSITIVO))
                .isEqualTo("com os acréscimos das opções, o produto pode sair por R$ -2,50, "
                        + "e o preço precisa ser maior que zero; ajuste os acréscimos negativos");
    }

    /**
     * O único motivo desta classe que é recusa de estado: o pedido está bem formado, e é o
     * estado do produto que o proíbe. 409 desde a E-8 (ADR-053 emendada); a frase é a mesma.
     */
    @Test
    @DisplayName("RASCUNHO_NAO_SE_INATIVA: 409, com a mesma frase")
    void rascunho_nao_se_inativa() {
        assertThat(detalheDe(pizza()::inativar, Motivo.RASCUNHO_NAO_SE_INATIVA, 409))
                .isEqualTo("este produto ainda é rascunho e nunca apareceu no cardápio; "
                        + "não há o que tirar do ar");
    }

    /**
     * O que impede o 409 do rascunho de virar troca da classe inteira: <b>todo outro motivo
     * de regra continua 400</b>. Percorre o enum, e não uma lista escrita à mão — um motivo
     * novo entra aqui sozinho, e quem o quiser em 409 tem de dizê-lo no tratador.
     *
     * <p>A exceção é montada com {@code new}, ao contrário do resto desta classe: o que se
     * afirma aqui é o mapa do tratador, motivo por motivo, e não o que o agregado lança.
     */
    @Test
    @DisplayName("todo outro motivo de regra continua 400 — o 409 é de um motivo, não da classe")
    void os_outros_motivos_continuam_400() {
        List<Motivo> deRegra = java.util.Arrays.stream(Motivo.values())
                .filter(m -> m != Motivo.INTEGRIDADE && m != Motivo.RASCUNHO_NAO_SE_INATIVA)
                .toList();

        assertThat(deRegra).as("os nove motivos de regra além do rascunho").hasSize(9);
        for (Motivo motivo : deRegra) {
            RegraDoCatalogoViolada violada =
                    new RegraDoCatalogoViolada(motivo, "teste", "ESGOTADO_HOJE", "Pizza");
            assertThat(tratador.regraViolada(violada).getStatus())
                    .as("%s", motivo)
                    .isEqualTo(400);
        }
    }

    @Test
    @DisplayName("MODO_SEM_CONTROLE nomeia o produto e o estado em português")
    void modo_sem_controle() {
        Produto lata = ProdutoDeTeste.refrigerante();
        assertThat(detalheDe(() -> lata.marcar(ProdutoDeTeste.estado(false)),
                Motivo.MODO_SEM_CONTROLE))
                .contains("«" + lata.getNome() + "»")
                .doesNotContain("SEM_CONTROLE", "ESGOTADO", "ACABANDO");
    }

    // ── A2 e A3 · a frase não traz o código, e traz os dados certos ────────

    @Test
    @DisplayName("dois grupos, um impossível: o detail nomeia aquele, não o outro, e não diz C5")
    void a_recusa_nomeia_um_grupo_entre_dois() {
        Produto p = pizza();
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Tamanho", 1, 1, 0,
                List.of(Opcao.nova("Grande", Precos.reais("0.00"), 0))));
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Borda", 2, 2, 1,
                List.of(Opcao.nova("Catupiry", Precos.reais("6.00"), 0))));

        String detail = detalheDe(p::publicar, Motivo.PUBLICACAO_GRUPO_IMPOSSIVEL);

        assertThat(detail)
                .isEqualTo("o grupo «Borda» pede mais escolhas do que tem opções, e ninguém "
                        + "conseguiria pedir o produto; acrescente opções ou diminua o mínimo")
                .as("com um grupo só, uma frase fixa passaria igual — este é o caso que separa "
                        + "a frase escrita da frase útil")
                .doesNotContain("Tamanho")
                .as("o código da invariante é do log, não da tela")
                .doesNotContain("C5", "C1");
    }

    @Test
    @DisplayName("dois grupos impossíveis: a frase vai ao plural e nomeia os dois")
    void dois_grupos_impossiveis_no_plural() {
        Produto p = pizza();
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Tamanho", 1, 1, 0, List.of()));
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Borda", 1, 1, 1, List.of()));

        assertThat(detalheDe(p::publicar, Motivo.PUBLICACAO_GRUPO_IMPOSSIVEL))
                .startsWith("os grupos «Tamanho», «Borda» pedem mais escolhas do que têm opções");
    }

    // ── A4 · integridade é 500, e não conta nada ──────────────────────────

    @Test
    @DisplayName("INTEGRIDADE: 500, e o corpo não ecoa a mensagem técnica")
    void integridade_e_500_e_mudo() {
        RegraDoCatalogoViolada defeito = lancada(() -> Produto.rascunho(null, CATEGORIA, "Pizza",
                Precos.reais("10.00"), ModoDeControle.QUALITATIVO, 0));
        assertThat(defeito.motivo()).isEqualTo(Motivo.INTEGRIDADE);

        ProblemDetail resposta = tratador.regraViolada(defeito);

        assertThat(resposta.getStatus()).isEqualTo(500);
        assertThat(resposta.getDetail())
                .as("sem esta asserção, um 500 que ecoasse \"produto sem estabelecimento\" passaria")
                .isEqualTo("não foi possível completar a operação")
                .doesNotContain("sem estabelecimento", "sem id", "nul");
    }

    // ── A5 · a mensagem técnica continua na exceção ───────────────────────

    @Test
    @DisplayName("a mensagem da exceção continua com o código da invariante — é dela que o log vive")
    void a_mensagem_tecnica_continua() {
        Produto gratis = Produto.rascunho(LOJA, CATEGORIA, "Pizza doce", Precos.zero(),
                ModoDeControle.QUALITATIVO, 0);
        assertThat(lancada(gratis::publicar)).hasMessageStartingWith("C1:");
    }

    // ── apoio ──────────────────────────────────────────────────────────────

    private static Produto pizza() {
        return Produto.rascunho(LOJA, CATEGORIA, "Pizza", Precos.reais("40.00"),
                ModoDeControle.QUALITATIVO, 0);
    }

    private static RegraDoCatalogoViolada lancada(Executable ato) {
        Throwable t = catchThrowable(ato::execute);
        assertThat(t).isInstanceOf(RegraDoCatalogoViolada.class);
        return (RegraDoCatalogoViolada) t;
    }

    /** Provoca no domínio, confere o motivo, e devolve o detail de um 400. */
    private String detalheDe(Executable ato, Motivo esperado) {
        return detalheDe(ato, esperado, 400);
    }

    /** O mesmo, com o status esperado — 409 só para a recusa de estado (E-8). */
    private String detalheDe(Executable ato, Motivo esperado, int status) {
        RegraDoCatalogoViolada violada = lancada(ato);
        assertThat(violada).as("o domínio não recusou").isNotNull();
        assertThat(violada.motivo()).isEqualTo(esperado);
        ProblemDetail resposta = tratador.regraViolada(violada);
        assertThat(resposta.getStatus()).isEqualTo(status);
        return resposta.getDetail();
    }
}
