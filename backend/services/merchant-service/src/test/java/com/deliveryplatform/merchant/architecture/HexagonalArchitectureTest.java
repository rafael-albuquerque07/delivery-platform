package com.deliveryplatform.merchant.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * A arquitetura hexagonal só sobrevive se for verificada. Em revisão manual,
 * ela erode em duas semanas.
 *
 * Regra de dependência:  api ─┐
 *                             v
 *          infrastructure ──> application ──> domain
 */
@AnalyzeClasses(packages = "com.deliveryplatform.merchant")
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule camadas = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("api").definedBy("..api..")
            .layer("application").definedBy("..application..")
            .layer("domain").definedBy("..domain..")
            .layer("infrastructure").definedBy("..infrastructure..")

            .whereLayer("api").mayNotBeAccessedByAnyLayer()
            .whereLayer("infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("application").mayOnlyBeAccessedByLayers("api", "infrastructure")
            .whereLayer("domain").mayOnlyBeAccessedByLayers("api", "application", "infrastructure")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule dominioNaoConheceSpring = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "com.fasterxml.jackson..")
            .because("o domínio não deve importar framework, ORM nem serialização")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule entidadesJpaForaDoDominio = noClasses()
            .that().resideInAPackage("..domain..")
            .should().beAnnotatedWith("jakarta.persistence.Entity")
            .because("entidades JPA vivem em infrastructure/persistence/entity")
            .allowEmptyShould(true);
}
