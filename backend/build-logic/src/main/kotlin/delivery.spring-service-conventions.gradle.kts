// Convenções de todo microsserviço Spring Boot: web, validação, segurança como
// Resource Server, actuator, OpenAPI e a base de testes.

import org.gradle.accessors.dm.LibrariesForLibs

val libs = the<LibrariesForLibs>()

plugins {
    id("delivery.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.bom.get().toString())
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Todo serviço valida JWT; apenas o identity-service também emite.
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation(libs.springdoc.webmvc)

    compileOnly(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.archunit.junit5)
}

// A imagem é construída pelo Dockerfile de cada módulo (multi-stage, não root),
// não por bootBuildImage — mantém o build reproduzível e a base sob controle.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}
