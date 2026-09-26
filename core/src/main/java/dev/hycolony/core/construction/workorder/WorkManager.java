package dev.hycolony.core.construction.workorder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Action;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.construction.ConstructionBuildingTypes;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.port.Msg;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A colony's work orders. Port of MineColonies' WorkManager, with the creation checks of
 * AbstractBuilding.requestWorkOrder and the claim rule of WorkOrderBuilding.canBuild.
 */
public final class WorkManager {
    public static final int TICK_INTERVAL = 20;
    /** WorkOrderBuilding.MAX_DISTANCE_SQ: 100 blocks, 3D. */
    public static final long MAX_DISTANCE_SQ = 100L * 100L;
    /** IWorkOrder.WORK_ORDER_COMPARATOR. */
    static final Comparator<WorkOrder> ORDER =
            Comparator.comparingInt(WorkOrder::priority).reversed().thenComparingInt(WorkOrder::id);

    private final Colony colony;
    private final WorkOrderValidation validation;
    private final WorkOrderAssignment assignment;
    private final Map<Integer, WorkOrder> orders = new LinkedHashMap<>();
    private final Map<BlockPos, WorkOrder> byBuilding = new HashMap<>();
    private int topId;

    public WorkManager(Colony colony) {
        this.colony = colony;
        this.validation = new WorkOrderValidation(colony);
        this.assignment = new WorkOrderAssignment(colony);
    }

    /**
     * Refusals in order: permission, duplicate, then those of {@link WorkOrderValidation} (max level, repair of an
     * unbuilt building, a type that does not fit the building ({@link #isAllowed}), no builder of the level, no builder
     * within 100 blocks (unless one is chosen), no blueprint, footprint outside the colony). {@code buildingPos} must
     * hold a building of this colony.
     */
    public Either<WorkOrder, WorkOrderRefusal> request(
            UUID player, BlockPos buildingPos, WorkOrderType type, String style, Optional<BlockPos> builder) {
        if (!colony.permissions().hasPermission(player, Action.MANAGE_HUTS)) {
            return new Either.Right<>(WorkOrderRefusal.NO_PERMISSION);
        }
        Building b = colony.buildings()
                .at(buildingPos)
                .orElseThrow(() -> new IllegalArgumentException("No building at " + buildingPos));
        if (byBuilding.containsKey(buildingPos)) {
            return new Either.Right<>(WorkOrderRefusal.ALREADY_EXISTS);
        }
        return switch (validation.check(b, type, style, builder)) {
            case Either.Right<WorkOrderValidation.Accepted, WorkOrderRefusal> refused ->
                new Either.Right<>(refused.value());
            case Either.Left<WorkOrderValidation.Accepted, WorkOrderRefusal> accepted ->
                new Either.Left<>(create(player, b, type, builder, accepted.value()));
        };
    }

    private WorkOrder create(
            UUID player,
            Building b,
            WorkOrderType type,
            Optional<BlockPos> builder,
            WorkOrderValidation.Accepted accepted) {
        WorkOrder order = new WorkOrder(++topId, type, b.position(), accepted.targetLevel(), accepted.layout());
        builder.ifPresent(order::setClaimedBy);
        order.setFree(isFree(player, type));
        add(order);
        if (type == WorkOrderType.BUILD) {
            b.setStyle(order.style()); // the building keeps the style it is built in
        }
        colony.markDirty();
        announceCreated(b, order);
        return order;
    }

    /** Tells the members allowed to receive messages, then posts {@link ColonyEvents.WorkOrderCreated}. */
    private void announceCreated(Building b, WorkOrder order) {
        BlockPos buildingPos = order.buildingPos();
        Msg created = Msg.of(
                "hycolony.workorder.created",
                b.displayName(),
                colony.name(),
                String.valueOf(buildingPos.x()),
                String.valueOf(buildingPos.y()),
                String.valueOf(buildingPos.z()));
        for (UUID member : colony.permissions().members().keySet()) {
            if (colony.permissions().hasPermission(member, Action.RECEIVE_MESSAGES)) {
                colony.context().notifier().send(member, created);
            }
        }
        colony.context().bus().post(new ColonyEvents.WorkOrderCreated(colony, order));
    }

    /** MC builderInfiniteResources, or a creative operator's order (if enabled); a REMOVE never is. */
    private boolean isFree(UUID player, WorkOrderType type) {
        var config = colony.context().config();
        return type != WorkOrderType.REMOVE
                && (config.builderInfiniteResources()
                        || (config.creativeOperatorFreeBuilds()
                                && colony.context().players().isCreativeOperator(player)));
    }

    /**
     * Removes the order; if it was its builder's active order ({@link #claimedBy}), that builder's requests are
     * cancelled too (they were made for it). Placed blocks stay.
     */
    public void cancel(int orderId) {
        WorkOrder order = orders.get(orderId);
        if (order == null) {
            return;
        }
        Optional<BlockPos> claimer = order.claimedBy();
        boolean active = claimer.flatMap(this::claimedBy).map(order::equals).orElse(false);
        orders.remove(orderId);
        byBuilding.remove(order.buildingPos());
        if (active) {
            colony.buildings()
                    .at(claimer.get())
                    .ifPresent(hut -> colony.requests().cancelAllFrom(hut.requesterId()));
        }
        order.release();
        colony.markDirty();
    }

    /**
     * WindowInfoPage.updatePriority: {@code delta > 0} moves the order up (priority = previous one's + 1),
     * {@code delta < 0} moves it down (priority = next one's - 1). No-op at either end.
     */
    public void move(int orderId, int delta) {
        List<WorkOrder> list = ordered();
        int i = 0;
        while (i < list.size() && list.get(i).id() != orderId) {
            i++;
        }
        if (i == list.size()) {
            return;
        }
        WorkOrder o = list.get(i);
        if (delta > 0 && i > 0) {
            o.setPriority(list.get(i - 1).priority() + 1);
            colony.markDirty();
        } else if (delta < 0 && i < list.size() - 1) {
            o.setPriority(list.get(i + 1).priority() - 1);
            colony.markDirty();
        }
    }

    /** Priority descending, then id ascending. */
    public List<WorkOrder> ordered() {
        List<WorkOrder> list = new ArrayList<>(orders.values());
        list.sort(ORDER);
        return list;
    }

    public Optional<WorkOrder> byId(int id) {
        return Optional.ofNullable(orders.get(id));
    }

    /** This very order is still registered (allocation-free, for the builder's per-step check). */
    public boolean holds(WorkOrder o) {
        return o.equals(orders.get(o.id()));
    }

    public Optional<WorkOrder> byBuilding(BlockPos buildingPos) {
        return Optional.ofNullable(byBuilding.get(buildingPos));
    }

    /**
     * The builder's active order: a builder can hold several claims (chosen at creation); it works on the one with
     * the lowest id.
     */
    public Optional<WorkOrder> claimedBy(BlockPos builderHut) {
        // ponytail: linear scan of a colony's few orders; index claims if colonies hold hundreds.
        return orders.values().stream()
                .filter(o -> builderHut.equals(o.claimedBy().orElse(null)))
                .min(Comparator.comparingInt(WorkOrder::id));
    }

    /** WorkManager.onColonyTick: drops the orders whose building is gone, then {@link WorkOrderAssignment}. */
    public void tick() {
        // WorkOrderBuilding.isValid: an order whose building is gone is dropped.
        List<Integer> invalid = new ArrayList<>();
        for (WorkOrder o : orders.values()) {
            if (colony.buildings().at(o.buildingPos()).isEmpty()) {
                invalid.add(o.id());
            }
        }
        invalid.forEach(this::cancel);
        assignment.assign(orders.values());
    }

    /** The order is done: removed without touching requests. */
    public void complete(WorkOrder o) {
        if (orders.remove(o.id()) != null) {
            byBuilding.remove(o.buildingPos());
            colony.markDirty();
        }
    }

    /**
     * A building left the colony: its own order is cancelled (MineColonies drops invalid orders), and the orders
     * its builder held are released. The colony already cancelled the building's requests.
     */
    public void onBuildingRemoved(BlockPos pos) {
        WorkOrder own = byBuilding.get(pos);
        if (own != null) {
            cancel(own.id());
        }
        for (WorkOrder o : orders.values()) {
            if (pos.equals(o.claimedBy().orElse(null))) {
                o.release();
                colony.markDirty();
            }
        }
    }

    /**
     * Whether {@code type} fits the building; the hut window shows exactly these buttons. BUILD: level 0, not
     * deconstructed; UPGRADE: 1 <= level < max; REPAIR: level 1+ or deconstructed; REMOVE: level 1+, not deconstructed
     * (a deconstructed hut is picked up instead, as in MC).
     */
    public static boolean isAllowed(Building b, WorkOrderType type) {
        int level = b.level();
        return switch (type) {
            case BUILD -> level == 0 && !b.isDeconstructed();
            case UPGRADE -> level >= 1 && level < b.type().maxLevel();
            case REPAIR -> level > 0 || b.isDeconstructed();
            case REMOVE -> level > 0 && !b.isDeconstructed();
        };
    }

    /** WorkOrderBuilding.canBuild: level high enough, or level 5, or its own hut; and within 100 blocks. */
    public static boolean canBuild(Building builderHut, WorkOrder o, int builderLevel) {
        boolean allowed = builderLevel >= o.targetLevel()
                || builderLevel == ConstructionBuildingTypes.BUILDER.maxLevel()
                || builderHut.position().equals(o.buildingPos());
        return allowed && builderHut.position().distSq(o.buildingPos()) <= MAX_DISTANCE_SQ;
    }

    /** The highest order id ever given: saved, so ids are never reused after a restart. */
    public int topId() {
        return topId;
    }

    public void restoreTopId(int id) {
        topId = Math.max(topId, id);
    }

    /** Every order, in creation order (for {@link WorkOrderSerializer}). */
    Collection<WorkOrder> all() {
        return Collections.unmodifiableCollection(orders.values());
    }

    /** Replaces every order with {@code loaded} (for {@link WorkOrderSerializer}). */
    void restore(List<WorkOrder> loaded) {
        orders.clear();
        byBuilding.clear();
        for (WorkOrder o : loaded) {
            add(o);
            topId = Math.max(topId, o.id());
        }
    }

    private void add(WorkOrder o) {
        orders.put(o.id(), o);
        byBuilding.put(o.buildingPos(), o);
    }

    static boolean isEmployedBuilder(Building b) {
        return b.type().equals(ConstructionBuildingTypes.BUILDER)
                && b.module(WorkerModule.class).map(w -> !w.workers().isEmpty()).orElse(false);
    }
}
