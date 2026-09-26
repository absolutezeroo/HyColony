package dev.hycolony.core.colony.view;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ConstructionPorts;
import dev.hycolony.core.colony.ui.BuilderResourcesView;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the builder hut's resources tab: each item its order still needs, with what the builder and its hut hold
 * and what the player could add.
 */
final class BuilderResourcesViews {
    private final ColonyContext ctx;

    BuilderResourcesViews(ColonyContext ctx) {
        this.ctx = ctx;
    }

    BuilderResourcesView of(Colony c, Building hut, BuildingResourcesModule m, UUID player) {
        Optional<WorkOrder> order = c.work().claimedBy(hut.position());
        List<BuilderResourcesView.ResourceRow> rows = new ArrayList<>();
        // A free order needs nothing: its list is empty.
        if (order.isPresent() && m.orderId() == order.get().id() && !order.get().free()) {
            Optional<Inventory> inv = WorkOrderStatus.firstWorker(c, hut).map(CitizenData::inventory);
            List<BlockPos> containers = hut.containers();
            m.needs().remaining().forEach((item, needed) -> rows.add(row(player, item, needed, inv, containers)));
        }
        return new BuilderResourcesView(
                c.id(),
                hut.position(),
                rows,
                order.map(o -> WorkOrderStatus.percent(c, o)).orElse(0),
                order.map(o -> o.stage().name().toLowerCase(Locale.ROOT)).orElse(""));
    }

    private BuilderResourcesView.ResourceRow row(
            UUID player, ItemKey item, int needed, Optional<Inventory> inv, List<BlockPos> containers) {
        ConstructionPorts ports = ctx.ports();
        int available =
                inv.map(i -> i.count(item)).orElse(0) + ports.containers().count(containers, item);
        int has = ports.playerInventory().count(player, item);
        return new BuilderResourcesView.ResourceRow(
                item, needed, available, has, BuilderResourcesView.Status.of(needed, available, has));
    }
}
