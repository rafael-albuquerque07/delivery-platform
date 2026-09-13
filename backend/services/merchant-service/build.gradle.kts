plugins {
    id("delivery.jpa-conventions")
    id("delivery.messaging-conventions")
    id("delivery.redis-conventions")
}

dependencies {
    // A única dependência de módulo do repositório que um serviço pode declarar.
    // A ADR-001, emendada pela ADR-040, diz: build-logic, :value-types e
    // bibliotecas externas -- e nada mais. Um segundo project(...) aqui é
    // violação da ADR-001, não uma linha a mais.
    implementation(project(":value-types"))
}
