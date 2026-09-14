package com.deliveryplatform.merchant.infrastructure.persistence.mapper;

import com.deliveryplatform.merchant.domain.model.Disponibilidade;
import com.deliveryplatform.merchant.domain.model.Documento;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.Faixa;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.domain.model.Identificacao;
import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Operacao;
import com.deliveryplatform.merchant.domain.model.Pausa;
import com.deliveryplatform.merchant.domain.model.PoliticaDeTroco;
import com.deliveryplatform.merchant.domain.model.Telefone;
import com.deliveryplatform.merchant.infrastructure.persistence.entity.EstabelecimentoJpaEntity;
import com.deliveryplatform.merchant.infrastructure.persistence.entity.HorarioJpa;
import com.deliveryplatform.merchant.infrastructure.persistence.entity.MetodoAceitoJpa;
import com.deliveryplatform.merchant.infrastructure.persistence.entity.PausaJpa;
import com.deliveryplatform.valuetypes.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Escrito à mão, não MapStruct — pelo mesmo motivo do
 * {@code UsuarioJpaMapper}: a volta do banco passa por {@code Documento},
 * {@code Telefone.de(...)}, {@code FusoHorario.de(...)} e {@code Money.de(...)},
 * que validam. Um mapeador gerado atribuiria campo a campo e traria de volta um
 * estado que o domínio recusaria.
 *
 * <p>E aqui ele faz um trabalho que nenhum gerador faria sozinho: <b>reagrupar</b>,
 * duas vezes. A tabela guarda pares planos (modalidade, método) e trios planos
 * (dia, início, fim); o domínio quer {@code Map<Modalidade, Set<MetodoPagamento>>}
 * e {@code Map<DayOfWeek, List<Faixa>>}. A ida achata, a volta agrupa — e a
 * ordenação das faixas por início é reposta pelo próprio
 * {@code Disponibilidade}, não aqui, para haver um lugar só que decide o que é
 * um horário válido.
 *
 * <p><b>A volta assume BRL.</b> A coluna guarda só o valor — é o que a ADR-009
 * especifica —, e {@code Money.de(BigDecimal)} constrói em real. Se um dia
 * houver segunda moeda, é migration por coluna e mudança aqui.
 */
@Component
public class EstabelecimentoJpaMapper {

    public EstabelecimentoJpaEntity paraEntidade(Estabelecimento estabelecimento) {
        Identificacao identificacao = estabelecimento.getIdentificacao();
        Operacao operacao = estabelecimento.getOperacao();
        PoliticaDeTroco troco = estabelecimento.getPoliticaDeTroco();

        Set<MetodoAceitoJpa> metodos = new LinkedHashSet<>();
        operacao.metodosPorModalidade().forEach((modalidade, aceitos) ->
                aceitos.forEach(metodo -> metodos.add(new MetodoAceitoJpa(modalidade, metodo))));

        // EnumMap: ordem do enum, estável, e o diff de um UPDATE fica legível.
        Map<Modalidade, BigDecimal> minimos = new EnumMap<>(Modalidade.class);
        operacao.pedidoMinimoPorModalidade()
                .forEach((modalidade, valor) -> minimos.put(modalidade, valor.valor()));

        Set<HorarioJpa> horarios = new LinkedHashSet<>();
        estabelecimento.getDisponibilidade().horarioDeFuncionamento().forEach((dia, faixas) ->
                faixas.forEach(faixa -> horarios.add(
                        new HorarioJpa(dia, faixa.inicio(), faixa.fim()))));

        Pausa pausa = estabelecimento.getDisponibilidade().pausa();

        return new EstabelecimentoJpaEntity(
                estabelecimento.getId(),
                identificacao.nome(),
                identificacao.documento().numero(),
                identificacao.telefone().numero(),
                identificacao.enderecoTextual(),
                identificacao.bairro(),
                identificacao.fusoHorario().identificador(),
                operacao.tipoDeOperacao(),
                operacao.descontoDeRetirada().valor(),
                troco.fundoMaximoDeTroco().valor(),
                troco.aceitaPedidoSemTrocoDisponivel(),
                new PausaJpa(pausa.ativa(), pausa.pausadoAte(), pausa.motivo()),
                metodos,
                minimos,
                horarios);
    }

    public Estabelecimento paraDominio(EstabelecimentoJpaEntity entidade) {
        Identificacao identificacao = new Identificacao(
                entidade.getNome(),
                new Documento(entidade.getDocumento()),
                Telefone.de(entidade.getTelefone()),
                entidade.getEnderecoTextual(),
                entidade.getBairro(),
                FusoHorario.de(entidade.getFusoHorario()));

        Map<Modalidade, Set<MetodoPagamento>> metodos = new EnumMap<>(Modalidade.class);
        for (MetodoAceitoJpa par : entidade.getMetodosAceitos()) {
            metodos.computeIfAbsent(par.getModalidade(), qualquer -> new LinkedHashSet<>())
                    .add(par.getMetodo());
        }

        Map<Modalidade, Money> minimos = new EnumMap<>(Modalidade.class);
        entidade.getPedidoMinimo()
                .forEach((modalidade, valor) -> minimos.put(modalidade, Money.de(valor)));

        Operacao operacao = new Operacao(
                entidade.getTipoDeOperacao(),
                metodos,
                Money.de(entidade.getDescontoDeRetirada()),
                minimos);

        PoliticaDeTroco troco = new PoliticaDeTroco(
                Money.de(entidade.getFundoMaximoDeTroco()),
                entidade.isAceitaPedidoSemTrocoDisponivel());

        Map<DayOfWeek, List<Faixa>> horario = new EnumMap<>(DayOfWeek.class);
        for (HorarioJpa linha : entidade.getHorarios()) {
            horario.computeIfAbsent(linha.getDiaDaSemana(), qualquer -> new ArrayList<>())
                    .add(new Faixa(linha.getInicio(), linha.getFim()));
        }

        PausaJpa pausaSalva = entidade.getPausa();
        Pausa pausa = pausaSalva.isAtiva()
                ? new Pausa(true, pausaSalva.getAte(), pausaSalva.getMotivo())
                : Pausa.nenhuma();

        Disponibilidade disponibilidade = new Disponibilidade(horario, pausa);

        return Estabelecimento.reconstituir(
                entidade.getId(), identificacao, operacao, troco, disponibilidade);
    }
}
