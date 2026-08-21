plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

// Dentro do build-logic o acessor tipado `libs` NÃO existe — ele é gerado para os
// projetos que consomem o catálogo, e este build é justamente quem os produz.
// A saída é ler o catálogo pela API, que funciona aqui e nos convention plugins.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun catalogPlugin(alias: String, artifact: String): String {
    val plugin = catalog.findPlugin(alias).orElseThrow {
        IllegalStateException("alias de plugin '$alias' não existe em gradle/libs.versions.toml")
    }.get()
    return "$artifact:${plugin.version.requiredVersion}"
}

dependencies {
    implementation(
        catalogPlugin("spring-boot", "org.springframework.boot:spring-boot-gradle-plugin")
    )
}
