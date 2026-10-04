package dev.hyangler;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** HyAngler's core is pure Java (CLAUDE.md § 1). */
@AnalyzeClasses(packages = "dev.hyangler", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule coreNeverTouchesHytale =
            noClasses().should().dependOnClassesThat().resideInAPackage("com.hypixel..");
}
