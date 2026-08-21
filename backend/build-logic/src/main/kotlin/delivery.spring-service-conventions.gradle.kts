// Convenções de todo microsserviço Spring Boot: web, validação, segurança como
// Resource Server, actuator, OpenAPI e a base de testes.

import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("delivery.java-conventions")
    id("org.springframework.boot")
}

// Em convention plugin, o acessor tipado `libs` não está disponível.
// Ler o catálogo pela API funciona e não depende de código gerado.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    // O plugin do Boot não importa o BOM sozinho — sem estes platform(),
    // as dependências sem versão abaixo não resolvem.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform(catalog.findLibrary("spring-cloud-bom").orElseThrow()))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Todo serviço valida JWT; apenas o identity-service também emite.
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation(catalog.findLibrary("springdoc-webmvc").orElseThrow())

    compileOnly(catalog.findLibrary("mapstruct").orElseThrow())
    annotationProcessor(catalog.findLibrary("mapstruct-processor").orElseThrow())

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(catalog.findLibrary("archunit-junit5").orElseThrow())
}

// A imagem é construída pelo Dockerfile de cada módulo (multi-stage, não root),
// não por bootBuildImage — mantém o build reproduzível e a base sob controle.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}
