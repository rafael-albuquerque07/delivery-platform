package com.deliveryplatform.identity.application.exception;

/**
 * Uma recusa para todos os motivos: código errado, código expirado, tentativas
 * estouradas, nenhum código pedido, e telefone que passou a ter conta entre o
 * pedido e o cadastro.
 *
 * <p><b>Não carrega qual deles foi</b>, pelo mesmo motivo que a
 * {@code CredenciaisInvalidas} não carrega (ADR-037 §7, ADR-042 §5): a
 * diferença entre duas respostas é informação sobre o cadastro, e uma lista de
 * telefones que têm conta neste produto é uma lista de comerciantes.
 *
 * <p>Telefone que não normaliza é outra coisa e não passa por aqui — vira 400,
 * porque um número malformado não podia estar cadastrado de jeito nenhum.
 */
public class CadastroRecusado extends RuntimeException {

    public CadastroRecusado() {
        super("cadastro recusado");
    }
}
