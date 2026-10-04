package dev.hycolony.plugin.command;

import dev.hycolony.core.kernel.catalog.FoodCatalog;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.food.FoodValueAsset;
import dev.hycolony.plugin.food.HytaleFoods;
import java.util.List;
import java.util.Set;

/** Selftest step: HyColony's food files and Hytale's other foods reached the food catalog (spec 2026-10-04 § 5). */
final class FoodSelfTest {
    private FoodSelfTest() {}

    /** The food files the catalog took, those it skipped, and how many foods it values by quality. */
    static void run(SelfTestReport report, WorldRuntime rt) {
        FoodCatalog foods = rt.manager().context().ports().foods();
        Set<String> taken = foods instanceof HytaleFoods hytale ? hytale.fromFiles() : Set.of();
        Set<String> files = FoodValueAsset.all().keySet();
        List<String> skipped =
                files.stream().filter(id -> !taken.contains(id)).sorted().toList();
        report.line(
                "food files (" + taken.size() + " of " + files.size() + ")",
                !files.isEmpty() && skipped.isEmpty(),
                files.isEmpty()
                        ? "no food file read"
                        : "skipped (unknown item, out of bounds or excluded): " + skipped);
        long byQuality =
                foods.foods().stream().filter(f -> !taken.contains(f.id())).count();
        report.line("foods by quality (" + byQuality + ")", true, "");
    }
}
