package com.deliveryplatform.catalog.domain.model;

import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.valuetypes.Money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Um item do cardápio. Raiz de agregado: o grupo de opções e a opção não
 * existem fora dele, e ninguém os altera pelas costas.
 *
 * <h2>O vendável é derivado e nunca armazenado</h2>
 *
 * <p>{@link #vendavel()} é uma conta sobre três campos que já estão aqui.
 * Guardá-lo como coluna significaria mantê-lo em dia a cada publicação, a cada
 * marcação de disponibilidade, a cada opção que esgota, a cada abertura de
 * expediente — cinco caminhos de escrita para um valor que a leitura calcula em
 * nanossegundos. <i>O registro diz o que foi feito; o cálculo diz o que vale
 * agora.</i>
 *
 * <p>O preço desta decisão é conhecido e está aceito: o Mongo não filtra por
 * campo que não existe, então "só os vendáveis" é um filtro de aplicação sobre
 * o resultado de {@code (estabelecimentoId, estadoDePublicacao)} — o primeiro
 * índice da §7. O dia em que o cardápio de uma loja não couber em memória, a
 * saída é projeção com dono e invalidação escritos, não um booleano solto.
 *
 * <h2>O que este agregado deliberadamente não sabe</h2>
 *
 * <ul>
 *   <li><b>Se a categoria existe, e se ela é da mesma loja.</b> {@link Categoria}
 *       é outra raiz; agregado não valida agregado. A regra é do caso de uso, e
 *       ela nasce com a rota que cria produto (G-B).</li>
 *   <li><b>Se a loja está aberta.</b> Isso é do {@code merchant}, e vem pela
 *       porta ou pelo evento.</li>
 *   <li><b>Que dia é hoje.</b> Não há relógio nem fuso nesta classe. O dia
 *       operacional tem um dono só (ADR-046 §6) e o catálogo compara, nunca
 *       calcula.</li>
 * </ul>
 */
public final class Produto {

    private final UUID id;
    private final UUID estabelecimentoId;

    private UUID categoriaId;
    private String nome;
    private String descricao;
    private String imagemRef;
    private Money precoBase;
    private int ordem;

    private EstadoDePublicacao estadoDePublicacao;
    private ModoDeControle modoDeControle;
    private Disponibilidade disponibilidade;

    private final List<GrupoDeOpcoes> gruposDeOpcoes = new ArrayList<>();

    private Produto(UUID id, UUID estabelecimentoId, UUID categoriaId, String nome,
                    Money precoBase, ModoDeControle modoDeControle, int ordem) {
        if (estabelecimentoId == null) {
            throw new RegraDoCatalogoViolada("produto sem estabelecimento");
        }
        if (categoriaId == null) {
            throw new RegraDoCatalogoViolada("produto sem categoria");
        }
        if (modoDeControle == null) {
            throw new RegraDoCatalogoViolada("produto sem modo de controle");
        }
        exigirPrecoValido(precoBase);

        this.id = id;
        this.estabelecimentoId = estabelecimentoId;
        this.categoriaId = categoriaId;
        this.nome = Textos.exigirPreenchido(nome, "nome do produto");
        this.precoBase = precoBase;
        this.modoDeControle = modoDeControle;
        this.ordem = ordem;
        this.estadoDePublicacao = EstadoDePublicacao.RASCUNHO;
        this.disponibilidade = Disponibilidade.inicial();
    }

    /**
     * Todo produto nasce {@code RASCUNHO} e {@code DISPONIVEL}.
     *
     * <p>Nascer rascunho é o que permite que o cadastro seja incompleto sem ser
     * inválido: sem foto, sem descrição, sem grupo. A completude é cobrada na
     * publicação, que é quando alguém vai ver.
     */
    public static Produto rascunho(UUID estabelecimentoId, UUID categoriaId, String nome,
                                   Money precoBase, ModoDeControle modoDeControle, int ordem) {
        return new Produto(UUID.randomUUID(), estabelecimentoId, categoriaId, nome,
                precoBase, modoDeControle, ordem);
    }

    // ── publicação ──────────────────────────────────────────────────────────

    /**
     * Coloca o produto no cardápio público.
     *
     * <p><b>Publicar é onde as invariantes são cobradas</b> ({@code catalogo.md}
     * §2): C1 (preço base maior que zero), C5 (mínimo alcançável) e C4 (teto
     * alcançável). Em rascunho o comerciante pode deixar o produto pela metade —
     * preço zero, grupo sem opção —, que é como se trabalha.
     *
     * <p>Recusa se algum grupo obrigatório não puder <b>nunca</b> ser
     * satisfeito — zero opções para um mínimo de um. Note o "nunca": grupo
     * cujas opções estão todas esgotadas <b>publica</b>, porque esgotar é do
     * dia e publicar é do cadastro. O produto fica {@code ATIVO} e não vendável
     * até a opção voltar, que é exatamente o comportamento certo: o
     * comerciante não teve de mexer em nada, e não vai encontrar o prato em
     * rascunho na segunda-feira.
     *
     * <p>Publicar o que já está publicado não faz nada. Idempotência aqui não é
     * conveniência: a publicação vai virar uma rota, e rota que estoura ao ser
     * repetida obriga o cliente a saber o estado antes de agir.
     */
    public void publicar() {
        if (estadoDePublicacao == EstadoDePublicacao.ATIVO) {
            return;
        }
        if (precoBase.ehZero()) {
            throw new RegraDoCatalogoViolada(
                    "C1: preço base zero impede a publicação de " + nome
                            + " — produto de graça por descuido");
        }
        List<String> impossiveis = gruposDeOpcoes.stream()
                .filter(g -> !g.estruturalmenteSatisfazivel())
                .map(GrupoDeOpcoes::nome)
                .toList();
        if (!impossiveis.isEmpty()) {
            throw new RegraDoCatalogoViolada(
                    "C5: grupo obrigatório sem opções suficientes impede a publicação: "
                            + String.join(", ", impossiveis));
        }
        List<String> tetosInalcancaveis = gruposDeOpcoes.stream()
                .filter(g -> !g.tetoAlcancavel())
                .map(GrupoDeOpcoes::nome)
                .toList();
        if (!tetosInalcancaveis.isEmpty()) {
            throw new RegraDoCatalogoViolada(
                    "C4: maxEscolhas maior que o número de opções impede a publicação: "
                            + String.join(", ", tetosInalcancaveis));
        }
        estadoDePublicacao = EstadoDePublicacao.ATIVO;
    }

    /**
     * Retira do cardápio o que já esteve nele.
     *
     * <p>Recusa a partir de {@code RASCUNHO}: um rascunho nunca apareceu, então
     * não há o que retirar. Permitir a transição faria {@code RASCUNHO} e
     * {@code INATIVO} significarem a mesma coisa, e a lista do que o
     * comerciante está montando desapareceria dentro da lista do que ele tirou
     * do ar.
     */
    public void inativar() {
        if (estadoDePublicacao == EstadoDePublicacao.RASCUNHO) {
            throw new RegraDoCatalogoViolada(
                    "rascunho não se inativa: ele nunca apareceu no cardápio");
        }
        estadoDePublicacao = EstadoDePublicacao.INATIVO;
    }

    // ── composição ──────────────────────────────────────────────────────────

    /**
     * Acrescenta um grupo, mantendo a lista ordenada por {@code ordem}.
     *
     * <p>A lista sai sempre ordenada porque o cardápio tem ordem e ela é do
     * comerciante. Empate de {@code ordem} não é recusado — dois grupos com o
     * mesmo número aparecem em ordem de inserção, que é estável e não é
     * decisão de ninguém. Se o {@code catalogo.md} exigir {@code ordem} única,
     * é aqui que a regra entra.
     */
    public void acrescentarGrupo(GrupoDeOpcoes grupo) {
        if (grupo == null) {
            throw new RegraDoCatalogoViolada("grupo nulo");
        }
        gruposDeOpcoes.add(grupo);
        gruposDeOpcoes.sort(Comparator.comparingInt(GrupoDeOpcoes::ordem));
    }

    public void substituirGrupos(List<GrupoDeOpcoes> grupos) {
        gruposDeOpcoes.clear();
        if (grupos != null) {
            grupos.forEach(this::acrescentarGrupo);
        }
    }

    // ── disponibilidade ─────────────────────────────────────────────────────

    /**
     * Registra o que o dia fez com este produto.
     *
     * <p><b>{@code SEM_CONTROLE} só aceita {@code DISPONIVEL}</b>, e essa é a
     * definição do modo, não uma restrição arbitrária: um produto que não acaba
     * não pode acabar. Sem a regra aqui, um refrigerante em lata poderia ficar
     * {@code ESGOTADO_HOJE} por engano de tela e esperar a abertura do próximo
     * expediente para voltar — um produto sumido do cardápio por doze horas,
     * por um estado que o modo dele diz não existir.
     *
     * <p>A marcação não olha para {@link EstadoDePublicacao}: marcar esgotado
     * um produto inativo é inofensivo, e proibir obrigaria a ordenar duas
     * operações que o comerciante faz em telas diferentes.
     *
     * <p><b>O ato de reativar não está aqui.</b> Ele é o consumidor do
     * {@code ExpedienteAlteradoV1} e nasce na G-C; o que existe nesta rodada é
     * a pergunta, em
     * {@link Disponibilidade#deveReativarNoExpediente(java.time.LocalDate)}.
     */
    public void marcar(Disponibilidade nova) {
        if (nova == null) {
            throw new RegraDoCatalogoViolada("disponibilidade nula");
        }
        if (modoDeControle == ModoDeControle.SEM_CONTROLE
                && nova.estado() != EstadoDeDisponibilidade.DISPONIVEL) {
            throw new RegraDoCatalogoViolada(
                    "produto SEM_CONTROLE não acaba: " + nova.estado() + " não se aplica a "
                            + nome);
        }
        this.disponibilidade = nova;
    }

    // ── o vendável ──────────────────────────────────────────────────────────

    /**
     * A fórmula da §5 do {@code catalogo.md}, inteira e num lugar só.
     *
     * <pre>
     * vendavel = estadoDePublicacao == ATIVO
     *          ∧ disponibilidade ∈ {DISPONIVEL, ACABANDO}
     *          ∧ ∀ grupo obrigatório: contar(opções disponíveis) ≥ minEscolhas
     * </pre>
     *
     * <p>A terceira cláusula é a que ninguém lembra. Uma pizza
     * {@code DISPONIVEL} num cardápio publicado, cujo grupo "Tamanho" —
     * obrigatório, escolha uma — está com pequena, média e grande todas
     * esgotadas, <b>não é vendável</b>: não existe pedido válido a montar. Sem
     * esta cláusula o consumidor chega até o carrinho para descobrir lá que não
     * dá, e a cotação recusa um pedido que o cardápio ofereceu.
     */
    public boolean vendavel() {
        return estadoDePublicacao == EstadoDePublicacao.ATIVO
                && disponibilidade.permiteVenda()
                && gruposDeOpcoes.stream().allMatch(GrupoDeOpcoes::satisfazivelHoje);
    }

    // ── preço ───────────────────────────────────────────────────────────────

    /**
     * A metade de C1 que vale desde o rascunho: preço existe e não é negativo.
     *
     * <p>Preço negativo não é desconto — desconto é {@link Opcao#acrescimo()}
     * negativo, dentro de um grupo, com nome. <b>Zero passa aqui e para na
     * publicação</b>: C1 é {@code precoBase > 0}, e em rascunho o comerciante
     * pode ainda não ter posto o preço. Um produto de graça só existe por
     * descuido, e o descuido é pego quando alguém vai vê-lo.
     */
    private static void exigirPrecoValido(Money precoBase) {
        if (precoBase == null) {
            throw new RegraDoCatalogoViolada("produto sem preço base");
        }
        if (precoBase.ehNegativo()) {
            throw new RegraDoCatalogoViolada(
                    "C1: preço base negativo — desconto é acréscimo negativo em opção, não preço");
        }
    }

    // ── acessores ───────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getEstabelecimentoId() {
        return estabelecimentoId;
    }

    public UUID getCategoriaId() {
        return categoriaId;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getImagemRef() {
        return imagemRef;
    }

    public Money getPrecoBase() {
        return precoBase;
    }

    public int getOrdem() {
        return ordem;
    }

    public EstadoDePublicacao getEstadoDePublicacao() {
        return estadoDePublicacao;
    }

    public ModoDeControle getModoDeControle() {
        return modoDeControle;
    }

    public Disponibilidade getDisponibilidade() {
        return disponibilidade;
    }

    /** Cópia imutável: quem quiser mudar grupo passa pela raiz. */
    public List<GrupoDeOpcoes> getGruposDeOpcoes() {
        return List.copyOf(gruposDeOpcoes);
    }

    /**
     * Descrição é opcional, e "em branco" é o mesmo que "não tem".
     *
     * <p>Sem esta normalização o cardápio ganharia produtos com descrição de
     * três espaços, que a tela renderiza como um parágrafo vazio e nenhuma
     * busca encontra.
     */
    public void descreverCom(String descricao) {
        this.descricao = Textos.normalizarOpcional(descricao);
    }

    /** Mesma regra da descrição. A referência da imagem é opcional. */
    public void ilustrarCom(String imagemRef) {
        this.imagemRef = Textos.normalizarOpcional(imagemRef);
    }
}
