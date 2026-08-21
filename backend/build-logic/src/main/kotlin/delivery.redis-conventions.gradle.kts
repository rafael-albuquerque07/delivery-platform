// Redis: cache, TTL, idempotência, GEO e presença.

plugins {
    id("delivery.spring-service-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Testcontainers 2.x prefixa os módulos com "testcontainers-".
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
