// Redis: cache do cardápio público do catalog, e nada mais. É o único uso que
// sobreviveu — ADR-005, "O Redis fica, para cache", e catalogo.md §7. Os outros
// que este cabeçalho listava (TTL de carrinho, idempotência, GEO, presença)
// morreram cada um numa decisão própria; a tabela está na ADR-021, emenda de
// 26/09/2026. Serviço que aplicar este plugin aponta o porquê na coluna
// "Por quê" da ADR-021, ou o starter vira dependência sem dono.

plugins {
    id("delivery.spring-service-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Testcontainers 2.x prefixa os módulos com "testcontainers-".
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
