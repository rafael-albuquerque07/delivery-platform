// Redis: cache, TTL, idempotência, GEO e presença.

plugins {
    id("delivery.spring-service-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    testImplementation("org.testcontainers:junit-jupiter")
}
