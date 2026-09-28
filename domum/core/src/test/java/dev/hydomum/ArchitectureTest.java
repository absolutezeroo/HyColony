package dev.hydomum;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.hydomum", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule coreNeverTouchesHytale =
            noClasses().should().dependOnClassesThat().resideInAPackage("com.hypixel..");

    /** The pure API that other mods' cores may use (split spec § API des mods) stays pure of the internals. */
    @ArchTest
    static final ArchRule apiDoesNotDependOnInternals = noClasses()
            .that()
            .resideInAPackage("dev.hydomum.api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.hydomum.core..");
}
