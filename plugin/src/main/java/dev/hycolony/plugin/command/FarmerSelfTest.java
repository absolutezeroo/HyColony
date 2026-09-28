package dev.hycolony.plugin.command;

import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.crafting.recipe.CraftingRules.CustomRecipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.farming.hut.FarmerHut;
import dev.hycolony.core.kernel.item.Workstation;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import java.util.List;

/** Selftest step: the farmer can work (SP3b-2): seeds, the field block, a Farmingbench in its plan, its recipes. */
final class FarmerSelfTest {
    private static final Workstation LEVEL_1_BENCH = new Workstation("Farmingbench", 1);

    private FarmerSelfTest() {}

    /** One line per check; the hut item and block are checked by {@link HutTypesSelfTest}. */
    static void run(SelfTestReport report, WorldRuntime rt, IdMap ids) {
        int seeds = rt.manager().context().ports().farming().seeds().size();
        report.line("farming seeds (" + seeds + ")", seeds > 0, "id-map farming.seeds is empty");
        boolean field = HutTypesSelfTest.mapped(() -> ids.itemId("block.field"))
                && HutTypesSelfTest.mapped(() -> ids.blockId("block.field"));
        report.line("id-map block.field", field, "missing");
        BlueprintSource blueprints = rt.manager().context().ports().blueprints();
        boolean bench = blueprints.styles().stream()
                .flatMap(style -> blueprints.load(style, FarmerHut.TYPE_ID, 1, 0).stream())
                .anyMatch(plan -> plan.entries().stream()
                        .anyMatch(e ->
                                e.workstation().filter(LEVEL_1_BENCH::equals).isPresent()));
        report.line("farmer level 1 plan has a tier 1 Farmingbench", bench, "none in any style");
        RecipeCatalog catalog = rt.manager().context().ports().crafting().catalog();
        List<CustomRecipe> custom =
                rt.manager().context().ports().crafting().rules().custom(FarmerHut.TYPE_ID);
        List<String> missing = custom.stream()
                .map(CustomRecipe::hytaleRecipe)
                .filter(id -> catalog.byHytaleId(id).isEmpty())
                .toList();
        report.line(
                "farmer built-in recipes (" + custom.size() + ")",
                !custom.isEmpty() && missing.isEmpty(),
                custom.isEmpty() ? "none in crafting.json" : "unknown: " + String.join(", ", missing));
    }
}
