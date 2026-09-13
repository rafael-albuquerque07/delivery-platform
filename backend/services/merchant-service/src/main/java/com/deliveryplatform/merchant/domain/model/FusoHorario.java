package com.deliveryplatform.merchant.domain.model;

import com.deliveryplatform.merchant.domain.exception.FusoHorarioInvalido;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;

/**
 * O relógio da loja (ADR-025 §2, M16). Identificador IANA, nunca deslocamento
 * fixo, nunca nulo.
 *
 * <p><b>Por que identificador e não {@code -03:00}.</b> Hoje o Brasil não
 * observa horário de verão e o deslocamento funcionaria. Mas
 * {@code America/Recife} e {@code America/Sao_Paulo} têm o mesmo deslocamento
 * agora e regras históricas diferentes: se o horário de verão voltar, São Paulo
 * observa e Recife não, e a loja gravada como {@code -03:00} passa a errar uma
 * hora sem nenhum sinal. Por isso {@link #de(String)} recusa
 * {@code "-03:00"} — o {@code ZoneId} que ele produz é um {@code ZoneOffset},
 * cujo id não está no conjunto abaixo. Não é efeito colateral da validação: é o
 * que M16 existe para impedir.
 *
 * <p><b>Por que a lista é fixa aqui.</b> Nenhuma API do JDK mapeia zona para
 * país — {@code ZoneId.getAvailableZoneIds()} devolve as seiscentas do mundo, e
 * a informação de território mora no tzdb, que o Java não expõe. A ADR-025
 * registra essa lista como consequência negativa assumida: <i>"é uma validação a
 * manter"</i>. Quando o produto sair do Brasil, é uma linha a remover.
 *
 * <p><b>Apelido de compatibilidade não entra.</b> {@code Brazil/East} é um link
 * do tzdb que o {@code ZoneId.of} aceita e devolve com o id dele mesmo, não
 * resolvido. Aceitá-lo daria duas grafias para a mesma zona — o mesmo problema
 * de unicidade que o {@link Telefone} resolve normalizando, e aqui não há o que
 * normalizar sem embutir uma tabela de apelidos que o JDK não dá.
 */
public record FusoHorario(ZoneId zona) {

    /**
     * As dezesseis zonas do Brasil no tzdb. Ordem alfabética para a conferência
     * ser possível a olho.
     */
    private static final Set<String> ZONAS_BRASILEIRAS = Set.of(
            "America/Araguaina",
            "America/Bahia",
            "America/Belem",
            "America/Boa_Vista",
            "America/Campo_Grande",
            "America/Cuiaba",
            "America/Eirunepe",
            "America/Fortaleza",
            "America/Maceio",
            "America/Manaus",
            "America/Noronha",
            "America/Porto_Velho",
            "America/Recife",
            "America/Rio_Branco",
            "America/Santarem",
            "America/Sao_Paulo");

    /**
     * O que o cadastro traz preenchido (ADR-025 §2). O comerciante troca num
     * toque; quase nenhum vai trocar, e é por isso que o padrão precisa ser o
     * certo para a maioria.
     */
    public static final FusoHorario PADRAO = de("America/Sao_Paulo");

    public FusoHorario {
        if (zona == null || !ZONAS_BRASILEIRAS.contains(zona.getId())) {
            throw new FusoHorarioInvalido(zona == null ? null : zona.getId());
        }
    }

    public static FusoHorario de(String identificador) {
        if (identificador == null) {
            throw new FusoHorarioInvalido(null);
        }
        try {
            return new FusoHorario(ZoneId.of(identificador.trim()));
        } catch (DateTimeException naoEhZona) {
            throw new FusoHorarioInvalido(identificador);
        }
    }

    /** O que vai para a coluna e para o payload do evento. */
    public String identificador() {
        return zona.getId();
    }

    /**
     * O conjunto aceito, imutável.
     *
     * <p>Existe porque a tela de cadastro precisa exatamente desta lista — a
     * ADR-025 §2 diz que o fuso é <i>campo visível no cadastro</i>, e um campo
     * visível com dezesseis opções é um seletor, não texto livre. Sem este
     * método, a tela reescreveria a lista, e duas listas divergem.
     */
    public static Set<String> identificadoresAceitos() {
        return ZONAS_BRASILEIRAS;
    }
}
