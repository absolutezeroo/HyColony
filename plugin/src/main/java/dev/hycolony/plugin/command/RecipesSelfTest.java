package dev.hycolony.plugin.command;

import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.crafting.JobTagAsset;

/** Selftest step: the game's recipes and benches reached the crafting core (SP3b-1). */
final class RecipesSelfTest {
    /** A Farmingbench recipe every 0.6.8 install has: wheat seeds from life essence. */
    private static final String WHEAT_SEEDS = "Plant_Seeds_Wheat";

    private static final String FARMING_BENCH = "Farmingbench";

    private RecipesSelfTest() {}

    /** The catalog holds recipes, wheat seeds at the Farmingbench, and the Farmingbench's first upgrade has a cost. */
    static void run(SelfTestReport report, WorldRuntime rt) {
        RecipeCatalog catalog = rt.manager().context().ports().crafting().catalog();
        int count = catalog.all().size();
        report.line("recipe catalog (" + count + " recipes)", count > 0, "no recipe: see the log at startup");
        boolean seeds = catalog.all().stream()
                .anyMatch(r -> r.primaryOutput().item().id().equals(WHEAT_SEEDS)
                        && r.bench().benchId().equals(FARMING_BENCH));
        report.line("recipe " + WHEAT_SEEDS + " at " + FARMING_BENCH, seeds, "missing");
        report.line(
                FARMING_BENCH + " tier 2 upgrade cost",
                !catalog.benchUpgradeCost(FARMING_BENCH, 1, 2).isEmpty(),
                "empty");
        report.line("job tag files (" + JobTagAsset.all().size() + ")", true, "");
    }
}
