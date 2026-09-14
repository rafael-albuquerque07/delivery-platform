package com.deliveryplatform.merchant.domain.exception;

import com.deliveryplatform.merchant.domain.model.Permissao;

import java.util.Set;
import java.util.TreeSet;

/**
 * A2, M4 e M5: {@code concedidas ⊆ próprias} — e a simetria vale para tirar
 * também.
 *
 * <p>Sem a simetria, um gerente que não tem {@code VER_VENDAS} rebaixa colegas
 * até o conjunto vazio usando uma permissão que ele próprio não possui.
 */
public class PermissaoNaoPossuida extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PermissaoNaoPossuida(Set<Permissao> faltantes) {
        super("o autor não possui: " + new TreeSet<>(faltantes));
    }
}
