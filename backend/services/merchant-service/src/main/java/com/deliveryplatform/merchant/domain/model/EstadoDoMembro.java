package com.deliveryplatform.merchant.domain.model;

/**
 * O ciclo de vida do vínculo.
 *
 * <p><b>Não há {@code CONVIDADO}, e a ausência é decisão desta rodada.</b> O
 * §1 do {@code estabelecimento.md} listava esse valor, mas a seção do convite
 * diz que <i>"o aceite cria o Membro com estado = ATIVO"</i> — e o
 * {@code Convite} tem estado próprio, {@code PENDENTE}. Nada escrevia
 * {@code CONVIDADO}: era um valor de enum sem produtor, a mesma
 * peça-que-nunca-rodou em outra forma.
 *
 * <p>Manter os dois exigiria que {@code Convite.PENDENTE} e
 * {@code Membro.CONVIDADO} concordassem para sempre — dois campos que precisam
 * concordar são uma invariante a testar até o fim dos tempos, que é o mesmo
 * argumento pelo qual o {@code identificadorNormalizado} da
 * {@code AreaDeEntrega} é derivado do nome em vez de guardado ao lado dele.
 * A pendência mora no {@code Convite}, que é raiz e sabe expirar.
 *
 * <p><b>{@code REMOVIDO} não apaga a linha.</b> O vínculo é registro de
 * responsabilidade: quem teve acesso àquela loja e quando. A ADR-029 já diz
 * isso para a recuperação — <i>"não se apaga ninguém para limpar"</i> — e o
 * {@code UNIQUE (usuario_id, estabelecimento_id)} faz a recontratação reusar o
 * mesmo vínculo em vez de criar um segundo.
 */
public enum EstadoDoMembro {

    ATIVO,
    SUSPENSO,
    REMOVIDO
}
