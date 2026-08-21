// Serviços documentais: MongoDB e Mongock.
// Mongock cumpre para o Mongo o papel que o Flyway cumpre no SQL — ADR-017.

import org.gradle.accessors.dm.LibrariesForLibs

val libs = the<LibrariesForLibs>()

plugins {
    id("delivery.spring-service-conventions")
}

dependencyManagement {
    imports {
        mavenBom(libs.mongock.bom.get().toString())
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation(libs.mongock.springboot)
    implementation(libs.mongock.mongodb.driver)

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
}
