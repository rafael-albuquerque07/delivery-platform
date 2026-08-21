// Build raiz: sem código próprio. Toda configuração compartilhada vive em
// build-logic/ como convention plugins, aplicados explicitamente por cada módulo.
// Nada de `subprojects {}` ou `allprojects {}` — eles quebram o configuration
// cache e escondem de onde vem cada configuração.

tasks.register("printModules") {
    val names = subprojects.map { it.path }.sorted()
    doLast { names.forEach(::println) }
}
