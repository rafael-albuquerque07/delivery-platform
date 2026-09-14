package com.deliveryplatform.merchant.domain.model;

import com.deliveryplatform.valuetypes.Money;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Onde a loja entrega e por quanto (`estabelecimento.md` §5, ADR-020).
 *
 * <p><b>A normalização do nome é regra de domínio, não detalhe de banco.</b>
 * "Boa Viagem", "boa viagem" e "BOA  VIAGEM" são a mesma área — e se não forem,
 * a Marli cadastra a mesma duas vezes sem perceber e metade dos pedidos sai com
 * a taxa errada. O {@link #identificadorNormalizado()} é derivado do nome:
 * maiúsculas, sem acento, sem espaço duplo, aparado.
 *
 * <p><b>Zero é taxa válida</b>, e é diferente de não haver área — M11. Quem
 * procura área recebe {@code Optional}, nunca um {@code Money} zero por falta de
 * resposta; entregar de graça onde a loja não entrega é o erro que essa
 * distinção existe para impedir.
 *
 * <p><b>Faixas de CEP são opcionais.</b> A ADR-020 modela entrega por bairro
 * <b>nomeado</b> — é a palavra que o cliente diz na conversa. A faixa de CEP é o
 * refinamento de quem quer resolver o endereço sem perguntar, e uma área sem
 * faixa nenhuma é perfeitamente normal: ela só não é alcançável por CEP.
 *
 * <p>Faixas que se sobrepõem <b>dentro da mesma área</b> são permitidas: a taxa
 * é uma só, então sobrepor é redundância. Faixa idêntica repetida é recusada,
 * pelo mesmo motivo do horário — não significa nada, e a chave primária da
 * tabela também não a aceita.
 */
public record AreaDeEntrega(String nome, List<FaixaDeCep> faixasDeCep, Money taxa, boolean ativa) {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}");
    private static final Pattern ESPACOS = Pattern.compile("\\s+");

    public AreaDeEntrega {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome da área é obrigatório");
        }
        nome = nome.trim();

        Objects.requireNonNull(taxa, "taxa");
        // E3
        if (taxa.ehNegativo()) {
            throw new IllegalArgumentException("taxa de entrega não pode ser negativa: " + taxa);
        }

        Objects.requireNonNull(faixasDeCep, "faixasDeCep");
        TreeSet<FaixaDeCep> ordenadas = new TreeSet<>(faixasDeCep);
        if (ordenadas.size() != faixasDeCep.size()) {
            throw new IllegalArgumentException("faixa de CEP repetida em " + nome);
        }
        faixasDeCep = List.copyOf(new ArrayList<>(ordenadas));
    }

    /** Área alcançável só pelo nome — o caminho normal da ADR-020. */
    public static AreaDeEntrega de(String nome, Money taxa) {
        return new AreaDeEntrega(nome, List.of(), taxa, true);
    }

    public static AreaDeEntrega de(String nome, Money taxa, List<FaixaDeCep> faixas) {
        return new AreaDeEntrega(nome, faixas, taxa, true);
    }

    public AreaDeEntrega desativada() {
        return new AreaDeEntrega(nome, faixasDeCep, taxa, false);
    }

    /**
     * Derivado do nome, não guardado ao lado dele — dois campos que precisam
     * concordar são uma invariante a testar para sempre. É a chave da área
     * dentro da loja (E1, M9).
     */
    public String identificadorNormalizado() {
        return normalizar(nome);
    }

    /**
     * A mesma normalização, sem precisar de uma área montada — é o que o
     * agregado usa para procurar por nome, e ter duas implementações seria a
     * forma mais direta de "Boa Viagem" deixar de achar "boa viagem".
     */
    public static String normalizar(String nome) {
        Objects.requireNonNull(nome, "nome");
        String semAcento = MARCAS.matcher(Normalizer.normalize(nome, Normalizer.Form.NFD))
                .replaceAll("");
        return ESPACOS.matcher(semAcento.trim()).replaceAll(" ").toUpperCase(Locale.ROOT);
    }

    /** Falso quando a área não tem faixa nenhuma — ela não é alcançável por CEP. */
    public boolean cobre(String cep) {
        return faixasDeCep.stream().anyMatch(faixa -> faixa.contem(cep));
    }

    public boolean temMesmoIdentificadorQue(AreaDeEntrega outra) {
        return identificadorNormalizado().equals(outra.identificadorNormalizado());
    }
}
