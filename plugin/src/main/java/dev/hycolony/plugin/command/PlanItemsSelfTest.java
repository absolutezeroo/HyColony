package dev.hycolony.plugin.command;

import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.resources.EntryCost;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.item.HytaleItemSources;
import java.util.Map;
import java.util.TreeMap;

/**
 * Selftest step: every item the builder asks for a plan of any style, hut and level has a source in survival (a
 * recipe, a block's break or a drop list), so that no order waits for ever (docs/research/audit-monde-hytale.md A-15,
 * A-16). Loads every plan: slow, but only on demand.
 */
final class PlanItemsSelfTest {
    /** Items named in the KO detail; the log of a long list would drown the chat. */
    private static final int SHOWN = 8;

    private PlanItemsSelfTest() {}

    /**
     * One report line: KO names the first items without source, each with the first plan asking for it and the
     * block of that plan it is asked for.
     */
    static void run(SelfTestReport report, WorldRuntime rt) {
        GamePorts ports = rt.manager().context().ports();
        BlueprintSource blueprints = ports.blueprints();
        HytaleItemSources sources = new HytaleItemSources();
        Map<String, String> missing = new TreeMap<>();
        for (String style : blueprints.styles()) {
            for (BuildingType type : rt.manager().context().buildingTypes().all()) {
                for (int level = 1; level <= type.maxLevel(); level++) {
                    String plan = style + "/" + type.id() + level;
                    blueprints
                            .load(style, type.id(), level, 0)
                            .ifPresent(bp -> collect(bp, plan, ports, sources, missing));
                }
            }
        }
        String detail = missing.entrySet().stream()
                .limit(SHOWN)
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        report.line("plan items with a source (" + missing.size() + " without)", missing.isEmpty(), detail);
    }

    private static void collect(
            Blueprint bp, String plan, GamePorts ports, HytaleItemSources sources, Map<String, String> missing) {
        for (BlueprintEntry e : bp.entries()) {
            for (ItemAmount a :
                    EntryCost.of(e, ports.catalog(), ports.crafting().catalog())) {
                if (!sources.hasSource(a.item().id())) {
                    missing.putIfAbsent(
                            a.item().id(), plan + " <- " + e.state().key().id());
                }
            }
        }
    }
}
