package dev.hyangler.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The api stands alone: another mod compiles against it and nothing else (spec 2026-10-04-hyangler § 3). */
@AnalyzeClasses(packages = "dev.hyangler.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiArchitectureTest {

    /** A whitelist, not a blacklist: Gson, HyAngler's core or Hytale would all be refused. */
    @ArchTest
    static final ArchRule apiDependsOnTheJdkJspecifyAndItselfOnly = classes()
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "org.jspecify..", "dev.hyangler.api..");
}
