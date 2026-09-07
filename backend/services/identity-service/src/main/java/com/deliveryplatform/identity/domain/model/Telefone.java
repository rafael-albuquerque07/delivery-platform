package com.deliveryplatform.identity.domain.model;

import com.deliveryplatform.identity.domain.exception.TelefoneInvalido;

import java.util.regex.Pattern;

/**
 * O identificador de login (ADR-036). Guardado sempre em E.164 — a unicidade
 * de U1 depende de existir uma única grafia por número.
 *
 * <p>O construtor canônico não pode ser mais restrito que o record (a
 * linguagem proíbe), mas valida com rigor: só aceita E.164 já pronto. A
 * entrada pensada para uso normal é {@link #de(String)}, que normaliza —
 * inclusive a presunção de Brasil — antes de chamar este construtor. Chamar o
 * construtor direto com um número fora do padrão falha do mesmo jeito, só
 * que sem a normalização.
 */
public record Telefone(String numero) {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    private static final Pattern CARACTERES_DE_FORMATACAO = Pattern.compile("[\\s()\\-]");
    private static final Pattern NAO_DIGITO = Pattern.compile("\\D");

    public Telefone {
        if (numero == null || !E164.matcher(numero).matches()) {
            throw new TelefoneInvalido(numero);
        }
    }

    /**
     * Normaliza para E.164 antes de validar. Entrada sem {@code +} com 10 ou
     * 11 dígitos é lida como brasileira e ganha {@code +55} — presunção da
     * P2 (comércio de bairro brasileiro), testada aqui e em lugar nenhum
     * mais. Com 12 ou 13 dígitos começando em {@code 55}, o DDI já está
     * escrito e falta só o sinal. A ordem importa: {@code 55987654321} tem
     * onze dígitos e é celular do DDD 55, não DDI 55 — por isso o
     * comprimento decide antes do prefixo, e este ramo vem depois do de
     * 10/11.
     */
    public static Telefone de(String bruto) {
        if (bruto == null) {
            throw new TelefoneInvalido(null);
        }

        String semFormatacao = CARACTERES_DE_FORMATACAO.matcher(bruto).replaceAll("");

        if (semFormatacao.startsWith("+")) {
            return new Telefone(semFormatacao);
        }

        String digitos = NAO_DIGITO.matcher(semFormatacao).replaceAll("");
        if (digitos.length() == 10 || digitos.length() == 11) {
            return new Telefone("+55" + digitos);
        }

        if ((digitos.length() == 12 || digitos.length() == 13) && digitos.startsWith("55")) {
            return new Telefone("+" + digitos);
        }

        return new Telefone(semFormatacao);
    }
}
