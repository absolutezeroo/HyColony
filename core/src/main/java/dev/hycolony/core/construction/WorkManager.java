package dev.hycolony.core.construction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Action;
import dev.hycolony.core.colony.ClaimCell;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.port.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final Comparator<WorkOrder> ORDER = Comparator.comparingInt(WorkOrder::priority).reversed()
            .thenComparingInt(WorkOrder::id);

    private final Colony colony;
    private final Map<Integer, WorkOrder> orders = new LinkedHashMap<>();
    private final Map<BlockPos, WorkOrder> byBuilding = new HashMap<>();
    private int topId;

    public WorkManager(Colony colony) {
        this.colony = colony;
    }

    /**
     * Refusals in order: permission, duplicate, max level, repair of an unbuilt building, a type that does not fit
     * the building ({@link #isAllowed}), no builder of the level,
     * no builder within 100 blocks (unless one is chosen), no blueprint, footprint outside the colony.
     * {@code buildingPos} must hold a building of this colony.
     */
    public Either<WorkOrder, WorkOrderRefusal> request(UUID player, BlockPos buildingPos, WorkOrderType type,
            String style, Optional<BlockPos> builder) {
        if (!colony.permissions().hasPermission(player, Action.MANAGE_HUTS)) {
            return refuse(WorkOrderRefusal.NO_PERMISSION);
        }
        Building b = colony.buildings().at(buildingPos)
                .orElseThrow(() -> new IllegalArgumentException("No building at " + buildingPos));
        if (byBuilding.containsKey(buildingPos)) {
            return refuse(WorkOrderRefusal.ALREADY_EXISTS);
        }
        int level = b.level();
        if ((type == WorkOrderType.BUILD || type == WorkOrderType.UPGRADE) && level >= b.type().maxLevel()) {
            return refuse(WorkOrderRefusal.MAX_LEVEL);
        }
        if (type == WorkOrderType.REPAIR && level == 0 && !b.isDeconstructed()) {
            return refuse(WorkOrderRefusal.NOT_BUILT);
        }
        if (!isAllowed(b, type)) {
            return refuse(WorkOrderRefusal.INVALID_TYPE);
        }
        // WorkOrderBuilding.create: REMOVE targets 0 but follows the plan of the current level.
        int target = switch (type) {
            case BUILD, UPGRADE -> level + 1;
            case REPAIR -> level;
            case REMOVE -> 0;
        };
        int blueprintLevel = type == WorkOrderType.REMOVE ? level : target;
        List<Building> employed = colony.buildings().all().stream().filter(WorkManager::isEmployedBuilder).toList();
        if (type != WorkOrderType.REMOVE && !canBeBuiltByBuilder(b, target)
                && employed.stream().noneMatch(e -> e.level() >= target)) {
            return refuse(WorkOrderRefusal.BUILDER_NECESSARY);
        }
        if (builder.isEmpty() && employed.stream().noneMatch(e -> e.position().distSq(buildingPos) <= MAX_DISTANCE_SQ)) {
            return refuse(WorkOrderRefusal.BUILDER_TOO_FAR_AWAY);
        }
        if (builder.isPresent()) {
            // REMOVE targets 0, so any builder hut qualifies.
            Optional<Building> chosen = colony.buildings().at(builder.get());
            if (chosen.isEmpty() || !chosen.get().type().equals(ConstructionBuildingTypes.BUILDER)
                    || (chosen.get().level() < target && !canBeBuiltByBuilder(b, target))) {
                return refuse(WorkOrderRefusal.BUILDER_NECESSARY);
            }
        }
        String resolvedStyle = resolveStyle(style, type, b);
        Optional<Blueprint> blueprint = colony.context().ports().blueprints()
                .load(resolvedStyle, b.type().id(), blueprintLevel, b.rotation());
        if (blueprint.isEmpty()) {
            return refuse(WorkOrderRefusal.NO_BLUEPRINT);
        }
        if (!insideColony(blueprint.get(), buildingPos)) {
            return refuse(WorkOrderRefusal.OUT_OF_COLONY);
        }

        WorkOrder order = new WorkOrder(++topId, type, buildingPos, target, blueprintLevel, resolvedStyle,
                b.rotation());
        builder.ifPresent(order::setClaimedBy);
        add(order);
        if (type == WorkOrderType.BUILD) {
            b.setStyle(resolvedStyle); // the building keeps the style it is built in
        }
        colony.markDirty();
        Msg created = Msg.of("hycolony.workorder.created", b.displayName(), colony.name(),
                String.valueOf(buildingPos.x()), String.valueOf(buildingPos.y()), String.valueOf(buildingPos.z()));
        for (UUID member : colony.permissions().members().keySet()) {
            if (colony.permissions().hasPermission(member, Action.RECEIVE_MESSAGES)) {
                colony.context().notifier().send(member, created);
            }
        }
        colony.context().bus().post(new ColonyEvents.WorkOrderCreated(colony, order));
        return new Either.Left<>(order);
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
            colony.buildings().at(claimer.get()).ifPresent(hut -> colony.requests().cancelAllFrom(hut.requesterId()));
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
        for (int i = 0; i < list.size(); i++) {
            WorkOrder o = list.get(i);
            if (o.id() != orderId) {
                continue;
            }
            if (delta > 0 && i > 0) {
                o.setPriority(list.get(i - 1).priority() + 1);
                colony.markDirty();
            } else if (delta < 0 && i < list.size() - 1) {
                o.setPriority(list.get(i + 1).priority() - 1);
                colony.markDirty();
            }
            return;
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
    boolean holds(WorkOrder o) {
        return orders.get(o.id()) == o;
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
        return orders.values().stream().filter(o -> builderHut.equals(o.claimedBy().orElse(null)))
                .min(Comparator.comparingInt(WorkOrder::id));
    }

    /**
     * WorkManager.onColonyTick: each free order, in {@link #ordered()} order, goes to the first builder hut (in
     * building order) that has a worker, holds no order, is not MANUAL and passes {@link #canBuild}. Claimed orders
     * already sit with their builder (MineColonies sorts them first only to mark those builders busy).
     */
    public void tick() {
        // WorkOrderBuilding.isValid: an order whose building is gone is dropped.
        List<Integer> invalid = new ArrayList<>();
        for (WorkOrder o : orders.values()) {
            if (colony.buildings().at(o.buildingPos()).isEmpty()) {
                invalid.add(o.id());
            }
        }
        invalid.forEach(this::cancel);
        Set<BlockPos> busy = new HashSet<>();
        List<WorkOrder> free = new ArrayList<>();
        for (WorkOrder o : orders.values()) {
            Optional<BlockPos> claimer = o.claimedBy();
            if (claimer.isPresent() && colony.buildings().at(claimer.get()).isPresent()) {
                busy.add(claimer.get());
                continue;
            }
            if (claimer.isPresent()) { // failsafe: the builder hut vanished
                o.release();
                colony.markDirty();
            }
            free.add(o);
        }
        if (free.isEmpty()) {
            return;
        }
        free.sort(ORDER);
        List<Building> idle = new ArrayList<>();
        for (Building b : colony.buildings().all()) {
            if (isEmployedBuilder(b) && !busy.contains(b.position()) && b.module(BuilderSettingsModule.class)
                    .map(s -> s.mode() != BuilderSettingsModule.Mode.MANUAL).orElse(true)) {
                idle.add(b);
            }
        }
        for (WorkOrder o : free) {
            for (Iterator<Building> it = idle.iterator(); it.hasNext(); ) {
                Building b = it.next();
                if (canBuild(b, o, b.level())) {
                    o.setClaimedBy(b.position());
                    it.remove();
                    colony.markDirty();
                    break;
                }
            }
        }
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

    public JsonArray write() {
        JsonArray arr = new JsonArray();
        orders.values().forEach(o -> arr.add(o.write()));
        return arr;
    }

    public void read(JsonArray arr) {
        orders.clear();
        byBuilding.clear();
        for (JsonElement el : arr) {
            WorkOrder o = WorkOrder.read(el.getAsJsonObject());
            add(o);
            topId = Math.max(topId, o.id());
        }
    }

    private void add(WorkOrder o) {
        orders.put(o.id(), o);
        byBuilding.put(o.buildingPos(), o);
    }

    private static Either<WorkOrder, WorkOrderRefusal> refuse(WorkOrderRefusal r) {
        return new Either.Right<>(r);
    }

    private static boolean isEmployedBuilder(Building b) {
        return b.type().equals(ConstructionBuildingTypes.BUILDER)
                && b.module(WorkerModule.class).map(w -> !w.workers().isEmpty()).orElse(false);
    }

    /** BuildingBuilder.canBeBuiltByBuilder: a builder hut may always order its own next level. */
    private static boolean canBeBuiltByBuilder(Building b, int target) {
        return b.type().equals(ConstructionBuildingTypes.BUILDER) && target == b.level() + 1;
    }

    /** A built building keeps its style: only a BUILD may choose one. */
    private String resolveStyle(String style, WorkOrderType type, Building b) {
        if (type != WorkOrderType.BUILD && !b.style().isEmpty()) {
            return b.style();
        }
        if (style != null && !style.isEmpty()) {
            return style;
        }
        if (!b.style().isEmpty()) {
            return b.style();
        }
        List<String> styles = colony.context().ports().blueprints().styles();
        return styles.isEmpty() ? "" : styles.get(0);
    }

    /**
     * WorkManager.isWorkOrderWithinColony: every claim cell of the footprint rectangle (hut + min .. hut + max, in x
     * and z) must belong to this colony.
     */
    private boolean insideColony(Blueprint bp, BlockPos hut) {
        ClaimCell a = ClaimCell.of(hut.offset(bp.min().x(), 0, bp.min().z()));
        ClaimCell b = ClaimCell.of(hut.offset(bp.max().x(), 0, bp.max().z()));
        for (int cx = Math.min(a.x(), b.x()); cx <= Math.max(a.x(), b.x()); cx++) {
            for (int cz = Math.min(a.z(), b.z()); cz <= Math.max(a.z(), b.z()); cz++) {
                if (!colony.contains(new BlockPos(cx * ClaimCell.SIZE, hut.y(), cz * ClaimCell.SIZE))) {
                    return false;
                }
            }
        }
        return true;
    }
}
