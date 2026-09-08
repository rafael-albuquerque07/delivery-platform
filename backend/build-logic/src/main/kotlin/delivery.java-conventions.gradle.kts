// Convenções de Java aplicadas a todo módulo do backend.

import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
}

java {
    toolchain {
        // Toolchain garante o JDK 21 independentemente do que está instalado
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -processing avisa que nenhum processador reclamou as anotações.
    // Num projeto Spring isso é permanente e não acionável: as anotações
    // são de runtime, e o único processador declarado -- o do MapStruct --
    // só reclama as dele. Advertência que nunca pode ser resolvida treina
    // todo mundo a ignorar advertências, e a próxima, que importa, some
    // no meio.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters", "-Xlint:-processing"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Gradle não repassa -D da linha de comando para a JVM forkada do teste
    // sozinho -- só configura o processo do próprio Gradle. openapi.atualizar
    // (ADR-039) regrava o contrato commitado em vez de compará-lo, e precisa
    // deste repasse explícito para chegar ao teste.
    System.getProperty("openapi.atualizar")?.let { systemProperty("openapi.atualizar", it) }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

repositories {
    mavenCentral()
}
