package dev.hycolony.core.colony.view;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.ProvidesTab;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.colony.ui.tab.ModuleTab;
import dev.hycolony.core.construction.workorder.WorkManager;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a hut's window view: its level, workers, the citizens it may hire, its work order, the orders it allows and
 * the tabs its modules provide.
 */
final class BuildingViews {
    private final ColonyContext ctx;

    BuildingViews(ColonyContext ctx) {
        this.ctx = ctx;
    }

    BuildingView of(Colony c, Building b, UUID viewer) {
        Optional<WorkerModule> w = b.module(WorkerModule.class);
        Optional<WorkOrder> order = c.work().byBuilding(b.position());
        boolean manage = c.permissions().hasPermission(viewer, Action.MANAGE_HUTS);
        return new BuildingView(
                c.id(),
                b.position(),
                b.type().id(),
                b.level(),
                b.type().maxLevel(),
                b.isBuilt(),
                b.isDeconstructed(),
                workers(c, w),
                hireable(c, w),
                w.map(WorkerModule::hiringMode),
                order.map(o -> new BuildingView.OrderRow(
                        o.id(),
                        o.type(),
                        o.targetLevel(),
                        WorkOrderStatus.builderName(c, o),
                        WorkOrderStatus.percent(c, o))),
                order.isEmpty() ? allowedOrders(b) : EnumSet.noneOf(WorkOrderType.class),
                ctx.ports().blueprints().styles(),
                b.style(),
                manage,
                manage && b.canBePickedUp(),
                // MC AbstractWindowWorkerModuleBuilding: only a hut with workers shows its pickup priority.
                w.isPresent() ? OptionalInt.of(b.pickupPriority().value()) : OptionalInt.empty(),
                tabs(c, b, viewer));
    }

    /** MC BuildingEntry: one tab per module that has a view, in module order. */
    private static List<ModuleTab> tabs(Colony c, Building b, UUID viewer) {
        return b.modules().values().stream()
                .filter(ProvidesTab.class::isInstance)
                .map(m -> ((ProvidesTab) m).tab(c, b, viewer))
                .toList();
    }

    private static List<BuildingView.WorkerRow> workers(Colony c, Optional<WorkerModule> w) {
        return w.map(m -> m.workers().stream()
                        .flatMap(id -> c.citizens().get(id).stream())
                        .map(BuildingViews::workerRow)
                        .toList())
                .orElse(List.of());
    }

    /** Adults without a job or workplace, if the hut employs anyone. */
    private static List<BuildingView.WorkerRow> hireable(Colony c, Optional<WorkerModule> w) {
        return w.isEmpty()
                ? List.of()
                : c.citizens().all().stream()
                        .filter(d -> !d.isChild() && d.job().isEmpty() && d.workBuilding() == null)
                        .map(BuildingViews::workerRow)
                        .toList();
    }

    private static Set<WorkOrderType> allowedOrders(Building b) {
        Set<WorkOrderType> allowed = EnumSet.noneOf(WorkOrderType.class);
        for (WorkOrderType type : WorkOrderType.values()) {
            if (WorkManager.isAllowed(b, type)) {
                allowed.add(type);
            }
        }
        return allowed;
    }

    private static BuildingView.WorkerRow workerRow(CitizenData d) {
        return new BuildingView.WorkerRow(d.id(), d.name());
    }
}
