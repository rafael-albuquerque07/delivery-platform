rootProject.name = "delivery-platform"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Gateway
include(":infra:gateway")

// Módulo compartilhado de tipos de valor — ADR-040, emenda à ADR-001.
// O único módulo que não é serviço nem gateway.
include(":value-types")

// Oito microsserviços de negócio — ADR-021.
// inventory e geolocation foram ADIADOS (marcos 10 e 11), não cancelados;
// notification foi absorvido pelo conversation-service e não volta.
include(":services:identity-service")
include(":services:merchant-service")
include(":services:catalog-service")
include(":services:settlement-service")
include(":services:order-service")
include(":services:payment-service")
include(":services:delivery-service")
include(":services:conversation-service")
