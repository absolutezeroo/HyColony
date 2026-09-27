package dev.hycolony.plugin.command;

import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.plugin.WorldRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Selftest steps of the warehouse and courier huts: their plans in every style, and the weather where you stand. */
final class LogisticsSelfTest {
    private LogisticsSelfTest() {}

    /** One report line per (hut type, style) with the racks of each level, then one for the rain at {@code at}. */
    static void run(SelfTestReport report, WorldRuntime rt, BlockPos at) {
        BlueprintSource blueprints = rt.manager().context().ports().blueprints();
        for (BuildingType type : List.of(WarehouseBuilding.TYPE, DeliverymanHut.TYPE)) {
            for (String style : blueprints.styles()) {
                List<String> racks = new ArrayList<>();
                boolean all = true;
                for (int level = 1; level <= type.maxLevel(); level++) {
                    Optional<Blueprint> bp = blueprints.load(style, type.id(), level, 0);
                    all &= bp.isPresent();
                    racks.add(bp.map(b -> Long.toString(b.entries().stream()
                                    .filter(BlueprintEntry::hasContainer)
                                    .count()))
                            .orElse("missing"));
                }
                // An OK line shows only the step, so the rack counts go in the step name.
                report.line(
                        "blueprint " + type.id() + " " + style + ", racks per level " + racks,
                        all,
                        "a level is missing");
            }
        }
        boolean raining = rt.manager().context().worldQuery().isRainingAt(at);
        report.line("weather (information only), raining or snowing here: " + raining, true, "");
    }

    /** Where a selftest step writes its result. */
    @FunctionalInterface
    interface SelfTestReport {
        void line(String step, boolean ok, String detail);
    }
}
