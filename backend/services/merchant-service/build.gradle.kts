// Sem redis-conventions: nada no merchant usa Redis. O único motivo escrito era
// o cache de autorização, que a ADR-011 pôs em processo e a ADR-043 situou no
// serviço que pergunta. ADR-021, emenda de 24/09/2026.
plugins {
    id("delivery.jpa-conventions")
    id("delivery.messaging-conventions")
}

dependencies {
    // A única dependência de módulo do repositório que um serviço pode declarar.
    // A ADR-001, emendada pela ADR-040, diz: build-logic, :value-types e
    // bibliotecas externas -- e nada mais. Um segundo project(...) aqui é
    // violação da ADR-001, não uma linha a mais.
    implementation(project(":value-types"))
}
