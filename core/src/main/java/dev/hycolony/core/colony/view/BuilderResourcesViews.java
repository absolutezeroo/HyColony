package dev.hycolony.core.colony.view;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ConstructionPorts;
import dev.hycolony.core.colony.ui.BuilderResourcesView;
import dev.hycolony.core.colony.ui.BuilderResourcesView.ResourceRow;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the builder hut's Resources tab (MC WindowBuilderResModule): each item its order still needs, with what the
 * builder and its hut hold and what the player could add, sorted by ResourceComparator.
 */
final class BuilderResourcesViews {
    /**
     * BuildingBuilderResource.ResourceComparator without an explicit order: status descending (HAVE_ENOUGH first,
     * NOT_NEEDED last), then by name.
     *
     * <p>Deviation from MC: by item id, not by translated name, which only the client knows.
     */
    static final Comparator<ResourceRow> RESOURCE_ORDER = Comparator.comparing(ResourceRow::status)
            .reversed()
            .thenComparing(r -> r.item().id());

    /** Stages a BUILD or REPAIR goes through (BuilderAI, StructureScan.nextStage). */
    private static final List<Stage> BUILD_STAGES =
            List.of(Stage.CLEAR, Stage.SOLID, Stage.DECORATE, Stage.CLEAR_LEFTOVERS);
    /** An UPGRADE starts at SOLID (WorkOrder.initialStage). */
    private static final List<Stage> UPGRADE_STAGES = BUILD_STAGES.subList(1, BUILD_STAGES.size());

    private final ColonyContext ctx;

    BuilderResourcesViews(ColonyContext ctx) {
        this.ctx = ctx;
    }

    BuilderResourcesView of(Colony c, Building hut, BuildingResourcesModule m, UUID player) {
        Optional<WorkOrder> order = c.work().claimedBy(hut.position());
        List<ResourceRow> rows = new ArrayList<>();
        // A free order needs nothing: its list is empty.
        if (order.isPresent() && m.orderId() == order.get().id() && !order.get().free()) {
            Optional<Inventory> inv = WorkOrderStatus.firstWorker(c, hut).map(CitizenData::inventory);
            List<BlockPos> containers = hut.containers();
            m.needs().remaining().forEach((item, needed) -> rows.add(row(player, item, needed, inv, containers)));
        }
        rows.sort(RESOURCE_ORDER);
        return new BuilderResourcesView(rows, order.map(o -> header(c, o, rows)));
    }

    private static BuilderResourcesView.Header header(Colony c, WorkOrder o, List<ResourceRow> rows) {
        List<Stage> stages = stages(o.type());
        int step = o.stage() == Stage.DONE ? stages.size() : Math.max(0, stages.indexOf(o.stage()));
        return new BuilderResourcesView.Header(
                o.type(),
                c.buildings().at(o.buildingPos()).map(Building::displayName).orElse(""),
                o.targetLevel(),
                step,
                stages.size(),
                suppliedPercent(rows),
                WorkOrderStatus.percent(c, o));
    }

    /**
     * The stages counted by "step X/Y".
     *
     * <p>Deviation from MC: its BuildingProgressStage lists (6 stages for a new building, 5 for an upgrade, 2 for a
     * removal) are replaced by ours, as our builder has fewer stages.
     */
    private static List<Stage> stages(WorkOrderType type) {
        return switch (type) {
            case BUILD, REPAIR -> BUILD_STAGES;
            case UPGRADE -> UPGRADE_STAGES;
            case REMOVE -> List.of(Stage.REMOVE);
        };
    }

    /** WindowBuilderResModule.pullResourcesFromHut: sum of min(available, needed) over the sum needed; 0 if none. */
    private static int suppliedPercent(List<ResourceRow> rows) {
        double supplied = 0;
        double total = 0;
        for (ResourceRow r : rows) {
            supplied += Math.min(r.available(), r.needed());
            total += r.needed();
        }
        return total > 0 ? (int) (supplied / total * 100) : 0;
    }

    private ResourceRow row(UUID player, ItemKey item, int needed, Optional<Inventory> inv, List<BlockPos> containers) {
        ConstructionPorts ports = ctx.ports();
        int available =
                inv.map(i -> i.count(item)).orElse(0) + ports.containers().count(containers, item);
        int has = ports.playerInventory().count(player, item);
        return new ResourceRow(item, needed, available, has, BuilderResourcesView.Status.of(needed, available, has));
    }
}
