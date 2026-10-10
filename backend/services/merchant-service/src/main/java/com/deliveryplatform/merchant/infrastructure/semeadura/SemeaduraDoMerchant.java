package com.deliveryplatform.merchant.infrastructure.semeadura;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.application.usecase.GerenciarEquipeService;
import com.deliveryplatform.merchant.config.SemeaduraProperties;
import com.deliveryplatform.merchant.domain.model.AreaDeEntrega;
import com.deliveryplatform.merchant.domain.model.Disponibilidade;
import com.deliveryplatform.merchant.domain.model.Documento;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.Faixa;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.domain.model.Identificacao;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Operacao;
import com.deliveryplatform.merchant.domain.model.Pausa;
import com.deliveryplatform.merchant.domain.model.PoliticaDeTroco;
import com.deliveryplatform.merchant.domain.model.Telefone;
import com.deliveryplatform.merchant.domain.model.TipoDeOperacao;
import com.deliveryplatform.valuetypes.Money;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A loja da fixture de desenvolvimento, e o vínculo de quem entra nela — ADR-059. <b>Andaime, não fundação</b>:
 * sai do repositório na rodada que der ao {@code merchant} as rotas de escrita
 * ({@code como-subir-local.md} §6).
 *
 * <p>Atrás de {@code delivery.semeadura.ligada}, falsa por padrão. Roda em toda
 * subida com a bandeira ligada, e cada metade é idempotente por si: a loja pelo id
 * da {@link Fixture}, o vínculo pelo par usuário e loja. Uma loja semeada antes do
 * vínculo existir (I-C) ganha o vínculo na subida seguinte, sem ressemear nada.
 *
 * <h2>Pelo agregado, e o id fixo por {@code reconstituir}</h2>
 *
 * Constrói os mesmos objetos de valor que o {@code LojaDeTeste} constrói, e grava
 * pelo {@link EstabelecimentoRepositorio}. A fábrica {@code novo} sorteia o id, e
 * o {@code catalog} precisa saber o id desta loja sem perguntar — por isso a
 * {@code reconstituir}, que passa pelo <b>mesmo construtor</b> e pelas mesmas
 * verificações (M9, M10). Se o agregado recusar, a subida quebra, que é o certo.
 *
 * <h2>A faixa começa no futuro, e é a decisão da ADR-059</h2>
 *
 * Uma abertura gera um evento. Se a varredura publicasse antes de o
 * {@code catalog} gravar o produto, o evento seria consumido sem nada para
 * reativar, e nenhum segundo evento viria. Com a faixa começando
 * {@link #ADIANTAMENTO} à frente, não há corrida: há uma espera.
 *
 * <p>O horário é semanal ({@code DayOfWeek} → faixas de {@code LocalTime}). O
 * dia da semana sai do <b>instante</b> de início convertido no fuso da loja, e
 * não do dia de hoje: semeada às 23h58, a faixa começa amanhã, e é amanhã que
 * ela tem de estar. O fim pode cair antes do início — a faixa cruza a
 * meia-noite, e o agregado sabe o que isso significa.
 *
 * <h2>O vínculo, pelo mesmo caminho do aceite de convite (W-D)</h2>
 *
 * {@code Membro.fundador}: {@code ADMINISTRADOR}, ativo, com todas as permissões —
 * a fábrica que existe para o primeiro vínculo de uma loja, que é o que este é.
 * O usuário é o da {@code Fixture} do {@code identity}, pelo mesmo id. E o vínculo
 * nascido vai ao outbox por {@code GerenciarEquipeService.registrarVinculoNascido},
 * <b>na mesma transação</b> — sem isso, um {@code catalog} que guardou "sem
 * vínculo" em cache não ficaria sabendo (invariante 7, ADR-043). O método exige
 * transação aberta ({@code MANDATORY}); é por isso que {@link #run} é
 * {@code @Transactional}.
 *
 * <p><b>Não grava marca d'água e não publica a abertura.</b> Quem publica é a
 * varredura, sozinha — é isso que a fixture existe para deixar ver.
 */
@Component
@EnableConfigurationProperties(SemeaduraProperties.class)
public class SemeaduraDoMerchant implements ApplicationRunner {

    /** Quanto à frente a faixa começa. Arredondado para baixo ao minuto, porque a faixa é de minutos. */
    static final Duration ADIANTAMENTO = Duration.ofMinutes(5);

    /** Quanto a faixa dura — o bastante para a prova e para olhar com calma. */
    static final Duration DURACAO = Duration.ofHours(3);

    /** O fuso da fixture, explícito — nunca o do contêiner (ADR-025). */
    static final FusoHorario FUSO = FusoHorario.PADRAO;

    private static final Logger log = LoggerFactory.getLogger(SemeaduraDoMerchant.class);

    private final SemeaduraProperties propriedades;
    private final EstabelecimentoRepositorio estabelecimentos;
    private final MembroRepositorio membros;
    private final GerenciarEquipeService equipes;
    private final Clock relogio;

    public SemeaduraDoMerchant(SemeaduraProperties propriedades,
                               EstabelecimentoRepositorio estabelecimentos,
                               MembroRepositorio membros,
                               GerenciarEquipeService equipes,
                               Clock relogio) {
        this.propriedades = propriedades;
        this.estabelecimentos = estabelecimentos;
        this.membros = membros;
        this.equipes = equipes;
        this.relogio = relogio;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments argumentos) {
        if (!propriedades.ligada()) {
            return;
        }
        Instant agora = relogio.instant();
        if (estabelecimentos.buscarPorId(Fixture.LOJA).isPresent()) {
            log.info("semeadura: a loja da fixture já existe");
        } else {
            semearLoja(agora);
        }
        if (membros.buscarPorUsuarioELoja(Fixture.USUARIO, Fixture.LOJA).isPresent()) {
            log.info("semeadura: o vínculo da fixture já existe");
        } else {
            Membro dono = membros.salvar(Membro.fundador(Fixture.USUARIO, Fixture.LOJA, agora));
            equipes.registrarVinculoNascido(dono, agora);
            log.info("semeadura: vínculo de {} com a loja {} gravado", Fixture.USUARIO, Fixture.LOJA);
        }
    }

    private void semearLoja(Instant agora) {
        ZonedDateTime inicio = agora.plus(ADIANTAMENTO).atZone(FUSO.zona()).truncatedTo(ChronoUnit.MINUTES);
        ZonedDateTime fim = inicio.plus(DURACAO);
        Faixa faixa = new Faixa(inicio.toLocalTime(), fim.toLocalTime());

        estabelecimentos.salvar(Estabelecimento.reconstituir(
                Fixture.LOJA,
                new Identificacao(
                        "Pizzaria da Fixture",
                        new Documento("12.345.678/0001-95"),
                        Telefone.de("(11) 98765-4321"),
                        "Rua das Palmeiras, 100",
                        "Centro",
                        FUSO),
                operacao(),
                new PoliticaDeTroco(Money.de("50.00"), false),
                new Disponibilidade(Map.of(inicio.getDayOfWeek(), List.of(faixa)), Pausa.nenhuma()),
                List.of(AreaDeEntrega.de("Centro", Money.de("5.00")))));

        log.info("semeadura: loja {} gravada, abre {} {}–{} ({})",
                Fixture.LOJA, inicio.getDayOfWeek(), faixa.inicio(), faixa.fim(), FUSO.zona());
    }

    private static Operacao operacao() {
        Map<Modalidade, Set<MetodoPagamento>> metodos = new EnumMap<>(Modalidade.class);
        metodos.put(Modalidade.ENTREGA, Set.of(MetodoPagamento.DINHEIRO, MetodoPagamento.PIX));
        metodos.put(Modalidade.RETIRADA, Set.of(MetodoPagamento.DINHEIRO));
        Map<Modalidade, Money> minimos = new EnumMap<>(Modalidade.class);
        minimos.put(Modalidade.ENTREGA, Money.de("25.00"));
        minimos.put(Modalidade.RETIRADA, Money.ZERO);
        return new Operacao(TipoDeOperacao.PRODUCAO, metodos, Money.de("5.00"), minimos);
    }
}
