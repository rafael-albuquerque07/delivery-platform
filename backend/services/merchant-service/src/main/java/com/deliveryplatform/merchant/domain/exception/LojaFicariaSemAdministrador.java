package com.deliveryplatform.merchant.domain.exception;

/**
 * A3 e M6: um estabelecimento sempre mantém pelo menos um
 * {@code ADMINISTRADOR} {@code ATIVO}.
 *
 * <p><b>Uma operação lança isto, e só uma: sair da própria loja.</b> As seis
 * operações administrativas não conseguem violar A3 — a demonstração está em
 * {@code Equipe.administradoresAtivos()} —, porque todas exigem um autor
 * administrador ativo diferente do alvo. A saída é a única que não tem autor
 * separado do alvo, e por isso é a única que precisa contar.
 *
 * <p>A mensagem é explícita e diz o que fazer, porque quem está saindo quase
 * sempre não percebeu que era o último — e a alternativa, um erro genérico,
 * mandaria a pessoa tentar de novo até desistir.
 */
public class LojaFicariaSemAdministrador extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LojaFicariaSemAdministrador() {
        super("você é o único administrador ativo: promova alguém antes de sair (M6)");
    }
}
