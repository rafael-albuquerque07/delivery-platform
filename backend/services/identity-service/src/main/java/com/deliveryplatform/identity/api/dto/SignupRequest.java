package com.deliveryplatform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O cadastro inteiro numa chamada (ADR-042 §1).
 *
 * <p><b>Os dois limites da senha têm donos diferentes, e só um deles é
 * arbitrário.</b>
 *
 * <p>O <b>máximo de 72</b> não é escolha: o bcrypt ignora, em silêncio, tudo
 * que passa de 72 bytes. Sem este limite, uma senha longa seria aceita no
 * cadastro e conferida só até o septuagésimo segundo byte no login — e o
 * usuário teria uma senha mais curta do que pensa, sem que nada avisasse. O
 * limite é em caracteres e o bcrypt conta bytes, então acento come duas casas:
 * é conservador de propósito.
 *
 * <p>O <b>mínimo de 8</b> é proposta, não política. Nenhum documento do
 * repositório decide força de senha — {@code usuario.md} §3 fala só do hash — e
 * esta linha existe para não aceitar senha de um caractere enquanto a decisão
 * não é tomada. Quando for, ela sai daqui e vira regra com nome.
 *
 * <p>O <b>máximo de 120 no nome</b> é a largura da coluna. Sem ele, um nome
 * mais longo vira erro de banco no meio da transação em vez de 400 na borda.
 */
public record SignupRequest(
        @NotBlank String telefone,
        @NotBlank String codigo,
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(min = 8, max = 72) String senha) {
}
