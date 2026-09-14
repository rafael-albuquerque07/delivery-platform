package com.deliveryplatform.identity.application.usecase;

import com.deliveryplatform.identity.application.exception.CadastroRecusado;
import com.deliveryplatform.identity.application.port.in.CadastrarUsuario;
import com.deliveryplatform.identity.application.port.out.CodificadorDeSenha;
import com.deliveryplatform.identity.application.port.out.CodigoDeVerificacaoRepositorio;
import com.deliveryplatform.identity.application.port.out.UsuarioRepositorio;
import com.deliveryplatform.identity.domain.model.CodigoDeVerificacao;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.domain.model.Usuario;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class CadastrarUsuarioService implements CadastrarUsuario {

    private final UsuarioRepositorio usuarios;
    private final CodigoDeVerificacaoRepositorio codigos;
    private final CodificadorDeSenha codificador;
    private final Clock relogio;

    public CadastrarUsuarioService(
            UsuarioRepositorio usuarios,
            CodigoDeVerificacaoRepositorio codigos,
            CodificadorDeSenha codificador,
            Clock relogio) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.codificador = codificador;
        this.relogio = relogio;
    }

    /**
     * <b>Este método não é {@code @Transactional}, e isso é a decisão mais
     * importante da classe.</b>
     *
     * <p>Uma tentativa falha precisa ser gravada. Se a gravação do contador e a
     * exceção de recusa estivessem na mesma transação, o rollback desfaria o
     * incremento — e o limite de cinco tentativas nunca chegaria a um. Força
     * bruta de seis dígitos sairia de graça, e o teste que conta tentativas
     * passaria, porque em memória o contador sobe.
     *
     * <p>O preço é que o caminho feliz são duas transações: a conta nasce numa,
     * o código morre na outra. Se a segunda falhar, sobra um código órfão para
     * um telefone que agora tem conta — e ele já não serve para nada, porque
     * {@code existeComTelefone} barra o cadastro seguinte. É a troca certa: o
     * pior caso deste lado é uma linha a mais numa tabela; do outro lado era
     * um fluxo de verificação que não verifica.
     */
    @Override
    public UUID cadastrar(String telefoneBruto, String codigoInformado, String nome, String senha) {
        Telefone telefone = Telefone.de(telefoneBruto);
        Instant agora = relogio.instant();

        CodigoDeVerificacao codigo = codigos.buscarPorTelefone(telefone)
                .orElseThrow(CadastroRecusado::new);

        if (!codigo.confere(codigoInformado, agora)) {
            codigos.substituir(codigo);
            throw new CadastroRecusado();
        }

        // Corrida: entre o pedido do código e este instante, alguém pode ter
        // criado a conta. Mesma recusa, pelo mesmo motivo de sempre.
        if (usuarios.existeComTelefone(telefone)) {
            throw new CadastroRecusado();
        }

        // O carimbo é o instante em que o código foi conferido, e é isso que
        // torna a U4 verdadeira em vez de afirmada: o telefone nasce verificado
        // porque alguém provou que o atende, não porque o construtor exigiu.
        UUID id = salvar(Usuario.novo(nome, telefone, agora, codificador.codificar(senha)));

        codigos.removerDe(telefone);
        return id;
    }

    private UUID salvar(Usuario usuario) {
        try {
            return usuarios.salvar(usuario).getId();
        } catch (DataIntegrityViolationException e) {
            // O UNIQUE (telefone) perdeu a corrida acima por uma fração.
            throw new CadastroRecusado();
        }
    }
}
