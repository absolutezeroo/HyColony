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
    static final ArchRule coreNeverTouchesHytale =
            noClasses().should().dependOnClassesThat().resideInAPackage("com.hypixel..");

    @ArchTest
    static final ArchRule kernelDependsOnNothingElse = classes()
            .that()
            .resideInAPackage("dev.hycolony.core.kernel..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("dev.hycolony.core.kernel..", "java..", "com.google.gson..");

    /** `request` knows neither buildings nor construction: they plug in via Requester/ResolverProvider (spec § 2). */
    @ArchTest
    static final ArchRule requestDoesNotDependOnColonyBuildingConstructionJobOrCitizen = noClasses()
            .that()
            .resideInAPackage("dev.hycolony.core.request..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.hycolony.core.colony..",
                    "dev.hycolony.core.building..",
                    "dev.hycolony.core.construction..",
                    "dev.hycolony.core.job..",
                    "dev.hycolony.core.citizen..");

    @ArchTest
    static final ArchRule jobDoesNotDependOnConstruction = noClasses()
            .that()
            .resideInAPackage("dev.hycolony.core.job..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.hycolony.core.construction..");
}
