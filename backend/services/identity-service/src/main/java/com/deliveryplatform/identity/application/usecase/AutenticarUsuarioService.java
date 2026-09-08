package com.deliveryplatform.identity.application.usecase;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.deliveryplatform.identity.application.exception.CredenciaisInvalidas;
import com.deliveryplatform.identity.application.port.in.AutenticarUsuario;
import com.deliveryplatform.identity.application.port.out.CodificadorDeSenha;
import com.deliveryplatform.identity.application.port.out.EmissorDeToken;
import com.deliveryplatform.identity.application.port.out.TokenEmitido;
import com.deliveryplatform.identity.application.port.out.UsuarioRepositorio;
import com.deliveryplatform.identity.domain.exception.TelefoneInvalido;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.domain.model.Usuario;

@Service
public class AutenticarUsuarioService implements AutenticarUsuario {

    private final UsuarioRepositorio usuarios;
    private final CodificadorDeSenha codificador;
    private final EmissorDeToken emissor;

    /**
     * Um hash que não pertence a ninguém, calculado uma vez na subida.
     *
     * <p>Existe para que o caminho "telefone não cadastrado" gaste o mesmo tempo
     * que "senha errada". Sem ele o corpo da resposta se recusa a dizer qual das
     * duas foi, e o relógio diz: bcrypt custa dezenas de milissegundos, e não
     * gastá-los é resposta imediata. Um 401 que volta rápido demais é um "esse
     * telefone não existe" escrito em outra língua.
     */
    private final String hashDescartavel;

    public AutenticarUsuarioService(
            UsuarioRepositorio usuarios,
            CodificadorDeSenha codificador,
            EmissorDeToken emissor) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.emissor = emissor;
        this.hashDescartavel = codificador.codificar(UUID.randomUUID().toString());
    }

    @Override
    public TokenEmitido autenticar(String telefoneBruto, String senha) {
        Optional<Usuario> encontrado = procurar(telefoneBruto);

        if (encontrado.isEmpty()) {
            codificador.confere(senha, hashDescartavel);
            throw new CredenciaisInvalidas();
        }

        Usuario usuario = encontrado.get();

        if (!codificador.confere(senha, usuario.getHashDaSenha())) {
            throw new CredenciaisInvalidas();
        }

        // A U3 -- canal só autentica com verificadoEm != null -- não vira
        // checagem aqui porque a U4 já a garante por construção: não existe
        // Usuario com telefone não verificado. Escrever o if seria código que
        // nunca executa. No dia em que a troca de telefone existir
        // (usuario.md §7), ela cria o primeiro estado não verificado -- e é
        // essa rodada que precisa acrescentar a checagem, não esta.

        return emissor.emitir(usuario.getId());
    }

    private Optional<Usuario> procurar(String telefoneBruto) {
        try {
            return usuarios.buscarPorTelefone(Telefone.de(telefoneBruto));
        } catch (TelefoneInvalido e) {
            // Número que não normaliza não pode estar cadastrado: a U1 exige
            // forma canônica. Tratar como "não encontrado" mantém uma saída só.
            return Optional.empty();
        }
    }
}
