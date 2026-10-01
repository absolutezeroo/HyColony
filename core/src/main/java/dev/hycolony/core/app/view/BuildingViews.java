package dev.hycolony.core.app.view;

import dev.hycolony.core.app.hut.HireViews;
import dev.hycolony.core.app.hut.HutStock;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.building.module.ProvidesTab;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.construction.workorder.WorkManager;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.crafting.restaurant.RestaurantMenuModule;
import dev.hycolony.core.farming.hut.FarmerHut;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a hut's window view: its level, workers, its hire window, its work order, the orders it allows, its stock and
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
        boolean manage = ColonyAccess.allows(c, viewer, Action.MANAGE_HUTS);
        return new BuildingView(
                c.id(),
                b.position(),
                b.type().id(),
                b.level(),
                b.type().maxLevel(),
                b.isBuilt(),
                b.isDeconstructed(),
                b.customName(),
                mainKind(b, w),
                workers(c, w),
                HireViews.of(c, b),
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
                // MC AbstractWindowWorkerModuleBuilding: only a hut with workers shows its pickup priority.
                w.isPresent() ? OptionalInt.of(b.pickupPriority().value()) : OptionalInt.empty(),
                stock(b),
                tabs(c, b, viewer),
                residenceWarning(c, b));
    }

    /**
     * MC LivingBuildingView.getHoverWarningForLevel: before level 2, a farm (or fisher, not ported) of level 1 or
     * more; before level 3, a dining hall menu with a dish of tier 1 or more (MC checkColonyMenu); before levels 4 and
     * 5, one of tier 2 or more.
     */
    private static Optional<String> residenceWarning(Colony c, Building b) {
        if (b.module(LivingModule.class).isEmpty()) {
            return Optional.empty();
        }
        int next = b.level() + 1;
        boolean warn = switch (b.level()) {
            case 1 ->
                c.buildings().all().stream().noneMatch(o -> o.type().id().equals(FarmerHut.TYPE_ID) && o.level() >= 1);
            case 2 -> !RestaurantMenuModule.anyMenuServesTier(c, 1);
            case 3, 4 -> !RestaurantMenuModule.anyMenuServesTier(c, 2);
            default -> false;
        };
        return warn ? Optional.of("hycolony.ui.residence.warning." + next) : Optional.empty();
    }

    /**
     * MC WindowHutAllInventory.updateResources: every item of the hut block and racks with each container holding it,
     * most held first; the window sorts and filters them itself, as MC's.
     */
    private List<HutStock> stock(Building b) {
        Map<ItemKey, List<HutStock.Holder>> holders = new LinkedHashMap<>();
        for (BlockPos pos : b.containers()) {
            ctx.ports().containers().contents(List.of(pos)).forEach((item, count) -> {
                if (count > 0) {
                    holders.computeIfAbsent(item, k -> new ArrayList<>()).add(new HutStock.Holder(pos, count));
                }
            });
        }
        List<HutStock> stock = new ArrayList<>();
        holders.forEach((item, list) -> {
            list.sort(Comparator.comparingInt(HutStock.Holder::count).reversed());
            stock.add(new HutStock(
                    item, list.stream().mapToInt(HutStock.Holder::count).sum(), list));
        });
        return stock;
    }

    /** MC BuildingEntry: one tab per module that has a view, in module order. */
    private static List<ModuleTab> tabs(Colony c, Building b, UUID viewer) {
        return b.modules().values().stream()
                .filter(ProvidesTab.class::isInstance)
                .map(m -> ((ProvidesTab) m).tab(c, b, viewer))
                .toList();
    }

    /** MC AbstractBuildingView.getWindow: workers first, then a residence, else the minimal page. */
    private static BuildingView.MainKind mainKind(Building b, Optional<WorkerModule> w) {
        if (w.isPresent()) {
            return BuildingView.MainKind.WORKERS;
        }
        return b.module(LivingModule.class).isPresent() ? BuildingView.MainKind.LIVING : BuildingView.MainKind.SIMPLE;
    }

    private static List<BuildingView.WorkerLine> workers(Colony c, Optional<WorkerModule> w) {
        return w.map(m -> m.workers().stream()
                        .flatMap(id -> c.citizens().get(id).stream())
                        .map(d -> new BuildingView.WorkerLine(
                                d.id(), d.name(), m.job().id()))
                        .toList())
                .orElse(List.of());
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
}
