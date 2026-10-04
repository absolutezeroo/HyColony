package dev.hycolony.plugin.command;

import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.crafting.JobTagAsset;
import java.util.Optional;

/** Selftest step: the game's recipes and benches reached the crafting core (SP3b-1). */
final class RecipesSelfTest {
    /** The id-map key of wheat seeds, which every install makes at a bench from life essence. */
    private static final String WHEAT_SEEDS = "selftest.wheat_seeds";

    private RecipesSelfTest() {}

    /** The catalog holds recipes, wheat seeds at a bench, and that bench's first upgrade has a cost. */
    static void run(SelfTestReport report, WorldRuntime rt, IdMap ids) {
        RecipeCatalog catalog = rt.manager().context().ports().crafting().catalog();
        int count = catalog.all().size();
        report.line("recipe catalog (" + count + " recipes)", count > 0, "no recipe: see the log at startup");
        String seeds = ids.itemId(WHEAT_SEEDS);
        Optional<String> bench = catalog.all().stream()
                .filter(r -> r.primaryOutput().item().id().equals(seeds))
                .map(Recipe::bench)
                .filter(b -> !b.isFieldcraft())
                .map(b -> b.benchId())
                .findFirst();
        report.line("recipe " + seeds + " at a bench (" + bench.orElse("none") + ")", bench.isPresent(), "missing");
        bench.ifPresent(b -> report.line(
                b + " tier 2 upgrade cost", !catalog.benchUpgradeCost(b, 1, 2).isEmpty(), "empty"));
        report.line("job tag files (" + JobTagAsset.all().size() + ")", true, "");
    }
}
