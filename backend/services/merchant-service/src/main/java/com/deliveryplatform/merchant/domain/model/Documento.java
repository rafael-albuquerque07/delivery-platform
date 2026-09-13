package com.deliveryplatform.merchant.domain.model;

import com.deliveryplatform.merchant.domain.exception.DocumentoInvalido;

import java.util.regex.Pattern;

/**
 * CPF ou CNPJ do estabelecimento (`estabelecimento.md` §1, {@code identificacao}).
 * Guardado só com dígitos, para que a mesma inscrição não exista em duas
 * grafias.
 *
 * <p><b>Não há conferência de dígito verificador, e isso é decisão.</b> Nenhuma
 * invariante deste serviço depende de o documento ser aritmeticamente válido, e
 * a ADR-029 §3 diz por quê: <i>o documento do estabelecimento é público no
 * Brasil</i> — um ex-funcionário sabe, um estranho descobre — e por isso ele
 * <b>não prova titularidade de nada</b>. Um dígito verificador correto não o
 * torna menos público; ele só faria o campo parecer mais confiável do que é, que
 * é exatamente o erro que a ADR-029 existe para não deixar cometer. O que o
 * sistema exige como prova está lá, e não é isto.
 *
 * <p>Quando um caso de uso precisar recusar documento malformado na entrada — a
 * tela de cadastro, provavelmente — a checagem é de borda, com Bean Validation
 * no DTO, e não vira regra de domínio.
 *
 * <p><b>{@code toString} não mostra o número.</b> O {@code CLAUDE.md} manda
 * registrar log sem documento, e {@code toString} é como um campo chega ao log
 * sem ninguém ter decidido isso: basta um {@code log.info("{}", estabelecimento)}.
 * Quem precisa do valor chama {@link #numero()}, que é explícito.
 */
public record Documento(String numero) {

    private static final Pattern NAO_DIGITO = Pattern.compile("\\D");

    private static final int DIGITOS_CPF = 11;
    private static final int DIGITOS_CNPJ = 14;

    public Documento {
        String digitos = numero == null ? "" : NAO_DIGITO.matcher(numero).replaceAll("");
        if (digitos.length() != DIGITOS_CPF && digitos.length() != DIGITOS_CNPJ) {
            throw new DocumentoInvalido(digitos.length());
        }
        numero = digitos;
    }

    @Override
    public String toString() {
        return "Documento[oculto]";
    }
}
