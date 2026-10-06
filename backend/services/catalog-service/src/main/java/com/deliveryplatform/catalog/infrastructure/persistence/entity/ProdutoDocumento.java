package com.deliveryplatform.catalog.infrastructure.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.UUID;

/**
 * O produto como ele fica gravado — e ele é <b>outro tipo</b>, de propósito.
 *
 * <p>O domínio não pode importar Spring (o {@code HexagonalArchitectureTest}
 * recusa), então {@code @Document} não cabe no {@link
 * com.deliveryplatform.catalog.domain.model.Produto}. Mas a separação vale
 * mesmo sem a regra: o agregado muda quando o negócio muda, e o documento muda
 * quando o banco muda. São dois relógios.
 *
 * <h2>Três decisões de formato, e nenhuma delas é o padrão do framework</h2>
 *
 * <p><b>1 · Dinheiro é texto.</b> {@code {"valor": "49.90", "moeda": "BRL"}}.
 * Não é {@code double} — isso seria o erro que a ADR-009 existe para impedir —
 * e não é o mapeamento automático de {@code BigDecimal}, cuja representação
 * depende de configuração do conversor e muda entre versões do Spring Data.
 * Texto é exato, legível no shell e independente de biblioteca. O que se perde
 * é ordenar e somar no banco; o catálogo não faz nem um nem outro, e quando
 * fizer isso é migração, não surpresa.
 *
 * <p><b>2 · Dia operacional é texto ISO.</b> {@code "2026-09-25"}. Um
 * {@code LocalDate} mapeado pelo padrão vira {@code Date}, que é um
 * <i>instante</i>: ele ganha uma hora (meia-noite) e um fuso (o do processo
 * que gravou) que a data nunca teve. A reativação do expediente compara datas
 * (C11); um fuso a mais nessa comparação é o mesmo defeito da hora de corte,
 * por outra porta.
 *
 * <p><b>3 · Instante é texto ISO também.</b> {@code Instant} tem nanossegundos
 * e {@code Date} tem milissegundos: o mapeamento padrão trunca, em silêncio, e
 * um teste que grava e lê de volta falha por três casas decimais que ninguém
 * pediu. Texto ISO-8601 não trunca. Custo assumido: não dá para consultar por
 * intervalo. <b>Gatilho escrito:</b> quando alguém precisar consultar
 * {@code marcadoEm} por intervalo, o campo vira {@code Date} e o código passa a
 * truncar <i>explicitamente</i>, com teste que afirma o truncamento.
 *
 * <p><b>E os enums são texto.</b> Não por estilo: um enum mapeado por tipo faz
 * o documento depender da lista de valores do Java. O {@code ModoDeControle}
 * tem dois valores hoje e ganha o terceiro no marco 10; o documento não deve
 * saber disso. Valor desconhecido na leitura vira erro com mensagem, no
 * mapeador — nunca {@code null} silencioso.
 */
@Document(collection = "produtos")
public record ProdutoDocumento(
        @Id UUID id,
        UUID estabelecimentoId,
        UUID categoriaId,
        String nome,
        String descricao,
        String imagemRef,
        DinheiroDocumento precoBase,
        int ordem,
        String estadoDePublicacao,
        String modoDeControle,
        DisponibilidadeDocumento disponibilidade,
        List<GrupoDocumento> gruposDeOpcoes,
        /*
         * A versão do documento (ADR-052) — o primeiro {@code @Version} do
         * repositório. {@code Long} e não {@code long}: nula é o que diz ao
         * Spring Data que o documento é novo e deve ser inserido. Primitiva
         * começaria em zero, e toda inserção viraria a atualização de um
         * documento que não existe.
         */
        @Version Long versao
) {

    /** Texto, e não número. Ver a decisão 1 no javadoc da classe. */
    public record DinheiroDocumento(String valor, String moeda) {
    }

    /**
     * {@code marcadoEm} e {@code expedienteDeReferencia} são os dois texto, e
     * os dois são {@code null} juntos — é a mesma regra do carimbo pela metade
     * que o objeto de valor do domínio recusa.
     */
    public record DisponibilidadeDocumento(
            String estado,
            String marcadoEm,
            String expedienteDeReferencia
    ) {
    }

    public record GrupoDocumento(
            UUID id,
            String nome,
            int minEscolhas,
            int maxEscolhas,
            int ordem,
            List<OpcaoDocumento> opcoes
    ) {
    }

    public record OpcaoDocumento(
            UUID id,
            String nome,
            DinheiroDocumento acrescimo,
            DisponibilidadeDocumento disponibilidade,
            int ordem
    ) {
    }
}
