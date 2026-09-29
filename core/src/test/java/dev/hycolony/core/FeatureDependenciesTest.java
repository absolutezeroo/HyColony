package dev.hycolony.core;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The dependencies allowed between the core's top-level packages. Today's matrix is frozen: every feature but kernel
 * and request still sits in one cycle through {@code colony}. Like the allowlists of CLAUDE.md § 8, it may only
 * shrink: remove an edge once the code no longer needs it, never add one.
 */
@AnalyzeClasses(packages = "dev.hycolony.core", importOptions = ImportOption.DoNotIncludeTests.class)
class FeatureDependenciesTest {

    @ArchTest
    static final ArchRule topLevelPackagesUseOnlyTheirKnownDependencies = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("root")
            .definedBy("dev.hycolony.core")
            .layer("building")
            .definedBy("dev.hycolony.core.building..")
            .layer("citizen")
            .definedBy("dev.hycolony.core.citizen..")
            .layer("colony")
            .definedBy("dev.hycolony.core.colony..")
            .layer("construction")
            .definedBy("dev.hycolony.core.construction..")
            .layer("crafting")
            .definedBy("dev.hycolony.core.crafting..")
            .layer("farming")
            .definedBy("dev.hycolony.core.farming..")
            .layer("job")
            .definedBy("dev.hycolony.core.job..")
            .layer("kernel")
            .definedBy("dev.hycolony.core.kernel..")
            .layer("logistics")
            .definedBy("dev.hycolony.core.logistics..")
            .layer("request")
            .definedBy("dev.hycolony.core.request..")
            .whereLayer("root")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("kernel")
            .mayNotAccessAnyLayer()
            .whereLayer("request")
            .mayOnlyAccessLayers("kernel")
            .whereLayer("building")
            .mayOnlyAccessLayers("colony", "kernel", "request")
            .whereLayer("citizen")
            .mayOnlyAccessLayers("building", "colony", "job", "kernel")
            .whereLayer("job")
            .mayOnlyAccessLayers("building", "citizen", "colony", "kernel", "logistics", "request")
            .whereLayer("logistics")
            .mayOnlyAccessLayers("building", "citizen", "colony", "job", "kernel", "request")
            .whereLayer("crafting")
            .mayOnlyAccessLayers("building", "citizen", "colony", "job", "kernel", "logistics", "request")
            .whereLayer("farming")
            .mayOnlyAccessLayers("building", "citizen", "colony", "crafting", "job", "kernel", "logistics", "request")
            .whereLayer("construction")
            .mayOnlyAccessLayers("building", "citizen", "colony", "crafting", "job", "kernel", "logistics", "request")
            .whereLayer("colony")
            .mayOnlyAccessLayers(
                    "building",
                    "citizen",
                    "construction",
                    "crafting",
                    "farming",
                    "job",
                    "kernel",
                    "logistics",
                    "request");
}
