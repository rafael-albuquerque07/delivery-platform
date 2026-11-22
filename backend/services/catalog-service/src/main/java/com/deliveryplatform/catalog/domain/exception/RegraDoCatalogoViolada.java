package com.deliveryplatform.catalog.domain.exception;

import java.util.List;

/**
 * Uma regra do catálogo foi violada — ou uma invariante de construção quebrou.
 *
 * <p><b>Duas espécies, um tipo, e o {@link Motivo} as separa</b> (ADR-063). O censo
 * de 2026-10-11, em {@code cbd633c}, achou <b>30</b> pontos de lançamento em <b>6</b>
 * arquivos do {@code domain/model} — onze são regra que o comerciante pode causar, e o
 * resto é integridade: nulo onde o nosso código deveria ter posto valor. Até a E-3b
 * todos saíam <b>400</b>, o que dizia a quem lê que ele errou quando quem errou fomos
 * nós. O número envelhece a cada rodada, e quem quiser o de hoje roda — as duas
 * formas, porque a integridade passa pela fábrica:
 *
 * <pre>grep -rnE "new RegraDoCatalogoViolada|RegraDoCatalogoViolada\.integridade\(" src/main/java/com/deliveryplatform/catalog/domain/model | wc -l</pre>
 *
 * <p>Na E-3c nasceu o primeiro motivo lançado <b>fora</b> de um agregado — a
 * referência de categoria que não resolve, no caso de uso (ADR-066). O censo acima
 * conta só os do domínio.
 *
 * <p><b>O motivo não sabe de HTTP</b>, e é de propósito: quem mapeia motivo em código
 * é o {@code TratadorDeErros}. Regra é 400 com a frase composta a partir dos dados;
 * integridade é 500 com frase genérica, e a mensagem técnica fica no log.
 *
 * <p><b>A mensagem continua</b> — os testes afirmam o tipo e um fragmento dela, e é
 * ela que o log recebe. O que ela não faz mais é ir ao corpo da resposta.
 *
 * <p><b>Herda de {@code RuntimeException} e mora em {@code domain/exception}</b>,
 * como as exceções de domínio do {@code merchant} e do {@code identity}.
 *
 * <p><b>Gatilho escrito:</b> o dia em que alguém ler o catálogo e se confundir com uma
 * {@code RegraDoCatalogoViolada} que é 500. Aí a classe se parte em duas, com nomes
 * honestos, e os pontos de integridade trocam de tipo.
 */
public class RegraDoCatalogoViolada extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * O que foi violado, e quais dados a frase usa. A ordem dos dados está ao lado de
     * cada valor; o {@code TratadorDeErros} a lê nessa ordem.
     */
    public enum Motivo {
        /** dados: o campo ("nome do grupo"). */
        TEXTO_OBRIGATORIO,
        /** C3, e o máximo menor que 1. dados: o nome do grupo. */
        LIMITES_DO_GRUPO,
        /** C1 na construção: preço negativo. sem dados. */
        PRECO_BASE_INVALIDO,
        /** C1 na publicação: preço zero. dados: o nome do produto. */
        PUBLICACAO_SEM_PRECO,
        /** C5. dados: os nomes dos grupos impossíveis. */
        PUBLICACAO_GRUPO_IMPOSSIVEL,
        /** C4. dados: os nomes dos grupos com teto inalcançável. */
        PUBLICACAO_TETO_INALCANCAVEL,
        /** C2. dados: o mínimo possível, como {@code BigDecimal.toPlainString()}. */
        PUBLICACAO_PRECO_NAO_POSITIVO,
        /** Inativar um rascunho. sem dados. */
        RASCUNHO_NAO_SE_INATIVA,
        /** Marcar como acabando ou esgotado um produto SEM_CONTROLE. dados: o estado, o nome do produto. */
        MODO_SEM_CONTROLE,
        /**
         * A seção que o corpo cita não existe nesta loja — inexistente ou de outra, a
         * mesma recusa (M7). sem dados. <b>O primeiro motivo lançado fora de um
         * agregado</b>: pelo caso de uso que cria produto (ADR-066).
         */
        CATEGORIA_NAO_ENCONTRADA,
        /** Cotação: quantidade menor que 1. sem dados. */
        QUANTIDADE_INVALIDA,
        /** Cotação: opção escolhida que não pertence ao produto. sem dados. */
        OPCAO_FORA_DO_PRODUTO,
        /** Cotação: a mesma opção duas vezes no mesmo item (ADR-088). dados: o nome do produto. */
        OPCAO_REPETIDA,
        /** Cotação: escolhas num grupo fora do mínimo ou do máximo. dados: o nome do grupo, o mínimo, o máximo, o que veio. */
        ESCOLHAS_FORA_DOS_LIMITES,
        /** Nulo onde o nosso código deveria ter posto valor. Só um defeito o produz. */
        INTEGRIDADE
    }

    private final Motivo motivo;
    private final List<String> dados;

    public RegraDoCatalogoViolada(Motivo motivo, String mensagem, String... dados) {
        super(mensagem);
        this.motivo = motivo;
        this.dados = List.of(dados);
    }

    /** A invariante de construção: o que só um defeito nosso dispara. */
    public static RegraDoCatalogoViolada integridade(String mensagem) {
        return new RegraDoCatalogoViolada(Motivo.INTEGRIDADE, mensagem);
    }

    public Motivo motivo() {
        return motivo;
    }

    public List<String> dados() {
        return dados;
    }
}
