// Módulo de tipos de valor compartilhado (ADR-040). Não é serviço: sem Spring,
// sem bootJar, sem banco, sem Dockerfile.

import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("delivery.java-conventions")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    // O BOM entra só como platform: ele gerencia versões e não põe uma única
    // classe do Spring no classpath. É o que permite herdar as versões de JUnit
    // e AssertJ que o resto do repositório usa sem trazer o framework para
    // dentro do módulo que a ADR-040 proíbe de conhecê-lo -- e o
    // TiposDeValorNaoConhecemFrameworkTest confere que ele não entrou.
    //
    // As versões ficam aqui e não no build do módulo porque a ADR-001 diz que
    // build-logic é o único lugar onde o BOM do Spring e o ArchUnit são
    // declarados.
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation(catalog.findLibrary("archunit-junit5").orElseThrow())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
