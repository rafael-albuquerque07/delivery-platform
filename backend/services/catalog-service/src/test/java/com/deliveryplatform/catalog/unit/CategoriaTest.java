package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.model.Categoria;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.deliveryplatform.catalog.support.ProdutoDeTeste.LOJA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoriaTest {

    @Test
    void nasce_ativa() {
        Categoria c = Categoria.nova(LOJA, "Pizzas salgadas", 0);

        assertThat(c.isAtiva())
                .as("quem cria uma seção quer mostrá-la")
                .isTrue();
        assertThat(c.getEstabelecimentoId()).isEqualTo(LOJA);
    }

    @Test
    void sem_estabelecimento() {
        assertThatThrownBy(() -> Categoria.nova(null, "Pizzas", 0))
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .hasMessageContaining("estabelecimento");
    }

    @Test
    void nome_em_branco() {
        assertThatThrownBy(() -> Categoria.nova(LOJA, "  ", 0))
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .hasMessageContaining("nome da categoria");
    }

    @Test
    @DisplayName("desativar a seção inteira é um clique — é para isso que ela é raiz própria")
    void desativa_e_volta() {
        Categoria sorvetes = Categoria.nova(LOJA, "Sorvetes", 3);

        sorvetes.desativar();
        assertThat(sorvetes.isAtiva()).isFalse();

        sorvetes.ativar();
        assertThat(sorvetes.isAtiva()).isTrue();
    }

    @Test
    void renomear_tambem_recusa_branco() {
        Categoria c = Categoria.nova(LOJA, "Pizzas", 0);

        assertThatThrownBy(() -> c.renomear(""))
                .isInstanceOf(RegraDoCatalogoViolada.class);
        assertThat(c.getNome()).isEqualTo("Pizzas");
    }

    @Test
    @DisplayName("categoria inativa NÃO torna o produto invendável — a §5 não a menciona")
    void categoria_inativa_nao_derruba_o_produto() {
        Categoria c = Categoria.nova(LOJA, "Sorvetes", 3);
        c.desativar();
        Produto p = ProdutoDeTeste.margheritaPublicada();

        assertThat(p.vendavel())
                .as("desativar a seção é montagem do cardápio: a seção some e os produtos "
                        + "com ela. Quem chegar ao produto por link direto ou por um pedido "
                        + "em conversa compra. Se isso estiver errado, quem muda é a §5")
                .isTrue();
    }

    @Test
    @DisplayName("o agregado não valida o outro agregado: quem cruza loja e categoria é o caso de uso")
    void a_categoria_de_outra_loja_nao_e_recusada_aqui() {
        Produto p = ProdutoDeTeste.margherita();

        assertThat(p.getCategoriaId())
                .as("o produto guarda o id e não sabe se ele existe nem de quem é. "
                        + "A regra nasce com a rota que cria produto, na G-B")
                .isEqualTo(ProdutoDeTeste.CATEGORIA);
    }
}
