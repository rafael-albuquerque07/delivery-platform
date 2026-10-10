package com.deliveryplatform.catalog.infrastructure.semeadura;

import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.config.SemeaduraProperties;
import com.deliveryplatform.catalog.domain.model.Disponibilidade;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.valuetypes.Money;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * O produto da fixture de desenvolvimento — ADR-059. <b>Andaime, não
 * fundação</b>: sai do repositório junto com o semeador do {@code merchant}.
 *
 * <p>Atrás de {@code delivery.semeadura.ligada}, falsa por padrão. Grava
 * <b>um</b> produto na loja da {@link Fixture}: publicado, {@code ESGOTADO_HOJE},
 * e com uma opção do grupo também {@code ESGOTADO_HOJE} — a reativação trata
 * produto e opção, e assim a prova cobre os dois caminhos.
 *
 * <h2>Pelo agregado, e é por isso que o id é sorteado</h2>
 *
 * {@code rascunho} → {@code acrescentarGrupo} → {@code publicar} → {@code marcar}:
 * o mesmo caminho que um comerciante faria, com C1, C4 e C5 cobradas na
 * publicação. A {@code reconstituir} daria um id fixo, mas pula as regras de
 * publicação — é leitura, não escrita. Então a idempotência é pela <b>loja</b>:
 * se ela já tem produto publicado, não faz nada.
 *
 * <h2>Trinta dias atrás, e não ontem</h2>
 *
 * Calcular "ontem" exigiria o fuso da loja e a hora de corte das 04:00, e esse
 * cálculo tem um lugar só, o {@code merchant} (ADR-046 §6). Trinta dias atrás,
 * em UTC, é estritamente menor que qualquer expediente corrente em qualquer
 * fuso, e o predicado da reativação é {@code <}.
 *
 * <p><b>Não espera a loja abrir e não sabe de expediente.</b> Grava e termina.
 * Quem reativa é o consumidor, quando o evento chegar.
 */
@Component
public class SemeaduraDoCatalogo implements ApplicationRunner {

    static final Duration IDADE_DO_CARIMBO = Duration.ofDays(30);

    private static final Logger log = LoggerFactory.getLogger(SemeaduraDoCatalogo.class);

    private final SemeaduraProperties propriedades;
    private final ProdutoRepositorio produtos;
    private final Clock relogio;

    public SemeaduraDoCatalogo(SemeaduraProperties propriedades,
                               ProdutoRepositorio produtos,
                               Clock relogio) {
        this.propriedades = propriedades;
        this.produtos = produtos;
        this.relogio = relogio;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (!propriedades.ligada()) {
            return;
        }
        if (produtos.publicadosDe(Fixture.LOJA, PageRequest.of(0, 1)).hasContent()) {
            log.info("semeadura: a loja da fixture já tem produto — nada a fazer");
            return;
        }
        Instant marcadoEm = relogio.instant().minus(IDADE_DO_CARIMBO);
        LocalDate carimbo = LocalDate.ofInstant(marcadoEm, ZoneOffset.UTC);
        Disponibilidade esgotado = Disponibilidade.esgotadoHoje(marcadoEm, carimbo);

        Produto produto = Produto.rascunho(Fixture.LOJA, Fixture.CATEGORIA, "Pizza margherita",
                Money.de("49.90"), ModoDeControle.QUALITATIVO, 0);
        produto.acrescentarGrupo(GrupoDeOpcoes.novo("Tamanho", 1, 1, 0, List.of(
                Opcao.nova("Pequena", Money.ZERO, 0),
                Opcao.nova("Média", Money.de("8.00"), 1),
                Opcao.nova("Grande", Money.de("16.00"), 2))));
        produto.publicar();
        produto.marcar(esgotado);
        GrupoDeOpcoes tamanho = produto.getGruposDeOpcoes().get(0);
        produto.marcarOpcao(tamanho.id(), tamanho.opcoes().get(2).id(), esgotado);

        Produto salvo = produtos.salvar(produto);
        log.info("semeadura: produto {} gravado na loja {}, ESGOTADO_HOJE com carimbo {}",
                salvo.getId(), Fixture.LOJA, carimbo);
    }
}
