package dev.hycolony.plugin.command;

import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import java.util.ArrayList;
import java.util.List;

/** Selftest step: every registered building type can be placed (hut item and block in the id-map) and built. */
final class HutTypesSelfTest {
    private HutTypesSelfTest() {}

    /** One report line per registered type: KO lists a missing id-map key or the lack of a level 1 plan. */
    static void run(SelfTestReport report, WorldRuntime rt, IdMap ids) {
        BlueprintSource blueprints = rt.manager().context().ports().blueprints();
        for (BuildingType type : rt.manager().context().buildingTypes().all()) {
            List<String> missing = new ArrayList<>();
            if (!mapped(() -> ids.itemId(type.hutBlockKey()))) {
                missing.add("id-map item " + type.hutBlockKey());
            }
            if (!mapped(() -> ids.blockId(type.hutBlockKey()))) {
                missing.add("id-map block " + type.hutBlockKey());
            }
            boolean planned = blueprints.styles().stream()
                    .anyMatch(style -> blueprints.load(style, type.id(), 1, 0).isPresent());
            if (!planned) {
                missing.add("no level 1 plan in any style");
            }
            report.line("hut type " + type.id(), missing.isEmpty(), String.join(", ", missing));
        }
    }

    /** IdMap throws on an unknown key; a selftest reports it instead. */
    private static boolean mapped(Runnable lookup) {
        try {
            lookup.run();
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
