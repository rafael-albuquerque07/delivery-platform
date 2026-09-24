package com.deliveryplatform.merchant.domain.evento;

import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Permissao;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * O vínculo de uma pessoa com um estabelecimento mudou.
 *
 * <p><b>Por que o nome da classe carrega o {@code V1}.</b> Porque o contrato
 * carrega. No dia em que o payload mudar de forma incompatível, nasce um
 * {@code VinculoAlteradoV2} ao lado deste, e os dois convivem enquanto houver
 * consumidor de cada um. Um campo de versão dentro de uma classe só faria a
 * migração acontecer com {@code if}.
 *
 * <p><b>O payload é estado, não delta.</b> {@code papel}, {@code estado} e
 * {@code permissoes} descrevem o vínculo <i>depois</i> da mudança, inteiro, e
 * não existe campo dizendo o que mudou. Isso é o que torna o evento aplicável
 * duas vezes sem estrago (a entrega é pelo menos uma vez) e o que permite a um
 * consumidor que perdeu um evento se consertar sozinho no próximo. Um evento que
 * dissesse "perdeu a permissão X" aplicado fora de ordem corromperia o
 * consumidor em silêncio.
 *
 * <p><b>{@code permissoes} é a lista completa, e ausência é negação.</b> Quem
 * aplica este evento substitui a lista que tinha. Tratá-la como incremento
 * transformaria revogação em concessão permanente — o oposto exato do que este
 * evento existe para fazer.
 *
 * <p><b>Nome e telefone não entram.</b> São dado do {@code identity-service}, e a
 * ADR-001 proíbe o {@code merchant} de importá-lo. Um evento é o jeito mais
 * silencioso de furar essa regra: ninguém revisa o payload de uma fila com a
 * atenção com que revisa um {@code import}.
 *
 * @see <a href="../../../../../../../../../contracts/eventos.md">contracts/eventos.md</a>
 */
public record VinculoAlteradoV1(
        UUID estabelecimentoId,
        UUID usuarioId,
        UUID membroId,
        Papel papel,
        EstadoDoMembro estado,
        List<Permissao> permissoes,
        Instant ocorridoEm
) implements EventoDeDominio {

    public VinculoAlteradoV1 {
        permissoes = List.copyOf(permissoes);
    }

    /**
     * Lê o evento do vínculo <b>já alterado</b>.
     *
     * <p>Chamada depois da operação de domínio, nunca antes: o que vai no payload
     * é o estado resultante. Ela não recebe "o que mudou" porque o contrato não
     * tem onde guardar isso.
     */
    public static VinculoAlteradoV1 de(Membro membro, Instant ocorridoEm) {
        return new VinculoAlteradoV1(
                membro.getEstabelecimentoId(),
                membro.getUsuarioId(),
                membro.getId(),
                membro.getPapel(),
                membro.getEstado(),
                // Ordenada para que o payload de um mesmo estado seja sempre o
                // mesmo texto: um Set não promete ordem, e um JSON que muda de
                // ordem sem o fato ter mudado atrapalha diff, log e teste.
                membro.getPermissoes().stream()
                        .sorted(Comparator.comparing(Enum::name))
                        .toList(),
                ocorridoEm);
    }

    /**
     * Sem o {@code V1}: a versão vive em {@link #versao()}, nunca dentro do
     * tipo ({@code contracts/README.md}). O nome da classe carrega o sufixo
     * porque é a forma abreviada do par, como nos documentos de domínio.
     */
    @Override
    public String tipo() {
        return "VinculoAlterado";
    }

    @Override
    public short versao() {
        return 1;
    }

    @Override
    public String agregado() {
        return "membro";
    }

    /**
     * O agregado é o <b>membro</b>, não o estabelecimento.
     *
     * <p>O estabelecimento aparece no payload porque o consumidor precisa dele
     * para indexar a resposta; mas o fato aconteceu num vínculo, e é o vínculo
     * que se deve poder rastrear no outbox com um {@code where agregado_id = ?}.
     */
    @Override
    public UUID agregadoId() {
        return membroId;
    }

    @Override
    public String chaveDeRota() {
        return "merchant.vinculo.alterado.v1";
    }
}
