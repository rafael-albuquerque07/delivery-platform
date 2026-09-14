package com.deliveryplatform.merchant.domain.exception;

/**
 * A1 e M3: quem tem {@code GERENCIAR_EQUIPE} administra {@code COLABORADOR},
 * nunca {@code ADMINISTRADOR}.
 *
 * <p>A mensagem é explícita, como o documento de domínio exige para as três
 * regras de escalada — <i>"nunca com erro genérico, nunca em silêncio"</i>. Ela
 * não sai daqui como está: a borda traduz para o 403 indistinguível de "não
 * existe" que M7 manda devolver. Quem lê esta mensagem é quem depura, não quem
 * sonda.
 */
public class SemAutoridadeSobreMembro extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SemAutoridadeSobreMembro(String motivo) {
        super(motivo);
    }
}
