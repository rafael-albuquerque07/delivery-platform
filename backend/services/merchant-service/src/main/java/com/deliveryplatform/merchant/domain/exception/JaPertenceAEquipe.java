package com.deliveryplatform.merchant.domain.exception;

/**
 * Aceitar um convite tendo já um vínculo <b>ativo</b> naquela loja.
 *
 * <p><b>Aparece no aceite, e não no convite</b>, porque é só lá que o
 * {@code usuarioId} existe: o convite endereça um telefone, e traduzir telefone
 * em usuário é dado do {@code identity-service}. A fronteira entre os dois
 * serviços decide onde esta checagem cabe.
 *
 * <p><b>É distinta da {@code ConviteInvalido} de propósito.</b> Quem chegou
 * aqui apresentou um token válido, então não está adivinhando nada — e "você já
 * está nesta equipe" é a única resposta que evita a pessoa pedir outro convite
 * cinco vezes.
 */
public class JaPertenceAEquipe extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JaPertenceAEquipe() {
        super("esta pessoa já tem vínculo ativo nesta loja");
    }
}
