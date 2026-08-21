// Serviços documentais: MongoDB e Mongock.
// Mongock cumpre para o Mongo o papel que o Flyway cumpre no SQL — ADR-017.

plugins {
    id("delivery.spring-service-conventions")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(platform(catalog.findLibrary("mongock-bom").orElseThrow()))

    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation(catalog.findLibrary("mongock-springboot").orElseThrow())
    implementation(catalog.findLibrary("mongock-mongodb-driver").orElseThrow())

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x prefixa os módulos com "testcontainers-".
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mongodb")
}
