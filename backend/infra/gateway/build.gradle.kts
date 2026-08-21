plugins {
    id("delivery.spring-service-conventions")
}

dependencies {
    // Variante WebMVC do gateway, e não a reativa: as convenções já trazem
    // spring-boot-starter-web (servlet), e o Gateway reativo é incompatível com
    // Spring MVC no classpath — a aplicação falha no boot.
    //
    // Se este artefato não resolver, confira o nome no BOM do Spring Cloud
    // 2025.1.x: a linha 4.2 reorganizou os starters do Gateway
    // (…-gateway-server-webmvc, …-gateway-server-webflux).
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc")

    // Redis não reativo, coerente com o modelo servlet — usado para rate limit.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
}
