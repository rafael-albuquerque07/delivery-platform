package com.deliveryplatform.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Os dois números da reativação. <b>Nenhum é segredo</b> — tamanho de lote e teto
 * de repetição não são credencial —, e por isso moram no {@code application.yml} e
 * não em variável de ambiente.
 *
 * <p>{@code record}, como o {@code CacheDaAutorizacaoProperties} e o
 * {@code EscutaProperties} deste serviço. <b>Ao contrário deles, valida no
 * construtor canônico</b> — a forma do {@code JwtProperties} do {@code identity},
 * sem Bean Validation —, porque aqui zero não é um valor estranho, é um que faz a
 * varredura relatar sucesso sem ter feito nada.
 *
 * @param lote quantos identificadores a consulta traz por vez. Não é paginação de
 *             exposição — nada disto sai numa resposta HTTP —, é teto de memória: a
 *             varredura carrega <b>identificadores</b>, e relê cada produto na sua
 *             própria transação. O {@code CLAUDE.md} exige {@code Pageable} em rota;
 *             aqui a forma escrita é este teto
 * @param tentativas quantas vezes um documento é refeito depois de um conflito de
 *                   versão (ADR-052). Três, e não mais: conflito aqui é duas pessoas
 *                   no mesmo produto no mesmo segundo. Se três não bastam, o
 *                   problema não é a repetição
 */
@ConfigurationProperties(prefix = "delivery.reativacao")
public record ReativacaoProperties(int lote, int tentativas) {

    public ReativacaoProperties {
        if (lote < 1) {
            throw new IllegalStateException(
                    "delivery.reativacao.lote precisa ser pelo menos 1 — "
                            + "zero faria a varredura terminar sem examinar nada e relatar sucesso");
        }
        if (tentativas < 1) {
            throw new IllegalStateException(
                    "delivery.reativacao.tentativas precisa ser pelo menos 1 — "
                            + "zero faria toda gravação desistir antes da primeira tentativa");
        }
    }
}
