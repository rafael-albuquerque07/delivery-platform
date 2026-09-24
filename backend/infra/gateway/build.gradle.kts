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

    // Sem spring-boot-starter-data-redis (ADR-044 §6).
    //
    // Ele estava aqui com o comentário "usado para rate limit", e não existe
    // RequestRateLimiter em lugar nenhum do repositório. O starter sozinho
    // registra um indicador de saúde, e um gateway sem Redis passaria a se
    // declarar fora de serviço por uma peça que ele não usa — exatamente o que
    // a emenda à ADR-021 acabou de remover do merchant-service.
    //
    // O limite de taxa que a ADR-012 atribui ao gateway volta quando houver
    // ambiente exposto, e com decisão própria: por IP ou por token, quanto por
    // minuto, e o que responder no estouro.
}
