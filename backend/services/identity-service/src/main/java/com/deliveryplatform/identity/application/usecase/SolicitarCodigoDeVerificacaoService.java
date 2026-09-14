package com.deliveryplatform.identity.application.usecase;

import com.deliveryplatform.identity.application.port.in.SolicitarCodigoDeVerificacao;
import com.deliveryplatform.identity.application.port.out.CodigoDeVerificacaoRepositorio;
import com.deliveryplatform.identity.application.port.out.GeradorDeCodigo;
import com.deliveryplatform.identity.application.port.out.UsuarioRepositorio;
import com.deliveryplatform.identity.domain.model.CodigoDeVerificacao;
import com.deliveryplatform.identity.domain.model.Telefone;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class SolicitarCodigoDeVerificacaoService implements SolicitarCodigoDeVerificacao {

    private final UsuarioRepositorio usuarios;
    private final CodigoDeVerificacaoRepositorio codigos;
    private final GeradorDeCodigo gerador;
    private final Clock relogio;

    public SolicitarCodigoDeVerificacaoService(
            UsuarioRepositorio usuarios,
            CodigoDeVerificacaoRepositorio codigos,
            GeradorDeCodigo gerador,
            Clock relogio) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.gerador = gerador;
        this.relogio = relogio;
    }

    /**
     * Telefone já cadastrado sai por aqui <b>em silêncio</b>: nenhum código é
     * criado e a resposta é a mesma de quem não tem conta (ADR-042 §5).
     *
     * <p>A alternativa — responder "já existe" — seria um oráculo de cadastro:
     * quem quisesse a lista de comerciantes deste produto a obteria varrendo
     * DDDs. E note que o silêncio só funciona porque o código também não volta
     * na resposta; se voltasse, a diferença entre ter e não ter conta seria
     * visível de qualquer jeito.
     *
     * <p>Telefone que não normaliza <b>não</b> sai em silêncio: o
     * {@code Telefone.de} lança, a borda devolve 400, e isso não vaza nada —
     * um número malformado não podia estar cadastrado de forma nenhuma.
     */
    @Override
    public void solicitar(String telefoneBruto) {
        Telefone telefone = Telefone.de(telefoneBruto);

        if (usuarios.existeComTelefone(telefone)) {
            return;
        }

        codigos.substituir(
                CodigoDeVerificacao.novo(telefone, gerador.gerar(), relogio.instant()));
    }
}
