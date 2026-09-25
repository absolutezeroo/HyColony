package dev.hycolony.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.hycolony.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule coreNeverTouchesHytale = noClasses()
            .should().dependOnClassesThat().resideInAPackage("com.hypixel..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule kernelDependsOnNothingElse = classes()
            .that().resideInAPackage("dev.hycolony.core.kernel..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "dev.hycolony.core.kernel..", "java..", "com.google.gson..");

    /** Futurs packages de fonctionnalités : ils ne communiquent que via le kernel (spec § 2.2). */
    @ArchTest
    static final ArchRule featurePackagesAreIsolated = noClasses()
            .that().resideInAnyPackage("dev.hycolony.core.request..", "dev.hycolony.core.construction..",
                    "dev.hycolony.core.job..", "dev.hycolony.core.life..", "dev.hycolony.core.defense..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.hycolony.core.request..",
                    "dev.hycolony.core.construction..", "dev.hycolony.core.job..", "dev.hycolony.core.life..",
                    "dev.hycolony.core.defense..")
            .allowEmptyShould(true);
}
