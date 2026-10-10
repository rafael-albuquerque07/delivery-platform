package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.infrastructure.persistence.entity.ProdutoDocumento;
import com.deliveryplatform.catalog.infrastructure.persistence.mapper.ProdutoMapper;
import com.deliveryplatform.catalog.infrastructure.persistence.mapper.ProdutoMapper.DocumentoIlegivel;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Documento gravado que não volta a ser {@code Produto} é defeito do banco, não pedido
 * malformado (G-C3b).
 */
class ProdutoMapperTest {

    @Test
    @DisplayName("documento que viola regra do domínio vira DocumentoIlegivel, com o id e sem o conteúdo")
    void regra_violada_na_leitura_e_documento_ilegivel() {
        ProdutoDocumento bom = ProdutoMapper.paraDocumento(ProdutoDeTeste.margheritaPublicada());
        // Parseia inteiro — o problema não é de formato, é de regra: produto sem categoria.
        ProdutoDocumento semCategoria = new ProdutoDocumento(bom.id(), bom.estabelecimentoId(), null,
                bom.nome(), bom.descricao(), bom.imagemRef(), bom.precoBase(), bom.ordem(),
                bom.estadoDePublicacao(), bom.modoDeControle(), bom.disponibilidade(),
                bom.gruposDeOpcoes(), bom.versao());

        assertThatThrownBy(() -> ProdutoMapper.paraDominio(semCategoria))
                .isInstanceOf(DocumentoIlegivel.class)
                .hasMessageContaining(bom.id().toString())
                .hasMessageNotContaining(bom.nome())
                .hasCauseInstanceOf(RegraDoCatalogoViolada.class);
    }
}
