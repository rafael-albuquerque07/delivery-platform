// O pacote NÃO se chama `build`: o `.gitignore` da raiz ignora qualquer
// diretório `build/`, e com esse nome este arquivo nunca teria entrado no Git —
// o build passaria nesta máquina e quebraria em todo clone.
package com.deliveryplatform.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * A regra que a ADR-001 exige por escrito desde o primeiro dia:
 *
 * > *"Uma regra que falhe se `:services:X` declarar `:services:Y`. Requisito do
 * > marco 1 — sem ela, a decisão central desta ADR depende de ninguém errar."*
 *
 * ## Por que task, e não teste
 *
 * A ADR-040 virou teste — `TiposDeValorNaoConhecemFrameworkTest` — porque ela
 * proíbe **imports dentro de um módulo**, e import é classe: o ArchUnit enxerga.
 *
 * Esta é outra coisa. Ela proíbe **arestas entre módulos**, e aresta é
 * declaração de build, não classe. Um teste não veria um
 * `implementation(project(":services:x"))` declarado e ainda não usado — e para
 * enxergar todos os módulos teria de morar num módulo que depende de todos, que
 * já seria a violação. A informação está no Gradle; a verificação mora onde a
 * informação está.
 *
 * E ela não precisa compilar nada para decidir: lê o grafo. (Nada a obriga a
 * rodar antes do `compileJava` — na prova de 24/09 ela foi a primeira a
 * falhar, mas por ordem de agendamento, não por dependência declarada.)
 *
 * ## O que é permitido
 *
 * Uma única aresta: `:value-types`, que a ADR-040 abriu como exceção à ADR-001.
 * Não é "nenhum módulo": é **um, e só um** — e é exatamente por a regra ter
 * deixado de ser absoluta que ela passou a precisar de quem a verifique.
 *
 * `build-logic` não entra na conta: ele é um *included build*, aplicado como
 * plugin, e não uma dependência de projeto.
 */
abstract class VerificarDependenciaEntreModulos : DefaultTask() {

    /** O módulo sendo verificado, para que a mensagem de falha diga quem errou. */
    @get:Input
    abstract val modulo: Property<String>

    /** Caminhos de projeto que este módulo declara, em qualquer configuração. */
    @get:Input
    abstract val arestasDeclaradas: ListProperty<String>

    /** A lista de exceções. Curta de propósito. */
    @get:Input
    abstract val permitidos: SetProperty<String>

    @TaskAction
    fun verificar() {
        val quem = modulo.get()
        val liberados = permitidos.get()
        val proibidas = arestasDeclaradas.get().filterNot { it in liberados }

        if (proibidas.isEmpty()) {
            logger.info("ADR-001: {} declara {} — em conformidade.",
                    quem, arestasDeclaradas.get().ifEmpty { listOf("nenhum módulo") })
            return
        }

        // A mensagem é metade da regra. Uma falha de build que diz só "proibido"
        // faz a pessoa procurar a regra; esta diz o que fazer com o código.
        throw GradleException(
            """
            |ADR-001 violada em $quem
            |
            |  Dependência de módulo declarada e não permitida:
            |${proibidas.joinToString("\n") { "      $it" }}
            |
            |  Permitido: ${liberados.sorted().joinToString(", ")} — e nada mais.
            |
            |  Um módulo do backend depende de build-logic, de :value-types e de
            |  bibliotecas externas. Um segundo project(...) não é uma linha a
            |  mais: é a decisão central da ADR-001 sendo desfeita em silêncio,
            |  porque a partir dela dois serviços passam a compilar juntos e a
            |  separação vira aspiração.
            |
            |  O que fazer, em ordem de preferência:
            |    1. Chamar o outro serviço pela porta dele (HTTP) ou reagir a um
            |       evento — é assim que serviço fala com serviço aqui.
            |    2. Se o que se quer compartilhar é tipo de valor sem estado e
            |       sem framework, ele pertence a :value-types (ADR-040).
            |    3. Se nenhuma das duas serve, o caso é emendar a ADR-001 — e
            |       essa é uma decisão de arquitetura, não um ajuste de build.
            """.trimMargin()
        )
    }
}
