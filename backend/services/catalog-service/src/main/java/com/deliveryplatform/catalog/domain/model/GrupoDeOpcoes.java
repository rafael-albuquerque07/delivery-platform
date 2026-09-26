package com.deliveryplatform.catalog.domain.model;

import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Um conjunto de escolhas do mesmo assunto: "Tamanho", "Bordas", "Bebidas".
 *
 * <h2>Duas perguntas diferentes que o grupo sabe responder</h2>
 *
 * <p>Esta é a distinção que a rodada inteira gira em torno, e ela some se as
 * duas perguntas virarem uma:
 *
 * <ul>
 *   <li>{@link #estruturalmenteSatisfazivel()} — <b>existe</b> opção suficiente
 *       para alguém escolher? Um grupo "Tamanho" obrigatório com zero opções
 *       cadastradas não pode ser satisfeito <i>nunca</i>, por ninguém, em
 *       nenhum dia. É defeito de cadastro, e trava a publicação.</li>
 *   <li>{@link #satisfazivelHoje()} — as opções que existem <b>estão
 *       disponíveis</b> agora? Um grupo "Tamanho" com pequena, média e grande,
 *       todas esgotadas, é cadastro perfeito num dia ruim. Não trava
 *       publicação: o produto continua {@code ATIVO} e apenas não é vendável
 *       hoje.</li>
 * </ul>
 *
 * <p>Confundir as duas é como o cardápio se despublica sozinho numa
 * sexta-feira à noite: o último tamanho acaba, alguma regra conclui que o
 * produto ficou inválido, e o comerciante encontra o prato em
 * {@code RASCUNHO} na segunda-feira sem saber quem mexeu.
 *
 * <h2>{@code minEscolhas} e {@code maxEscolhas}</h2>
 *
 * <p>{@code minEscolhas == 0} é grupo opcional ("Adicionais"); {@code >= 1} é
 * obrigatório ("Tamanho"). C3 — {@code 0 ≤ minEscolhas ≤ maxEscolhas} — é
 * cobrada aqui, na construção, porque um grupo que a viola não tem leitura
 * possível em nenhum estado. {@code maxEscolhas} é também pelo menos 1: com C3
 * e C4 juntas, zero só caberia num grupo sem opção nenhuma, que não pergunta
 * nada.
 *
 * <p><b>C4 e C5 são cobradas na publicação, e não aqui.</b> As duas comparam o
 * teto e o piso com a contagem de opções — {@code maxEscolhas ≤ opções} e
 * {@code minEscolhas ≤ opções} — e o {@code catalogo.md} §2 diz que em rascunho
 * o comerciante pode deixar o produto pela metade: o grupo nasce antes das
 * opções. {@link Produto#publicar()} pergunta {@link #tetoAlcancavel()} e
 * {@link #estruturalmenteSatisfazivel()}.
 */
public record GrupoDeOpcoes(
        UUID id,
        String nome,
        int minEscolhas,
        int maxEscolhas,
        int ordem,
        List<Opcao> opcoes
) {

    public GrupoDeOpcoes {
        if (id == null) {
            throw new RegraDoCatalogoViolada("grupo sem id");
        }
        nome = Textos.exigirPreenchido(nome, "nome do grupo");
        if (minEscolhas < 0) {
            throw new RegraDoCatalogoViolada("C3: minEscolhas negativo no grupo " + nome);
        }
        if (maxEscolhas < 1) {
            throw new RegraDoCatalogoViolada(
                    "maxEscolhas menor que 1 no grupo " + nome + " — grupo onde nada pode "
                            + "ser escolhido não é grupo");
        }
        if (minEscolhas > maxEscolhas) {
            throw new RegraDoCatalogoViolada(
                    "C3: minEscolhas maior que maxEscolhas no grupo " + nome);
        }
        opcoes = List.copyOf(opcoes == null ? List.of() : opcoes)
                .stream()
                .sorted(Comparator.comparingInt(Opcao::ordem))
                .toList();
    }

    public static GrupoDeOpcoes novo(String nome, int minEscolhas, int maxEscolhas,
                                     int ordem, List<Opcao> opcoes) {
        return new GrupoDeOpcoes(UUID.randomUUID(), nome, minEscolhas, maxEscolhas, ordem, opcoes);
    }

    /** {@code minEscolhas >= 1}: o consumidor não passa sem escolher. */
    public boolean obrigatorio() {
        return minEscolhas >= 1;
    }

    public long contarDisponiveis() {
        return opcoes.stream().filter(Opcao::disponivel).count();
    }

    /**
     * C4 — {@code maxEscolhas ≤ contagem de opções}: o teto pode ser alcançado.
     *
     * <p>"Escolha até 3" com 2 cadastradas promete ao consumidor uma escolha que
     * não existe, e a tela que monta a pergunta a partir do teto mente. Conta
     * opções cadastradas, <b>independente de disponibilidade</b>: esgotar é do
     * dia, e o teto é do cadastro.
     */
    public boolean tetoAlcancavel() {
        return maxEscolhas <= opcoes.size();
    }

    /**
     * C5 — {@code minEscolhas ≤ contagem de opções}: existe opção cadastrada em
     * número suficiente para o mínimo ser cumprido — <b>independente de
     * disponibilidade</b>.
     *
     * <p>Grupo opcional passa aqui inclusive vazio; quem o recusa na publicação
     * é C4, porque o teto dele é pelo menos 1.
     */
    public boolean estruturalmenteSatisfazivel() {
        return !obrigatorio() || opcoes.size() >= minEscolhas;
    }

    /**
     * A terceira cláusula do vendável da §5, para <b>este</b> grupo.
     *
     * <p>Grupo opcional não impede venda nem quando está inteiro esgotado: o
     * consumidor simplesmente não escolhe adicional.
     */
    public boolean satisfazivelHoje() {
        return !obrigatorio() || contarDisponiveis() >= minEscolhas;
    }
}
