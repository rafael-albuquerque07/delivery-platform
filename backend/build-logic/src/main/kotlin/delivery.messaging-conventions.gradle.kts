// Serviços que publicam ou consomem eventos de negócio.
// Quem publica precisa de Outbox; quem consome precisa de processed_messages.

plugins {
    id("delivery.spring-service-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x prefixa os módulos com "testcontainers-".
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
    testImplementation("org.awaitility:awaitility")
}
