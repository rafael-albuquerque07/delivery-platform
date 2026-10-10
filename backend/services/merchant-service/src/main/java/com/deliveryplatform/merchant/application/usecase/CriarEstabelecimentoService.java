package com.deliveryplatform.merchant.application.usecase;

import com.deliveryplatform.merchant.application.exception.CadastroDeLojaRecusado;
import com.deliveryplatform.merchant.application.port.in.CriarEstabelecimento;
import com.deliveryplatform.merchant.application.port.in.LojaDoUsuario;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.exception.DocumentoInvalido;
import com.deliveryplatform.merchant.domain.exception.FusoHorarioInvalido;
import com.deliveryplatform.merchant.domain.exception.TelefoneInvalido;
import com.deliveryplatform.merchant.domain.model.Disponibilidade;
import com.deliveryplatform.merchant.domain.model.Documento;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.domain.model.Identificacao;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Operacao;
import com.deliveryplatform.merchant.domain.model.PoliticaDeTroco;
import com.deliveryplatform.merchant.domain.model.Telefone;
import com.deliveryplatform.valuetypes.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * A loja e o fundador dela, na mesma transação (ADR-060).
 *
 * <h2>A ordem, e por que não é estilo</h2>
 *
 * <ol>
 *   <li>a loja é construída — e é <b>só aqui</b> que a recusa do agregado vira
 *       {@link CadastroDeLojaRecusado}, que o tratador responde com 400;</li>
 *   <li>a loja é salva <b>antes</b> do vínculo: a chave estrangeira de {@code membro}
 *       aponta para {@code estabelecimento} sem {@code cascade}
 *       ({@code V4__cria_membro.sql});</li>
 *   <li>o fundador nasce por {@code Membro.fundador} — a fábrica existe <i>"para que a
 *       loja nunca possa ser criada sem ele"</i>;</li>
 *   <li>o {@code VinculoAlteradoV1} vai ao outbox, dentro da transação: o
 *       {@code registrarVinculoNascido} é {@code MANDATORY}. Sem o evento, um
 *       {@code catalog} que guardou "sem vínculo" em cache recusaria o dono da loja
 *       nova pela janela de 60 s (ADR-011).</li>
 * </ol>
 *
 * <p><b>Sem autorização contra vínculo</b>, e é a única escrita assim: o vínculo é o
 * que ela cria (ADR-060 §1).
 */
@Service
public class CriarEstabelecimentoService implements CriarEstabelecimento {

    private final EstabelecimentoRepositorio estabelecimentos;
    private final MembroRepositorio membros;
    private final GerenciarEquipeService equipes;
    private final Clock relogio;

    public CriarEstabelecimentoService(EstabelecimentoRepositorio estabelecimentos,
                                       MembroRepositorio membros,
                                       GerenciarEquipeService equipes,
                                       Clock relogio) {
        this.estabelecimentos = estabelecimentos;
        this.membros = membros;
        this.equipes = equipes;
        this.relogio = relogio;
    }

    @Override
    @Transactional
    public LojaDoUsuario criar(UUID fundador, NovoEstabelecimento pedido) {
        // Microssegundos: é a precisão do timestamptz, e é o que o aceite de convite
        // faz — sem isto o instante gravado e o do evento diferem no nanossegundo.
        Instant agora = relogio.instant().truncatedTo(ChronoUnit.MICROS);

        Estabelecimento loja = estabelecimentos.salvar(construir(pedido));
        Membro dono = membros.salvar(Membro.fundador(fundador, loja.getId(), agora));
        equipes.registrarVinculoNascido(dono, agora);

        return new LojaDoUsuario(
                loja.getId(),
                loja.getIdentificacao().nome(),
                dono.getPapel(),
                List.copyOf(dono.getPermissoes()));
    }

    /**
     * O único ponto em que a recusa do agregado vira 400. Os tipos capturados são os
     * que os objetos de valor desta loja lançam — {@code IllegalArgumentException}
     * pelas invariantes (M12, M15, M17, texto obrigatório), e os três próprios do
     * documento, do telefone e do fuso (M16). Nada fora desta construção é capturado.
     */
    private static Estabelecimento construir(NovoEstabelecimento pedido) {
        try {
            return Estabelecimento.novo(
                    new Identificacao(
                            pedido.nome(),
                            new Documento(pedido.documento()),
                            Telefone.de(pedido.telefone()),
                            pedido.enderecoTextual(),
                            pedido.bairro(),
                            FusoHorario.de(pedido.fusoHorario())),
                    Operacao.nova(pedido.tipoDeOperacao(), pedido.metodosPorModalidade()),
                    new PoliticaDeTroco(
                            Money.de(pedido.fundoMaximoDeTroco()),
                            pedido.aceitaPedidoSemTrocoDisponivel()),
                    Disponibilidade.semHorario(),
                    List.of());
        } catch (IllegalArgumentException | DocumentoInvalido | TelefoneInvalido
                 | FusoHorarioInvalido recusa) {
            throw new CadastroDeLojaRecusado(recusa.getMessage(), recusa);
        }
    }
}
