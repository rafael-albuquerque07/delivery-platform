// Apura jornada, custódia, divergência e fechamento — docs/dominio/liquidacao.md.
// Relacional porque o extrato é somatório sobre lançamentos e precisa de
// transação e de consulta agregada; documental não ajudaria em nada aqui.
plugins {
    id("delivery.jpa-conventions")
    id("delivery.messaging-conventions")
}
