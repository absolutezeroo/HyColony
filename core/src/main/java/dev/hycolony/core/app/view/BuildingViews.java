package dev.hycolony.core.app.view;

import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.building.module.ProvidesTab;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.construction.workorder.WorkManager;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.farming.hut.FarmerHut;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a hut's window view: its level, workers, the citizens it may hire, its work order, the orders it allows, its
 * stock and the tabs its modules provide.
 */
final class BuildingViews {
    private final ColonyContext ctx;

    BuildingViews(ColonyContext ctx) {
        this.ctx = ctx;
    }

    BuildingView of(Colony c, Building b, UUID viewer) {
        Optional<WorkerModule> w = b.module(WorkerModule.class);
        Optional<WorkOrder> order = c.work().byBuilding(b.position());
        boolean manage = ColonyAccess.allows(c, viewer, Action.MANAGE_HUTS);
        return new BuildingView(
                c.id(),
                b.position(),
                b.type().id(),
                b.level(),
                b.type().maxLevel(),
                b.isBuilt(),
                b.isDeconstructed(),
                workers(c, b, w),
                hireable(c, b, w),
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
                stock(b),
                tabs(c, b, viewer),
                residenceWarning(c, b));
    }

    /**
     * MC LivingBuildingView.getHoverWarningForLevel: before level 2, a farm (or fisher, not ported) of level 1 or
     * more; before levels 3 to 5, a restaurant serving the right meals, which HyColony lacks, so always.
     */
    private static Optional<String> residenceWarning(Colony c, Building b) {
        if (b.module(LivingModule.class).isEmpty()) {
            return Optional.empty();
        }
        int next = b.level() + 1;
        boolean warn = switch (b.level()) {
            case 1 ->
                c.buildings().all().stream().noneMatch(o -> o.type().id().equals(FarmerHut.TYPE_ID) && o.level() >= 1);
            case 2, 3, 4 -> true;
            default -> false;
        };
        return warn ? Optional.of("hycolony.ui.residence.warning." + next) : Optional.empty();
    }

    /**
     * MC WindowHutAllInventory with its "count, descending" sort: every item of the hut block and racks, most held
     * first, ties by id.
     *
     * <p>Deviation from MC: no sort button nor search field; the list always uses this order.
     */
    private List<ItemAmount> stock(Building b) {
        return ctx.ports().containers().contents(b.containers()).entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> new ItemAmount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(ItemAmount::count)
                        .reversed()
                        .thenComparing(a -> a.item().id()))
                .toList();
    }

    /** MC BuildingEntry: one tab per module that has a view, in module order. */
    private static List<ModuleTab> tabs(Colony c, Building b, UUID viewer) {
        return b.modules().values().stream()
                .filter(ProvidesTab.class::isInstance)
                .map(m -> ((ProvidesTab) m).tab(c, b, viewer))
                .toList();
    }

    private static List<BuildingView.WorkerRow> workers(Colony c, Building b, Optional<WorkerModule> w) {
        return w.map(m -> m.workers().stream()
                        .flatMap(id -> c.citizens().get(id).stream())
                        .map(d -> workerRow(d, b.position()))
                        .toList())
                .orElse(List.of());
    }

    /**
     * Adults without a job or workplace, if the hut employs anyone, sorted as MC WindowHireWorker.updateCitizens: by
     * the distance from their home to the hut rounded to 40 blocks (the homeless count as 100), then by name.
     */
    private static List<BuildingView.WorkerRow> hireable(Colony c, Building b, Optional<WorkerModule> w) {
        return w.isEmpty()
                ? List.of()
                : c.citizens().all().stream()
                        .filter(d -> !d.isChild() && d.job().isEmpty() && d.workBuilding() == null)
                        .sorted(Comparator.comparingDouble((CitizenData d) -> homeBucket(d, b.position()))
                                .thenComparing(CitizenData::name))
                        .map(d -> workerRow(d, b.position()))
                        .toList();
    }

    /** MC WindowHireWorker: the home's distance to {@code hut} rounded to the nearest 40 blocks; 100 when homeless. */
    private static double homeBucket(CitizenData d, BlockPos hut) {
        BlockPos home = d.homeBuilding();
        if (home == null) {
            return 100.0;
        }
        double distance = Math.sqrt((double) home.distSq(hut));
        double rest = distance % 40;
        return rest > 20 ? distance - rest + 40 : distance - rest;
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

    /** MC WindowHireWorker's distance label: homeless, lives here, lives at its workplace, or N blocks away. */
    private static BuildingView.WorkerRow workerRow(CitizenData d, BlockPos hut) {
        BlockPos home = d.homeBuilding();
        if (home == null) {
            return new BuildingView.WorkerRow(d.id(), d.name(), BuildingView.HomeLine.HOMELESS, 0);
        }
        if (home.equals(hut)) {
            return new BuildingView.WorkerRow(d.id(), d.name(), BuildingView.HomeLine.LIVES_HERE, 0);
        }
        if (home.equals(d.workBuilding())) {
            return new BuildingView.WorkerRow(d.id(), d.name(), BuildingView.HomeLine.LIVES_AT_WORK, 0);
        }
        int distance = (int) Math.sqrt((double) home.distSq(hut));
        return new BuildingView.WorkerRow(d.id(), d.name(), BuildingView.HomeLine.DISTANCE, distance);
    }
}
