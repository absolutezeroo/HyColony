package dev.hycolony.core;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.conditions.ArchConditions.have;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import dev.hycolony.core.construction.builder.BuilderJob;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import java.util.stream.Stream;

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
            .resideInAnyPackage(
                    "dev.hycolony.core.kernel..", "java..", "com.google.gson..", "org.jspecify.annotations..");

    /**
     * `request` knows neither buildings nor construction: they plug in via Requester/ResolverProvider (spec § 2). A
     * crafting task names its recipe by id for the same reason (SP3b-1 spec).
     */
    @ArchTest
    static final ArchRule requestDoesNotDependOnColonyBuildingConstructionJobCitizenOrCrafting = noClasses()
            .that()
            .resideInAPackage("dev.hycolony.core.request..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.hycolony.core.colony..",
                    "dev.hycolony.core.building..",
                    "dev.hycolony.core.construction..",
                    "dev.hycolony.core.job..",
                    "dev.hycolony.core.citizen..",
                    "dev.hycolony.core.crafting..");

    @ArchTest
    static final ArchRule jobDoesNotDependOnConstruction = noClasses()
            .that()
            .resideInAPackage("dev.hycolony.core.job..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.hycolony.core.construction..");

    /**
     * Buildings host the other features through modules: construction, jobs, logistics and crafting depend on
     * buildings, never the reverse (spec § 6). Construction hut types are registered by the composition root, like its
     * jobs.
     */
    @ArchTest
    static final ArchRule buildingDependsOnNeitherConstructionJobLogisticsNorCrafting = noClasses()
            .that()
            .resideInAPackage("dev.hycolony.core.building..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.hycolony.core.construction..",
                    "dev.hycolony.core.job..",
                    "dev.hycolony.core.logistics..",
                    "dev.hycolony.core.crafting..");

    /**
     * Crafting sits beside construction (SP3b-1 spec); like every feature, it stays below the player actions, windows
     * and saves of {@code app} ({@link FeatureDependenciesTest}).
     */
    @ArchTest
    static final ArchRule craftingDoesNotDependOnConstruction = noClasses()
            .that()
            .resideInAPackage("dev.hycolony.core.crafting..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.hycolony.core.construction..");

    /**
     * The crafting sub-packages form no dependency cycle: recipes and the crafters' task state at the bottom, then the
     * hut's module, the crafter's AI, and the resolvers on top (SP3b-1 spec).
     */
    @ArchTest
    static final ArchRule craftingSubPackagesAreFreeOfCycles =
            slices().matching("dev.hycolony.core.crafting.(*)..").should().beFreeOfCycles();

    /** Like the construction root: an empty crafting root keeps the slice rule above complete. */
    @ArchTest
    static final ArchRule craftingRootPackageIsEmpty =
            noClasses().should().resideInAPackage("dev.hycolony.core.crafting");

    /** A crafter's task state is read by the module, the AI and the resolvers: it depends on none of them. */
    @ArchTest
    static final ArchRule craftingTaskDependsOnNoOtherCraftingPackage = noClasses()
            .that()
            .resideInAPackage("dev.hycolony.core.crafting.task..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.hycolony.core.crafting.recipe..",
                    "dev.hycolony.core.crafting.module..",
                    "dev.hycolony.core.crafting.job..",
                    "dev.hycolony.core.crafting.request..");

    /** The construction sub-packages form no dependency cycle. */
    @ArchTest
    static final ArchRule constructionSubPackagesAreFreeOfCycles =
            slices().matching("dev.hycolony.core.construction.(*)..").should().beFreeOfCycles();

    /** The slices above ignore the root package, so a class there could hide a cycle: every class lives in one. */
    @ArchTest
    static final ArchRule constructionRootPackageIsEmpty =
            noClasses().should().resideInAPackage("dev.hycolony.core.construction");

    /**
     * The builder's AI stays its own: other jobs share {@code job.work} instead, and only the job itself is registered
     * from outside (by the construction hut types).
     */
    @ArchTest
    static final ArchRule onlyTheBuilderJobIsReachableFromOutsideItsPackage = noClasses()
            .that()
            .resideOutsideOfPackage("dev.hycolony.core.construction.builder..")
            .should()
            .dependOnClassesThat(resideInAPackage("dev.hycolony.core.construction.builder..")
                    .and(not(equivalentTo(BuilderJob.class))));

    // No "job does not depend on logistics" rule: WorkerStock.dump asks for a pickup right after a dump, like MC
    // AbstractEntityAIBasic.dumpInventory calls building.createPickupRequest, and logistics is a core feature that
    // cannot be switched off.

    /** The colony root knows no logistics type: couriers and warehouses register from their own feature. */
    @ArchTest
    static final ArchRule colonyRootDoesNotDependOnLogistics = noClasses()
            .that()
            .resideInAPackage("dev.hycolony.core.colony")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.hycolony.core.logistics..");

    /** The logistics sub-packages (courier, pickup, warehouse) form no dependency cycle. */
    @ArchTest
    static final ArchRule logisticsSubPackagesAreFreeOfCycles =
            slices().matching("dev.hycolony.core.logistics.(*)..").should().beFreeOfCycles();

    /** Like the construction root: an empty logistics root keeps the slice rule above complete. */
    @ArchTest
    static final ArchRule logisticsRootPackageIsEmpty =
            noClasses().should().resideInAPackage("dev.hycolony.core.logistics");

    /** Jobs and their AIs compose shared parts instead of stacking a hierarchy: depth 1 below Job and JobAI. */
    @ArchTest
    static final ArchRule noSubclassOfAJobOrJobAISubtype = noClasses()
            .should(have(DescribedPredicate.describe(
                    "a parent that is a strict subtype of Job or JobAI", ArchitectureTest::extendsAJobOrJobAISubtype)));

    /** True if a direct superclass or interface of {@code type} is itself below Job or JobAI. */
    private static boolean extendsAJobOrJobAISubtype(JavaClass type) {
        return Stream.concat(type.getRawSuperclass().stream(), type.getRawInterfaces().stream())
                .anyMatch(parent -> isStrictSubtype(parent, Job.class) || isStrictSubtype(parent, JobAI.class));
    }

    /** True if {@code type} is assignable to {@code root} without being {@code root} itself. */
    private static boolean isStrictSubtype(JavaClass type, Class<?> root) {
        return type.isAssignableTo(root) && !type.isEquivalentTo(root);
    }
}
