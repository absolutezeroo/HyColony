package dev.hycolony.plugin.command;

import dev.hycolony.core.kernel.catalog.FoodCatalog;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.food.FoodValueAsset;
import java.util.List;
import java.util.Set;

/** Selftest step: HyColony's food files and Hytale's other foods reached the food catalog (spec 2026-10-04 § 5). */
final class FoodSelfTest {
    private FoodSelfTest() {}

    /** The food files the catalog took, those it skipped, and how many foods it values by quality. */
    static void run(SelfTestReport report, WorldRuntime rt) {
        FoodCatalog foods = rt.manager().context().ports().foods();
        Set<String> files = FoodValueAsset.all().keySet();
        List<String> skipped = files.stream()
                .filter(id -> foods.food(new ItemKey(id)).isEmpty())
                .sorted()
                .toList();
        report.line(
                "food files (" + (files.size() - skipped.size()) + " of " + files.size() + ")",
                !files.isEmpty() && skipped.isEmpty(),
                files.isEmpty() ? "no food file read" : "skipped (unknown item or out of bounds): " + skipped);
        long byQuality =
                foods.foods().stream().filter(f -> !files.contains(f.id())).count();
        report.line("foods by quality (" + byQuality + ")", true, "");
    }
}
