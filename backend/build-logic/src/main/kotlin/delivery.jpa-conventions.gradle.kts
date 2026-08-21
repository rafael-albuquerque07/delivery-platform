// Serviços relacionais: JPA, PostgreSQL, Flyway e Testcontainers.
// Não há H2 — ver ADR-014.

plugins {
    id("delivery.spring-service-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x prefixa os módulos com "testcontainers-" (ex.: era
    // "org.testcontainers:junit-jupiter", agora "org.testcontainers:testcontainers-junit-jupiter").
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}
