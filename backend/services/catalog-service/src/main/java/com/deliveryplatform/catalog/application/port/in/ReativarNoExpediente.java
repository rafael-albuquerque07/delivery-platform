package com.deliveryplatform.catalog.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * O ato que o {@code catalogo.md} §3 exige na abertura do expediente: devolver ao
 * cardápio todo produto e toda opção que acabou <b>num expediente anterior</b>.
 *
 * <p><b>Esta rodada não tem o consumidor do evento.</b> Ele nasce na G-C3b, com
 * fila durável, fila morta e política de retentativa — que são quatro
 * primeiras-vezes deste repositório e não cabem junto. O chamador desta rodada é
 * o teste de integração, e isso está escrito em vez de implícito.
 *
 * <p><b>Quem decide o que reativar é o agregado</b>, pelo
 * {@code Disponibilidade.deveReativarNoExpediente}, com {@code isBefore} e não
 * {@code !equals} (corrigido na G-C1). A consulta deste caso de uso só acha
 * candidatos: se ela e o predicado discordarem, <b>o predicado vence</b> e o
 * documento volta sem mudança. Há um teste que afirma exatamente isso.
 */
public interface ReativarNoExpediente {

    /**
     * @param estabelecimentoId a loja que abriu
     * @param expedienteQueAbriu o dia operacional do expediente que começou —
     *                           calculado pelo {@code merchant}, que é o único
     *                           lugar que sabe o fuso (ADR-046 §6), e que vem
     *                           <b>dentro do evento</b>. Esta rodada não pergunta
     *                           nada a ninguém, e é por isso que ela não depende
     *                           da decisão de identidade de serviço.
     */
    Resultado reativar(UUID estabelecimentoId, LocalDate expedienteQueAbriu);

    /**
     * O que aconteceu, em números que o consumidor da G-C3b vai registrar e que o
     * teste afirma.
     *
     * @param produtosAlterados documentos que mudaram e foram gravados
     * @param conflitosResolvidos gravações que falharam por versão e deram certo
     *                            na repetição (ADR-052). Zero é o caso normal;
     *                            maior que zero é a corrida acontecendo e sendo
     *                            tratada
     * @param candidatosSemMudanca documentos que a consulta devolveu e que o
     *                             predicado recusou. <b>Deveria ser sempre zero</b>
     *                             — a consulta é escrita para espelhar o predicado.
     *                             Diferente de zero significa que os dois
     *                             divergiram, e é por isso que este número existe
     *                             em vez de ser engolido
     */
    record Resultado(int produtosAlterados, int conflitosResolvidos, int candidatosSemMudanca) {

        public static final Resultado NADA = new Resultado(0, 0, 0);

        public Resultado mais(Resultado outro) {
            return new Resultado(
                    produtosAlterados + outro.produtosAlterados,
                    conflitosResolvidos + outro.conflitosResolvidos,
                    candidatosSemMudanca + outro.candidatosSemMudanca);
        }
    }
}
