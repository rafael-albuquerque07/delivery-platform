package com.deliveryplatform.identity.infrastructure.semeadura;

import com.deliveryplatform.identity.application.port.out.CodificadorDeSenha;
import com.deliveryplatform.identity.application.port.out.UsuarioRepositorio;
import com.deliveryplatform.identity.config.SemeaduraProperties;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.domain.model.Usuario;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * O usuário da fixture de desenvolvimento — o terceiro semeador da ADR-059, e o
 * que faz a loja da fixture responder por rota. <b>Andaime</b>: sai com os
 * outros dois.
 *
 * <p>Atrás de {@code delivery.semeadura.ligada}, falsa por padrão, e idempotente
 * pelo id da {@link Fixture}.
 *
 * <h2>A senha passa pelo cifrador do cadastro</h2>
 *
 * A porta {@link CodificadorDeSenha} — a mesma que o
 * {@code CadastrarUsuarioService} pede. Um hash literal aqui seria segredo no
 * repositório e ficaria errado no dia em que o cifrador mudasse. A senha vem de
 * {@code DELIVERY_SEMEADURA_SENHA} e não aparece em log nenhum.
 *
 * <h2>O id fixo, por {@code reconstituir} — e o que ele custa</h2>
 *
 * O {@code merchant} grava o vínculo deste usuário sem perguntar o id dele, então o
 * id é constante. A {@code novo} sorteia; a {@code reconstituir} passa pelo
 * mesmo construtor e pelas mesmas exigências, inclusive a U4.
 *
 * <p><b>O que ela não passa é o código de verificação.</b> O cadastro de verdade
 * carimba {@code telefoneVerificadoEm} no instante em que o código foi conferido
 * (ADR-042), e é isso que torna a U4 verdadeira em vez de afirmada. Aqui o
 * carimbo é o instante da semeadura: a U4 é <b>afirmada</b>, para um telefone que
 * ninguém atende. É aceitável num usuário que só existe com a bandeira ligada, e
 * é mais uma razão para a fixture sair do repositório quando as rotas de escrita
 * chegarem.
 */
@Component
@EnableConfigurationProperties(SemeaduraProperties.class)
public class SemeaduraDoIdentity implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SemeaduraDoIdentity.class);

    private final SemeaduraProperties propriedades;
    private final UsuarioRepositorio usuarios;
    private final CodificadorDeSenha codificador;
    private final Clock relogio;

    public SemeaduraDoIdentity(SemeaduraProperties propriedades,
                               UsuarioRepositorio usuarios,
                               CodificadorDeSenha codificador,
                               Clock relogio) {
        this.propriedades = propriedades;
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.relogio = relogio;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (!propriedades.ligada()) {
            return;
        }
        if (usuarios.buscarPorId(Fixture.USUARIO).isPresent()) {
            log.info("semeadura: o usuário da fixture já existe — nada a fazer");
            return;
        }
        Telefone telefone = Telefone.de(Fixture.TELEFONE);
        if (usuarios.existeComTelefone(telefone)) {
            // Outra conta tem o telefone da fixture. Gravar por cima trocaria a
            // senha de alguém; seguir em silêncio deixaria a loja sem quem entre.
            throw new IllegalStateException(
                    "semeadura: o telefone da fixture já pertence a outra conta — apague-a ou "
                            + "desligue a semeadura");
        }
        usuarios.salvar(Usuario.reconstituir(
                Fixture.USUARIO,
                "Dona da Fixture",
                telefone,
                relogio.instant(),
                null,
                null,
                codificador.codificar(propriedades.senha())));
        log.info("semeadura: usuário {} gravado, entra com o telefone da fixture", Fixture.USUARIO);
    }
}
