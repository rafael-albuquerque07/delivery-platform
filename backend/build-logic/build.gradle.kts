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

// implementation, não compileOnly: os convention plugins deste módulo aplicam o
// plugin do Spring Boot nos serviços que os usam. Isso executa o plugin, não só
// referencia seu tipo — então ele precisa estar disponível em runtime, no
// classpath deste build, e não só em tempo de compilação.
dependencies {
    implementation(
        catalogPlugin("spring-boot", "org.springframework.boot:spring-boot-gradle-plugin")
    )
}
