// Convenções de Java aplicadas a todo módulo do backend.

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
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

repositories {
    mavenCentral()
}
