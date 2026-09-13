package com.deliveryplatform.merchant.domain.model;

import com.deliveryplatform.merchant.domain.exception.TelefoneInvalido;

import java.util.regex.Pattern;

/**
 * O telefone de contato da loja (`estabelecimento.md` §1, {@code identificacao}).
 * É o número que o cliente liga e o que a ADR-029 §3 aceita como um dos
 * elementos <i>que só quem opera a loja controla</i>.
 *
 * <p><b>Esta é uma cópia deliberada do {@code Telefone} do
 * {@code identity-service}, e a ADR-040 recusou compartilhá-los por escrito.</b>
 * A sintaxe é a mesma — E.164, com a presunção de Brasil da P2 — e o significado
 * não é: lá o telefone é o <b>identificador de login</b>, com unicidade global
 * garantida por U1 e pelo índice único do {@code identity}; aqui é um campo de
 * contato, que duas lojas da mesma família podem ter igual sem que nada quebre.
 * Um tipo só sustentaria a impressão de que são a mesma coisa, e o dia em que o
 * {@code identity} precisasse de uma regra de unicidade no tipo, ela chegaria de
 * carona no cadastro de loja.
 *
 * <p>As duas cópias vão divergir, e divergir é o comportamento correto. Se um dia
 * convergirem de verdade, o caminho está escrito: emenda à ADR-040, com o
 * argumento.
 *
 * <p>O construtor canônico não pode ser mais restrito que o record (a linguagem
 * proíbe), mas valida com rigor: só aceita E.164 já pronto. A entrada pensada
 * para uso normal é {@link #de(String)}.
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
     * Normaliza para E.164 antes de validar. Entrada sem {@code +} com 10 ou 11
     * dígitos é lida como brasileira e ganha {@code +55} — presunção da P2
     * (comércio de bairro brasileiro). Com 12 ou 13 dígitos começando em
     * {@code 55}, o DDI já está escrito e falta só o sinal.
     *
     * <p>A ordem importa: {@code 55987654321} tem onze dígitos e é celular do
     * DDD 55 (Santa Maria), não DDI 55 seguido de nove dígitos — por isso o
     * comprimento decide antes do prefixo, e este ramo vem depois do de 10/11.
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
