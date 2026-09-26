package com.deliveryplatform.catalog.domain.model;

import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import java.time.Instant;
import java.time.LocalDate;

/**
 * O estado de disponibilidade de um produto, <b>com o carimbo de quando foi
 * marcado e de que expediente aquilo era</b>.
 *
 * <h2>Homônimo, e o documento é que manda</h2>
 *
 * <p>O {@code merchant} tem uma classe com este nome que significa outra coisa:
 * lá, {@code Disponibilidade} é o <i>horário de funcionamento</i> da loja. Aqui
 * é <i>tem ou não tem</i> do produto. Os dois nomes vêm dos documentos —
 * {@code estabelecimento.md} §4 e {@code catalogo.md} §1 —, e o código seguir o
 * documento vale mais do que o código inventar um terceiro nome que nenhum
 * documento usa. A ADR-031 exige nome único para <b>evento</b>, porque evento
 * atravessa serviço; isto não atravessa.
 *
 * <p>Se o homônimo incomodar, quem muda é o documento primeiro.
 *
 * <h2>Por que o carimbo é um par, e não dois campos soltos</h2>
 *
 * <p>{@code marcadoEm} é o instante e {@code expedienteDeReferencia} é o dia
 * operacional (ADR-025) que aquele instante ocupava <b>no fuso da loja</b>. Às
 * 01:30 de domingo o instante é domingo e o expediente é sábado. Os dois têm de
 * nascer juntos: um {@code ESGOTADO_HOJE} sem expediente não pode ser
 * comparado, e a reativação da §3 do {@code catalogo.md} é exatamente uma
 * comparação. É por isso que o construtor recusa o meio-termo.
 *
 * <p><b>O catálogo não calcula dia operacional.</b> Ele recebe o valor — no
 * payload do {@code ExpedienteAlteradoV1}, ou pela porta do {@code merchant} —
 * e compara. O cálculo tem um dono só (ADR-046 §6). Repare que não há
 * {@code FusoHorario} nesta classe, e isso é a garantia estrutural de que o
 * catálogo não vai derivar o dia por engano.
 *
 * @param estado                 o que o dia fez com o produto
 * @param marcadoEm              quando alguém disse isso; {@code null} enquanto
 *                               ninguém disse
 * @param expedienteDeReferencia o dia operacional de {@code marcadoEm};
 *                               {@code null} pelo mesmo motivo
 */
public record Disponibilidade(
        EstadoDeDisponibilidade estado,
        Instant marcadoEm,
        LocalDate expedienteDeReferencia
) {

    public Disponibilidade {
        if (estado == null) {
            throw new RegraDoCatalogoViolada("disponibilidade sem estado");
        }
        if ((marcadoEm == null) != (expedienteDeReferencia == null)) {
            throw new RegraDoCatalogoViolada(
                    "carimbo pela metade: marcadoEm e expedienteDeReferencia nascem juntos");
        }
        if (estado == EstadoDeDisponibilidade.ESGOTADO_HOJE && expedienteDeReferencia == null) {
            throw new RegraDoCatalogoViolada(
                    "ESGOTADO_HOJE sem expedienteDeReferencia nunca reativa");
        }
    }

    /**
     * Como todo produto nasce: disponível, sem carimbo, porque ninguém disse
     * nada ainda.
     *
     * <p>Nascer {@code DISPONIVEL} e não "desconhecido" é decisão do domínio:
     * quem cadastra um produto está dizendo que tem. O estado de quatro valores
     * existe para o comerciante <i>corrigir</i> isso durante o dia, não para
     * ele ter de afirmar o óbvio no cadastro.
     */
    public static Disponibilidade inicial() {
        return new Disponibilidade(EstadoDeDisponibilidade.DISPONIVEL, null, null);
    }

    public static Disponibilidade disponivel(Instant marcadoEm, LocalDate expediente) {
        return new Disponibilidade(EstadoDeDisponibilidade.DISPONIVEL, marcadoEm, expediente);
    }

    public static Disponibilidade acabando(Instant marcadoEm, LocalDate expediente) {
        return new Disponibilidade(EstadoDeDisponibilidade.ACABANDO, marcadoEm, expediente);
    }

    public static Disponibilidade esgotadoHoje(Instant marcadoEm, LocalDate expediente) {
        return new Disponibilidade(EstadoDeDisponibilidade.ESGOTADO_HOJE, marcadoEm, expediente);
    }

    public static Disponibilidade esgotadoIndeterminado(Instant marcadoEm, LocalDate expediente) {
        return new Disponibilidade(EstadoDeDisponibilidade.ESGOTADO_INDETERMINADO, marcadoEm, expediente);
    }

    public boolean permiteVenda() {
        return estado.permiteVenda();
    }

    /**
     * O predicado da reativação da §3, <b>escrito aqui e chamado na G-C</b>.
     *
     * <p>Ele mora nesta classe porque é uma pergunta sobre o carimbo, e porque
     * a alternativa — escrevê-lo dentro do consumidor do evento — colocaria a
     * regra do domínio na camada que fala RabbitMQ.
     *
     * <p>Repare no que ele <b>não</b> faz: não olha relógio, não olha fuso, não
     * chama {@code LocalDate.now()}. Recebe o expediente que abriu e compara.
     */
    public boolean deveReativarNoExpediente(LocalDate expedienteQueAbriu) {
        return estado == EstadoDeDisponibilidade.ESGOTADO_HOJE
                && !expedienteQueAbriu.equals(expedienteDeReferencia);
    }
}
