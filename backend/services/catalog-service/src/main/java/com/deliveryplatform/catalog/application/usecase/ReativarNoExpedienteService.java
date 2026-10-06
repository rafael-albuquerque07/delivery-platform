package com.deliveryplatform.catalog.application.usecase;

import com.deliveryplatform.catalog.application.port.in.ReativarNoExpediente;
import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.config.ReativacaoProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * A varredura, em lotes de identificadores, sem transação própria.
 *
 * <p><b>Sem {@code @Transactional} aqui, e isso é a decisão.</b> Uma transação só
 * para a loja inteira tornaria a repetição impossível (ADR-052) e manteria aberta
 * uma transação do tamanho do cardápio. Cada produto é a sua própria unidade de
 * trabalho, em {@link ReativacaoDeUmProduto}.
 *
 * <p>Consequência que fica escrita: <b>a varredura não é atômica.</b> Se ela
 * falhar no meio, parte do cardápio foi reativada e parte não. É aceitável porque
 * a operação é idempotente pela comparação de carimbo — refazer não desfaz nada e
 * não reativa o que acabou agora —, e é por isso que a repetição do evento é
 * inofensiva.
 */
@Service
public class ReativarNoExpedienteService implements ReativarNoExpediente {

    private static final Logger log = LoggerFactory.getLogger(ReativarNoExpedienteService.class);

    private final ProdutoRepositorio produtos;
    private final ReativacaoDeUmProduto umProduto;
    private final ReativacaoProperties propriedades;

    public ReativarNoExpedienteService(
            ProdutoRepositorio produtos,
            ReativacaoDeUmProduto umProduto,
            ReativacaoProperties propriedades) {
        this.produtos = produtos;
        this.umProduto = umProduto;
        this.propriedades = propriedades;
    }

    @Override
    public Resultado reativar(UUID estabelecimentoId, LocalDate expedienteQueAbriu) {
        Resultado total = Resultado.NADA;

        while (true) {
            List<UUID> candidatos = produtos.idsParaReativar(
                    estabelecimentoId, expedienteQueAbriu, propriedades.lote());
            if (candidatos.isEmpty()) {
                return total;
            }

            Resultado doLote = Resultado.NADA;
            for (UUID id : candidatos) {
                doLote = doLote.mais(comRepeticao(id, expedienteQueAbriu));
            }
            total = total.mais(doLote);

            // A guarda que impede o laço infinito, e ela não é zelo: a consulta
            // busca sempre o primeiro lote, porque o documento reativado deixa de
            // casar com o filtro. Se um lote inteiro não mudar nada, ele vai voltar
            // igual na próxima volta, para sempre.
            //
            // Lote sem progresso significa que a consulta e o predicado divergiram.
            // Parar e relatar transforma um travamento numa medida.
            if (doLote.produtosAlterados() == 0) {
                log.warn(
                        "reativacao interrompida: {} candidatos e nenhuma mudanca — "
                                + "a consulta e o predicado divergiram (loja {}, expediente {})",
                        candidatos.size(), estabelecimentoId, expedienteQueAbriu);
                return total;
            }
        }
    }

    /**
     * Repete a unidade de trabalho enquanto o conflito de versão disser que o
     * documento mudou debaixo dela. Esgotado o teto, a exceção <b>sobe</b>: quem
     * tem fila morta para decidir é o consumidor da G-C3b, e engolir aqui seria
     * relatar sucesso sobre um produto que ficou esgotado.
     */
    private Resultado comRepeticao(UUID produtoId, LocalDate expedienteQueAbriu) {
        int conflitos = 0;
        while (true) {
            try {
                Resultado r = umProduto.reativarUm(produtoId, expedienteQueAbriu);
                return new Resultado(
                        r.produtosAlterados(), conflitos, r.candidatosSemMudanca());
            } catch (OptimisticLockingFailureException conflito) {
                conflitos++;
                if (conflitos >= propriedades.tentativas()) {
                    throw conflito;
                }
                // Sem espera entre tentativas, e de propósito: o conflito é com
                // outra escrita que já terminou. A releitura seguinte vê o estado
                // novo. Esperar aqui seria esperar por nada.
            }
        }
    }
}
