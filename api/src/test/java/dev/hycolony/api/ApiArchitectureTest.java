package dev.hycolony.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The api stands alone: an addon compiles against it and nothing else (spec 2026-09-30, § 4.3). */
@AnalyzeClasses(packages = "dev.hycolony.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiArchitectureTest {

    /** A whitelist, not a blacklist: Gson, HyColony's core or Hytale would all be refused. */
    @ArchTest
    static final ArchRule apiDependsOnTheJdkJspecifyAndItselfOnly = classes()
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "org.jspecify..", "dev.hycolony.api..");
}
