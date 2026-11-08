package com.deliveryplatform.catalog.api.dto;

import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * O pedido de um produto novo (ADR-065). <b>Só forma aqui</b>: presente, não branco, id
 * e enum que o Jackson entende. A regra é de quem a tem — preço negativo é a C1 do
 * agregado, e a categoria existir nesta loja é do caso de uso (ADR-066). Validar
 * qualquer dos dois aqui seria a borda cobrando regra de domínio.
 *
 * <p><b>Cada {@code @NotNull} fecha um ponto de integridade que o corpo alcançava</b>
 * (gatilho da ADR-063, medido na E-3c): sem eles, {@code categoriaId},
 * {@code precoBase} e {@code modoDeControle} ausentes chegavam ao construtor do
 * {@code Produto} como nulo e saíam 500 — e {@code ordem} ausente estourava no
 * desempacotamento, antes de chegar a ele.
 *
 * <p><b>O preço é decimal</b>, como o {@code precoBase} do {@code ProdutoResponse} — a
 * mesma forma de dinheiro nos dois sentidos do contrato.
 *
 * <p><b>Sem grupos</b>: o grupo e a opção têm rota própria, depois de o produto existir
 * (ADR-077).
 */
@Schema(description = "Um produto novo. Nasce rascunho; vai ao cardápio pela publicação.")
public record ProdutoRequest(

        @Schema(description = "A seção do cardápio, que tem de ser desta loja.")
        @NotNull(message = "categoriaId é obrigatório")
        UUID categoriaId,

        @Schema(description = "O nome como o cliente o lê.", example = "Coca-Cola lata")
        @NotBlank(message = "nome é obrigatório")
        String nome,

        @Schema(description = "Opcional.", example = "350 ml")
        String descricao,

        @Schema(description = "Em reais, duas casas. Zero é aceito no rascunho e recusado "
                + "na publicação (C1); negativo é recusado aqui.", example = "6.00")
        @NotNull(message = "precoBase é obrigatório")
        BigDecimal precoBase,

        @Schema(description = "SEM_CONTROLE (não acaba) ou QUALITATIVO.", example = "SEM_CONTROLE")
        @NotNull(message = "modoDeControle é obrigatório")
        ModoDeControle modoDeControle,

        @Schema(description = "A posição do produto na seção.", example = "0")
        @NotNull(message = "ordem é obrigatória")
        Integer ordem
) {
}
