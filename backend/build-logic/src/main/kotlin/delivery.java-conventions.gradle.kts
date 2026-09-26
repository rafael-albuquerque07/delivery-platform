// Convenções de Java aplicadas a todo módulo do backend.

import com.deliveryplatform.buildlogic.VerificarDependenciaEntreModulos
import org.gradle.api.artifacts.ProjectDependency
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

// ── ADR-001: um módulo depende de :value-types, e de mais nada ───────────────
//
// A pendência que a própria ADR-001 criou, e que ficou aberta desde o primeiro
// dia: "Uma regra que falhe se :services:X declarar :services:Y. Requisito do
// marco 1 — sem ela, a decisão central desta ADR depende de ninguém errar."
//
// Ela mora AQUI, e não no build de cada módulo, porque assim ela vale para todo
// módulo do backend sem que ninguém precise lembrar de aplicá-la — inclusive
// para o módulo que alguém criar amanhã.
val verificarDependenciaEntreModulos =
    tasks.register<VerificarDependenciaEntreModulos>("verificarDependenciaEntreModulos") {
        group = "verification"
        description = "Falha se este módulo declarar dependência de outro módulo " +
                "além de :value-types (ADR-001)."

        // project.path, e não `path`: dentro deste bloco `path` é o da TASK
        // (":servico:verificarDependenciaEntreModulos"), e a mensagem de falha
        // precisa nomear o módulo.
        modulo.set(project.path)
        permitidos.set(setOf(":value-types"))

        // Lido do grafo do Gradle, que é onde a informação está. O provider é
        // resolvido depois que o build.gradle.kts do módulo já declarou as
        // dependências — avaliar isto na aplicação do plugin veria uma lista
        // sempre vazia, e a regra nunca dispararia.
        arestasDeclaradas.set(provider {
            configurations
                .matching { it.isCanBeDeclared }
                .flatMap { configuracao ->
                    configuracao.dependencies
                        .withType(ProjectDependency::class.java)
                        .map { dependencia -> dependencia.path }
                }
                .distinct()
                .sorted()
        })
    }

// Pendurada no `check`, e não numa task que alguém precise lembrar de rodar:
// `./gradlew build` e o CI já chamam `check`. Uma verificação que depende de
// alguém escolher executá-la é a mesma coisa que um comentário pedindo cuidado.
tasks.named("check") {
    dependsOn(verificarDependenciaEntreModulos)
}

repositories {
    mavenCentral()
}
