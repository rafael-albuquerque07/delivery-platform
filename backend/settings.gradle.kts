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

// Microsserviços de negócio
include(":services:identity-service")
include(":services:merchant-service")
include(":services:catalog-service")
include(":services:inventory-service")
include(":services:order-service")
include(":services:payment-service")
include(":services:delivery-service")
include(":services:geolocation-service")
include(":services:notification-service")
