package com.deliveryplatform.valuetypes;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * A regra de entrada da ADR-040, na parte em que ela é verificável.
 *
 * <p>O módulo é compartilhado pelos nove serviços, e a ADR-001 só o permite
 * porque ele não pode carregar acoplamento junto. "Sem dependência de framework"
 * é a metade que uma máquina consegue conferir — as outras (sem estado, sem
 * regra de negócio de serviço nenhum, sem porta, sem agregado, sem evento) são
 * regra de revisão, e a ADR as nomeia como tal.
 *
 * <p>Sem este teste a regra seria mais uma frase, e este repositório já tem seis
 * peças que eram frases.
 */
@AnalyzeClasses(packages = "com.deliveryplatform.valuetypes")
class TiposDeValorNaoConhecemFrameworkTest {

    @ArchTest
    static final ArchRule semFramework = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "javax..",
                    "org.hibernate..",
                    "com.fasterxml.jackson..",
                    "org.testcontainers..")
            .because("o módulo é compartilhado pelos nove serviços (ADR-040) e a ADR-001 só o "
                    + "permite porque ele não arrasta framework junto: um tipo daqui que "
                    + "conhecesse Spring ou JPA acoplaria os nove de uma vez");
}
