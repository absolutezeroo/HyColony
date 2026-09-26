package dev.hycolony.core.colony;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.persistence.ColonySerializer;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.colony.ui.BuilderResourcesView;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.colony.ui.CitizenRow;
import dev.hycolony.core.colony.ui.CitizenView;
import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.RequestsView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.colony.ui.WorkOrdersView;
import dev.hycolony.core.construction.ClaimRadius;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.resources.NeededResources;
import dev.hycolony.core.construction.workorder.WorkManager;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderRefusal;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.ColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.kernel.persist.SchemaTooNewException;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** All colonies of one world. Entry point the plugin calls, always on the world thread. */
public final class ColonyManager {
    public static final int MAX_NAME_LENGTH = 32;

    private static final System.Logger LOG = System.getLogger(ColonyManager.class.getName());

    private record PendingFoundation(String playerName, BlockPos pos, int rotation) {}

    private final ColonyContext ctx;
    private final TerritoryIndex territory = new TerritoryIndex();
    private final Map<Integer, Colony> colonies = new LinkedHashMap<>();
    private final Map<UUID, PendingFoundation> pending = new HashMap<>();
    private int nextId = 1;

    private ColonyStorage storage;
    private MigrationChain migrations = MigrationChain.sp1();
    /** Ids whose file must never be touched (newer schema). */
    private final Set<Integer> lockedIds = new HashSet<>();
    /** Set once listing the storage fails: saves are refused and founding is denied until restart. */
    private boolean storageUnavailable;

    public ColonyManager(ColonyContext ctx) {
        this.ctx = ctx;
    }

    public ColonyContext context() {
        return ctx;
    }

    public TerritoryIndex territory() {
        return territory;
    }

    public Collection<Colony> all() {
        return Collections.unmodifiableCollection(colonies.values());
    }

    public Optional<Colony> byId(int id) {
        return Optional.ofNullable(colonies.get(id));
    }

    public Optional<Colony> colonyAt(BlockPos pos) {
        var id = territory.colonyAt(pos);
        return id.isPresent() ? byId(id.getAsInt()) : Optional.empty();
    }

    public Optional<Colony> ownedBy(UUID player) {
        return colonies.values().stream()
                .filter(c -> c.permissions().owner().equals(player))
                .findFirst();
    }

    // ---- Hut placement (port of AbstractBlockHut.canPaste) ----

    public HutPlacement checkHutPlacement(UUID player, BlockPos pos, String buildingTypeId) {
        boolean isTownHall = BuildingTypes.TOWN_HALL.id().equals(buildingTypeId);
        Optional<Colony> colony = colonyAt(pos);
        if (colony.isEmpty()) {
            if (!isTownHall) {
                return new HutPlacement.Denied(
                        Msg.of(ownedBy(player).isPresent() ? "hycolony.hut.tooFar" : "hycolony.hut.noTownHall"));
            }
            if (!storageAvailable()) {
                return new HutPlacement.Denied(Msg.of("hycolony.storage.unavailable"));
            }
            if (ownedBy(player).isPresent()) {
                return new HutPlacement.Denied(Msg.of("hycolony.colony.alreadyOwner"));
            }
            if (!territory.isFreeForNewColony(
                    pos, ctx.config().initialColonySize(), ctx.config().minColonyDistance())) {
                return new HutPlacement.Denied(Msg.of("hycolony.colony.tooClose"));
            }
            return new HutPlacement.FoundNewColony();
        }
        Colony c = colony.get();
        if (!c.permissions().hasPermission(player, Action.PLACE_HUTS)) {
            return new HutPlacement.Denied(Msg.of("hycolony.permission.placeHuts", c.name()));
        }
        if (isTownHall && c.buildings().townHall().isPresent()) {
            return new HutPlacement.Denied(Msg.of("hycolony.hut.townHallExists"));
        }
        return new HutPlacement.Allowed(c);
    }

    public void beginFoundation(UUID player, String playerName, BlockPos pos, int rotation) {
        pending.put(player, new PendingFoundation(playerName, pos, rotation));
        ctx.ui().showFoundColony(player, new FoundColonyView(playerName + "'s Colony"));
    }

    /** Empty if nothing is pending, the name is invalid, or the spot became invalid meanwhile. */
    public Optional<Colony> confirmFoundation(UUID player, String rawName) {
        PendingFoundation p = pending.get(player);
        if (p == null) {
            return Optional.empty();
        }
        Optional<String> validated = validName(player, rawName);
        if (validated.isEmpty()) {
            return Optional.empty();
        }
        String name = validated.get();
        HutPlacement check = checkHutPlacement(player, p.pos(), BuildingTypes.TOWN_HALL.id());
        if (check instanceof HutPlacement.Denied denied) {
            // Same as a cancel; the adapter sees pendingPositionOf go empty and removes the block.
            cancelFoundation(player);
            ctx.notifier().send(player, denied.reason());
            return Optional.empty();
        }
        pending.remove(player);
        ctx.ui().close(player);
        Colony colony = new Colony(
                ctx,
                territory,
                new Colony.Founding(allocateId(), name, p.pos(), Permissions.createDefault(player, p.playerName())));
        register(colony);
        colony.log().add("colonyCreated", colony.day(), name);
        ctx.bus().post(new ColonyEvents.ColonyCreated(colony));
        placeHut(colony, BuildingTypes.TOWN_HALL.id(), p.pos(), p.rotation());
        ctx.notifier().send(player, Msg.of("hycolony.colony.created", name));
        save(colony);
        return Optional.of(colony);
    }

    /** Returns where the unconfirmed town hall stands, so the adapter can remove it. */
    public Optional<BlockPos> cancelFoundation(UUID player) {
        // Remove before closing, and close only once: closing may re-enter here (window dismiss = cancel).
        PendingFoundation p = pending.remove(player);
        if (p == null) {
            return Optional.empty();
        }
        ctx.ui().close(player);
        return Optional.of(p.pos());
    }

    public Optional<BlockPos> pendingPositionOf(UUID player) {
        return Optional.ofNullable(pending.get(player)).map(PendingFoundation::pos);
    }

    /** Cancels whichever player's unconfirmed town hall stands at {@code pos}; returns that player. */
    public Optional<UUID> cancelFoundationAt(BlockPos pos) {
        Optional<UUID> owner = pending.entrySet().stream()
                .filter(e -> e.getValue().pos().equals(pos))
                .map(Map.Entry::getKey)
                .findFirst();
        owner.ifPresent(this::cancelFoundation);
        return owner;
    }

    /** A building already registered at {@code pos} is stale (its block is gone): it is removed first, never a throw. */
    public void placeHut(Colony colony, String buildingTypeId, BlockPos pos, int rotation) {
        BuildingType type = ctx.buildingTypes().byId(buildingTypeId).orElseThrow();
        removeHut(colony, pos);
        Building building = Building.create(type, pos, rotation);
        colony.buildings().add(building);
        colony.log().add("buildingPlaced", colony.day(), type.id());
        colony.markDirty();
        ctx.bus().post(new ColonyEvents.BuildingPlaced(colony, building));
    }

    public void onHutRemoved(BlockPos pos) {
        colonyAt(pos).ifPresent(c -> removeHut(c, pos));
    }

    private void removeHut(Colony c, BlockPos pos) {
        c.buildings().remove(pos).ifPresent(b -> {
            c.log().add("buildingRemoved", c.day(), b.type().id());
            c.markDirty();
            ctx.bus().post(new ColonyEvents.BuildingRemoved(c, b));
        });
    }

    // ---- Protection and management ----

    public boolean protectionEnabled() {
        return ctx.config().enableColonyProtection();
    }

    /** Outside any colony everything is allowed. */
    public boolean isAllowed(UUID player, BlockPos pos, Action action) {
        return colonyAt(pos)
                .map(c -> c.permissions().hasPermission(player, action))
                .orElse(true);
    }

    public boolean setRank(UUID actor, int colonyId, UUID target, String targetName, int rankId) {
        Colony c = colonies.get(colonyId);
        if (c == null || !c.permissions().hasPermission(actor, Action.EDIT_PERMISSIONS)) {
            return false;
        }
        boolean changed = c.permissions().setRank(target, targetName, rankId);
        if (changed) {
            c.markDirty();
        }
        return changed;
    }

    public void openTownHall(UUID player, BlockPos hutPos) {
        Optional<Colony> colony = colonyAt(hutPos);
        if (colony.isEmpty()) {
            return;
        }
        Colony c = colony.get();
        if (canAccess(c, player)) {
            ctx.ui().showTownHall(player, townHallView(c, player));
        }
    }

    /** ACCESS_HUTS, else the player is told. */
    private boolean canAccess(Colony c, UUID player) {
        if (c.permissions().hasPermission(player, Action.ACCESS_HUTS)) {
            return true;
        }
        ctx.notifier().send(player, Msg.of("hycolony.permission.denied", c.name()));
        return false;
    }

    public boolean rename(UUID actor, int colonyId, String rawName) {
        Colony c = colonies.get(colonyId);
        if (c == null || !c.permissions().rankOf(actor).isColonyManager()) {
            return false;
        }
        Optional<String> name = validName(actor, rawName);
        if (name.isEmpty()) {
            return false;
        }
        c.setName(name.get());
        ctx.ui().showTownHall(actor, townHallView(c, actor));
        return true;
    }

    /** Trims {@code raw}; empty if blank or over {@link #MAX_NAME_LENGTH}, after notifying {@code actor}. */
    private Optional<String> validName(UUID actor, String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            ctx.notifier().send(actor, Msg.of("hycolony.colony.invalidName", String.valueOf(MAX_NAME_LENGTH)));
            return Optional.empty();
        }
        return Optional.of(name);
    }

    private TownHallView townHallView(Colony c, UUID viewer) {
        List<CitizenRow> rows = c.citizens().all().stream().map(d -> row(c, d)).toList();
        return new TownHallView(
                c.id(),
                c.name(),
                c.permissions().ownerName(),
                c.day(),
                rows,
                c.permissions().rankOf(viewer).isColonyManager());
    }

    private CitizenRow row(Colony c, CitizenData d) {
        return new CitizenRow(d.name(), d.gender(), status(c, d));
    }

    /** "absent" without a live body, else the AI state: "idle", "wandering" or "working". */
    private String status(Colony c, CitizenData d) {
        boolean present = c.citizens().bodyOf(d.id()).map(ctx.bodies()::isAlive).orElse(false);
        return !present
                ? "absent"
                : c.citizens()
                        .aiState(d.id())
                        .map(s -> s.name().toLowerCase(Locale.ROOT))
                        .orElse("idle");
    }

    // ---- Citizen window (MC WindowCitizen) ----

    /** Right-click on a citizen (ACCESS_HUTS, else the player is told). */
    public void openCitizen(UUID player, int colonyId, int citizenId) {
        Colony c = colonies.get(colonyId);
        CitizenData d = c == null ? null : c.citizens().get(citizenId).orElse(null);
        if (d == null || !canAccess(c, player)) {
            return;
        }
        Map<ItemKey, Integer> owned = ctx.ports().playerInventory().contents(player);
        List<RequestsView.RequestRow> requests = new ArrayList<>();
        for (Request r : c.requests().all()) {
            if (r.citizenId() == citizenId && r.state().ordinal() < RequestState.COMPLETED.ordinal()) {
                requests.add(requestRow(c, r, owned));
            }
        }
        Optional<Deliverable> waitingFor = requests.stream().findFirst().map(RequestsView.RequestRow::requestable);
        Map<Skill, Integer> skills = new EnumMap<>(Skill.class);
        for (Skill s : Skill.values()) {
            skills.put(s, d.skills().level(s));
        }
        ctx.ui()
                .showCitizen(
                        player,
                        new CitizenView(
                                c.id(),
                                d.id(),
                                d.name(),
                                d.job().map(j -> j.type().id()),
                                Optional.ofNullable(d.workBuilding())
                                        .flatMap(c.buildings()::at)
                                        .map(Building::displayName),
                                waitingFor.isPresent() ? "waitingFor" : status(c, d),
                                waitingFor,
                                c.citizens().jobActivity(d.id()),
                                skills,
                                d.inventory().contents(),
                                requests));
    }

    /** Archives before freeing anything; if archiving fails the colony stays registered. */
    public boolean deleteColony(int colonyId) {
        Colony c = colonies.get(colonyId);
        if (c == null) {
            return false;
        }
        if (storage != null) {
            try {
                storage.archive(colonyId);
            } catch (IOException e) {
                LOG.log(System.Logger.Level.ERROR, "Archiving colony " + colonyId + " failed; colony kept", e);
                return false;
            }
        }
        colonies.remove(colonyId);
        c.citizens().despawnAll();
        territory.releaseAll(colonyId);
        ctx.bus().post(new ColonyEvents.ColonyDeleted(colonyId));
        return true;
    }

    // ---- Work orders ----

    /** The hut's Build/Upgrade/Repair/Deconstruct button. On refusal the player gets its message. */
    public Optional<WorkOrder> requestWorkOrder(
            UUID player, BlockPos buildingPos, WorkOrderType type, String style, Optional<BlockPos> builder) {
        Colony c = colonyAt(buildingPos).orElse(null);
        if (c == null || c.buildings().at(buildingPos).isEmpty()) {
            return Optional.empty();
        }
        return submitWorkOrder(c, player, buildingPos, type, style, builder)
                        instanceof Either.Left<WorkOrder, WorkOrderRefusal> created
                ? Optional.of(created.value())
                : Optional.empty();
    }

    /** The hut window's order button: empty on success (the window is re-shown), else the refusal. */
    public Optional<WorkOrderRefusal> orderWork(UUID player, BlockPos hutPos, WorkOrderType type, String style) {
        Colony c = colonyAt(hutPos).orElse(null);
        Building b = c == null ? null : c.buildings().at(hutPos).orElse(null);
        if (b == null) {
            return Optional.of(WorkOrderRefusal.INVALID_TYPE); // the hut is gone
        }
        if (submitWorkOrder(c, player, hutPos, type, style, Optional.empty())
                instanceof Either.Right<WorkOrder, WorkOrderRefusal> refused) {
            return Optional.of(refused.value());
        }
        showBuilding(c, b, player);
        return Optional.empty();
    }

    /** On refusal the player gets its message. */
    private Either<WorkOrder, WorkOrderRefusal> submitWorkOrder(
            Colony c, UUID player, BlockPos pos, WorkOrderType type, String style, Optional<BlockPos> builder) {
        Either<WorkOrder, WorkOrderRefusal> r = c.work().request(player, pos, type, style, builder);
        if (r instanceof Either.Right<WorkOrder, WorkOrderRefusal> refused) {
            ctx.notifier()
                    .send(
                            player,
                            Msg.of("hycolony.workorder.refused."
                                    + refused.value().name().toLowerCase(Locale.ROOT)));
        }
        return r;
    }

    /** The hut window's Cancel button (MANAGE_HUTS). */
    public boolean cancelWork(UUID player, BlockPos hutPos) {
        Hut h = managedHut(player, hutPos).orElse(null);
        Optional<WorkOrder> order =
                h == null ? Optional.empty() : h.colony().work().byBuilding(hutPos);
        if (order.isEmpty()) {
            return false;
        }
        h.colony().work().cancel(order.get().id());
        showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /** Town hall info tab up/down arrows: {@code delta > 0} moves the order up. */
    public boolean moveWorkOrder(UUID player, int colonyId, int orderId, int delta) {
        Colony c = colonies.get(colonyId);
        if (c == null
                || !c.permissions().hasPermission(player, Action.MANAGE_HUTS)
                || c.work().byId(orderId).isEmpty()) {
            return false;
        }
        c.work().move(orderId, delta);
        ctx.ui().showWorkOrders(player, workOrdersView(c, player));
        return true;
    }

    public boolean deleteWorkOrder(UUID player, int colonyId, int orderId) {
        Colony c = colonies.get(colonyId);
        if (c == null
                || !c.permissions().hasPermission(player, Action.MANAGE_HUTS)
                || c.work().byId(orderId).isEmpty()) {
            return false;
        }
        c.work().cancel(orderId);
        ctx.ui().showWorkOrders(player, workOrdersView(c, player));
        return true;
    }

    public void openWorkOrders(UUID player, int colonyId) {
        Colony c = colonies.get(colonyId);
        if (c != null && canAccess(c, player)) {
            ctx.ui().showWorkOrders(player, workOrdersView(c, player));
        }
    }

    private WorkOrdersView workOrdersView(Colony c, UUID viewer) {
        List<WorkOrdersView.OrderLine> lines = c.work().ordered().stream()
                .map(o -> new WorkOrdersView.OrderLine(
                        o.id(),
                        o.type(),
                        c.buildings()
                                .at(o.buildingPos())
                                .map(Building::displayName)
                                .orElse(""),
                        o.targetLevel(),
                        o.priority(),
                        builderName(c, o)))
                .toList();
        return new WorkOrdersView(c.id(), lines, c.permissions().hasPermission(viewer, Action.MANAGE_HUTS));
    }

    // ---- Hut windows ----

    private record Hut(Colony colony, Building building) {}

    /** The hut at {@code pos} if {@code player} may manage it (MANAGE_HUTS, as MC's building messages). */
    private Optional<Hut> managedHut(UUID player, BlockPos pos) {
        return colonyAt(pos)
                .filter(c -> c.permissions().hasPermission(player, Action.MANAGE_HUTS))
                .flatMap(c -> c.buildings().at(pos).map(b -> new Hut(c, b)));
    }

    /** Any hut's window (ACCESS_HUTS); the town hall's window reaches it through its "building" action. */
    public void openBuilding(UUID player, BlockPos hutPos) {
        Colony c = colonyAt(hutPos).orElse(null);
        Building b = c == null ? null : c.buildings().at(hutPos).orElse(null);
        if (b != null && canAccess(c, player)) {
            showBuilding(c, b, player);
        }
    }

    public boolean hire(UUID player, BlockPos hutPos, int citizenId) {
        Hut h = managedHut(player, hutPos).orElse(null);
        WorkerModule w =
                h == null ? null : h.building().module(WorkerModule.class).orElse(null);
        CitizenData citizen =
                w == null ? null : h.colony().citizens().get(citizenId).orElse(null);
        if (citizen == null || citizen.isChild() || !w.hire(h.colony(), h.building(), citizen)) {
            return false;
        }
        showBuilding(h.colony(), h.building(), player);
        return true;
    }

    public boolean fire(UUID player, BlockPos hutPos, int citizenId) {
        Hut h = managedHut(player, hutPos).orElse(null);
        WorkerModule w =
                h == null ? null : h.building().module(WorkerModule.class).orElse(null);
        if (w == null || !w.workers().contains(citizenId)) {
            return false;
        }
        w.fire(h.colony(), h.building(), citizenId);
        showBuilding(h.colony(), h.building(), player);
        return true;
    }

    public boolean setHiring(UUID player, BlockPos hutPos, HiringMode mode) {
        Hut h = managedHut(player, hutPos).orElse(null);
        WorkerModule w =
                h == null ? null : h.building().module(WorkerModule.class).orElse(null);
        if (w == null || mode == null) {
            return false;
        }
        w.setHiringMode(mode);
        h.colony().markDirty();
        showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /**
     * MC's requestRemoval on a deconstructed hut (AbstractBuilding.pickUp): once every check passes, {@code giveItem}
     * gives the player the hut item (with its level) and says whether it fit. Only then does the building leave the
     * colony through the normal removal path (workers fired, requests and orders cancelled); the plugin removes the
     * block. A full inventory refuses and keeps the building.
     */
    public boolean pickUpBuilding(UUID player, BlockPos hutPos, BooleanSupplier giveItem) {
        Hut h = managedHut(player, hutPos).orElse(null);
        if (h == null || !canPickUp(h.building())) {
            return false;
        }
        if (!giveItem.getAsBoolean()) {
            ctx.notifier().send(player, Msg.of("hycolony.hut.pickupInventoryFull"));
            return false;
        }
        onHutRemoved(hutPos);
        ctx.ui().close(player);
        return true;
    }

    private static boolean canPickUp(Building b) {
        return b.isDeconstructed() && !b.type().equals(BuildingTypes.TOWN_HALL);
    }

    private void showBuilding(Colony c, Building b, UUID viewer) {
        Optional<WorkerModule> w = b.module(WorkerModule.class);
        List<BuildingView.WorkerRow> workers = w.map(m -> m.workers().stream()
                        .flatMap(id -> c.citizens().get(id).stream())
                        .map(ColonyManager::workerRow)
                        .toList())
                .orElse(List.of());
        List<BuildingView.WorkerRow> hireable = w.isEmpty()
                ? List.of()
                : c.citizens().all().stream()
                        .filter(d -> !d.isChild() && d.job().isEmpty() && d.workBuilding() == null)
                        .map(ColonyManager::workerRow)
                        .toList();
        Optional<WorkOrder> order = c.work().byBuilding(b.position());
        Set<WorkOrderType> allowed = EnumSet.noneOf(WorkOrderType.class);
        if (order.isEmpty()) {
            for (WorkOrderType type : WorkOrderType.values()) {
                if (WorkManager.isAllowed(b, type)) {
                    allowed.add(type);
                }
            }
        }
        boolean manage = c.permissions().hasPermission(viewer, Action.MANAGE_HUTS);
        ctx.ui()
                .showBuilding(
                        viewer,
                        new BuildingView(
                                c.id(),
                                b.position(),
                                b.type().id(),
                                b.level(),
                                b.type().maxLevel(),
                                b.isBuilt(),
                                b.isDeconstructed(),
                                workers,
                                hireable,
                                w.map(WorkerModule::hiringMode),
                                order.map(o -> new BuildingView.OrderRow(
                                        o.id(), o.type(), o.targetLevel(), builderName(c, o), percent(c, o))),
                                allowed,
                                ctx.ports().blueprints().styles(),
                                b.style(),
                                manage,
                                manage && canPickUp(b)));
    }

    private static BuildingView.WorkerRow workerRow(CitizenData d) {
        return new BuildingView.WorkerRow(d.id(), d.name());
    }

    private static Optional<CitizenData> firstWorker(Colony c, Building b) {
        return b.module(WorkerModule.class)
                .flatMap(w -> w.workers().stream().findFirst())
                .flatMap(c.citizens()::get);
    }

    private static Optional<String> builderName(Colony c, WorkOrder o) {
        return o.claimedBy()
                .flatMap(c.buildings()::at)
                .flatMap(hut -> firstWorker(c, hut))
                .map(CitizenData::name);
    }

    /** The order's progress, from its builder's resources module once that builder started it; 0 before. */
    private static int percent(Colony c, WorkOrder o) {
        return o.claimedBy()
                .flatMap(c.buildings()::at)
                .flatMap(hut -> hut.module(BuildingResourcesModule.class))
                .filter(m -> m.orderId() == o.id())
                .map(m -> percent(m.needs()))
                .orElse(0);
    }

    /** BuildingResourcesModuleView.getProgress: 100 minus the share of the plan's items still to place. */
    private static int percent(NeededResources needs) {
        int total = needs.sequence().size();
        return total == 0 ? 0 : Math.max(100 - (int) (needs.total() * 100.0 / total), 0);
    }

    /** The builder hut's resources tab (ACCESS_HUTS). */
    public void openBuilderResources(UUID player, BlockPos hutPos) {
        Colony c = colonyAt(hutPos).orElse(null);
        Building hut = c == null ? null : c.buildings().at(hutPos).orElse(null);
        BuildingResourcesModule m =
                hut == null ? null : hut.module(BuildingResourcesModule.class).orElse(null);
        if (m == null || !canAccess(c, player)) {
            return;
        }
        Optional<WorkOrder> order = c.work().claimedBy(hutPos);
        List<BuilderResourcesView.ResourceRow> rows = new ArrayList<>();
        // A free order needs nothing: its list is empty.
        if (order.isPresent() && m.orderId() == order.get().id() && !order.get().free()) {
            ConstructionPorts ports = ctx.ports();
            Optional<Inventory> inv = firstWorker(c, hut).map(CitizenData::inventory);
            List<BlockPos> containers = hut.containers();
            m.needs().remaining().forEach((item, needed) -> {
                int available = inv.map(i -> i.count(item)).orElse(0)
                        + ports.containers().count(containers, item);
                int has = ports.playerInventory().count(player, item);
                rows.add(new BuilderResourcesView.ResourceRow(
                        item, needed, available, has, BuilderResourcesView.Status.of(needed, available, has)));
            });
        }
        ctx.ui()
                .showBuilderResources(
                        player,
                        new BuilderResourcesView(
                                c.id(),
                                hutPos,
                                rows,
                                order.map(o -> percent(c, o)).orElse(0),
                                order.map(o -> o.stage().name().toLowerCase(Locale.ROOT))
                                        .orElse("")));
    }

    // ---- Requests: the player's "Fournir" / "Ajouter" ----

    /** The clipboard (ACCESS_HUTS): root requests held by the player or retrying resolver, as WindowClipBoard. */
    public void openRequests(UUID player, int colonyId) {
        Colony c = colonies.get(colonyId);
        if (c == null || !canAccess(c, player)) {
            return;
        }
        RequestManager m = c.requests();
        Map<RequestToken, Request> roots = new LinkedHashMap<>();
        for (String resolver : List.of(PlayerResolver.ID, RetryingResolver.ID)) {
            for (Request r : m.assignedTo(resolver)) {
                if (r.state().ordinal() >= RequestState.COMPLETED.ordinal()) {
                    continue;
                }
                Request root = r;
                while (root != null && root.parent().isPresent()) {
                    root = m.get(root.parent().get()).orElse(null);
                }
                if (root != null) {
                    roots.putIfAbsent(root.token(), root);
                }
            }
        }
        // WindowClipBoard: nearest requester to the player first, then by token (no position: token order only).
        Optional<BlockPos> at = ctx.players().position(player);
        List<Request> sorted = new ArrayList<>(roots.values());
        sorted.sort(Comparator.comparingLong((Request r) -> at.map(p -> c.buildings()
                                .byRequester(r.requester())
                                .map(b -> b.position().distSq(p))
                                .orElse(Long.MAX_VALUE))
                        .orElse(0L))
                .thenComparing(r -> r.token().id()));
        Map<ItemKey, Integer> owned = ctx.ports().playerInventory().contents(player);
        List<RequestsView.RequestRow> rows =
                sorted.stream().map(r -> requestRow(c, r, owned)).toList();
        ctx.ui().showRequests(player, new RequestsView(c.id(), rows));
    }

    /** A request as the player sees it: who asks, and how many matching items {@code owned} holds. */
    private RequestsView.RequestRow requestRow(Colony c, Request r, Map<ItemKey, Integer> owned) {
        int has = 0;
        for (Map.Entry<ItemKey, Integer> e : owned.entrySet()) {
            if (r.requestable().matches(e.getKey(), ctx.ports().catalog())) {
                has += e.getValue();
            }
        }
        String requester = r.citizenId() != -1
                ? c.citizens().get(r.citizenId()).map(CitizenData::name).orElse("")
                : c.buildings()
                        .byRequester(r.requester())
                        .map(Building::displayName)
                        .orElse(r.requester().value());
        return new RequestsView.RequestRow(r.token(), r.requestable(), requester, has);
    }

    /**
     * "Fournir": moves min(requested, owned) from the player to the requesting citizen (or, for the building
     * itself, its hut containers) and overrules the request. A partial amount still closes it; the requester asks
     * again for the rest. False if nothing was moved.
     */
    public boolean fulfil(UUID player, int colonyId, RequestToken token) {
        Colony c = colonies.get(colonyId);
        if (c == null || !c.permissions().hasPermission(player, Action.ACCESS_HUTS)) {
            return false;
        }
        Request req = c.requests().get(token).orElse(null);
        if (req == null || req.state().ordinal() >= RequestState.COMPLETED.ordinal()) {
            return false;
        }
        ConstructionPorts ports = ctx.ports();
        Deliverable wanted = req.requestable();
        Optional<ItemKey> item = wanted instanceof StackRequest s
                ? Optional.of(s.item())
                : ports.playerInventory().contents(player).keySet().stream()
                        .filter(k -> wanted.matches(k, ports.catalog()))
                        .findFirst();
        if (item.isEmpty()) {
            return false;
        }
        int n = ports.playerInventory().take(player, item.get(), wanted.count());
        if (n <= 0) {
            return false;
        }
        ItemAmount taken = new ItemAmount(item.get(), n);
        Optional<CitizenData> citizen =
                req.citizenId() == -1 ? Optional.empty() : c.citizens().get(req.citizenId());
        ItemAmount rest = taken;
        if (citizen.isPresent()) {
            rest = citizen.get().inventory().insert(taken, ports.catalog()::maxStack);
        } else {
            Optional<Building> hut = c.buildings().byRequester(req.requester());
            if (hut.isPresent()) {
                rest = ports.containers().insert(hut.get().containers(), taken);
            }
        }
        int moved = n - giveBack(player, rest);
        if (moved <= 0) {
            return false;
        }
        c.requests().overrule(token, List.of(new ItemAmount(item.get(), moved)), citizen.isPresent());
        c.markDirty();
        return true;
    }

    /**
     * "Ajouter": moves min(wanted, owned) from the player into the hut's containers, then overrules the first open
     * request of that building held by the player or retrying resolver for that item. Returns how many moved.
     */
    public int addToHut(UUID player, BlockPos hutPos, ItemKey item, int wanted) {
        Colony c = colonyAt(hutPos).orElse(null);
        if (c == null || wanted <= 0 || !c.permissions().hasPermission(player, Action.ACCESS_HUTS)) {
            return 0;
        }
        Building b = c.buildings().at(hutPos).orElse(null);
        if (b == null) {
            return 0;
        }
        ConstructionPorts ports = ctx.ports();
        int taken = ports.playerInventory().take(player, item, wanted);
        if (taken <= 0) {
            return 0;
        }
        int moved = taken - giveBack(player, ports.containers().insert(b.containers(), new ItemAmount(item, taken)));
        if (moved > 0) {
            overruleNextOpenRequestWithStack(c, b, new ItemAmount(item, moved));
            c.markDirty();
        }
        return moved;
    }

    /**
     * A player changed a hut container's content: the building's stuck requests that its hut can now serve (enough
     * matching stock beyond what its other requests reserved) get another chance.
     */
    public void onContainerChanged(BlockPos containerPos) {
        colonyAt(containerPos)
                .ifPresent(c -> c.buildings()
                        .owningContainer(containerPos)
                        .ifPresent(b -> c.requests()
                                .onColonyUpdate(r -> r.requester().equals(b.requesterId())
                                        && b.resolvers().stream().anyMatch(res -> res.canResolve(c.requests(), r)))));
    }

    /** AbstractBuilding.overruleNextOpenRequestWithStack. */
    private void overruleNextOpenRequestWithStack(Colony c, Building b, ItemAmount stack) {
        RequestManager m = c.requests();
        for (Request r : m.byRequester(b.requesterId())) {
            String resolver = m.resolverOf(r.token()).map(Resolver::resolverId).orElse("");
            boolean stuck = resolver.equals(PlayerResolver.ID) || resolver.equals(RetryingResolver.ID);
            if (stuck
                    && r.state().ordinal() < RequestState.COMPLETED.ordinal()
                    && r.requestable().matches(stack.item(), ctx.ports().catalog())) {
                m.overrule(r.token(), List.of(stack));
                return;
            }
        }
    }

    /** Returns {@code rest} to the player; returns its count (0 if none). */
    private int giveBack(UUID player, ItemAmount rest) {
        if (rest == null) {
            return 0;
        }
        ItemAmount lost = ctx.ports().playerInventory().give(player, rest);
        if (lost != null) {
            LOG.log(
                    System.Logger.Level.WARNING,
                    "Player {0} inventory full: {1} x {2} lost",
                    player,
                    lost.count(),
                    lost.item().id());
        }
        return rest.count();
    }

    // ---- Ticking and bodies ----

    public void tick() {
        for (Colony colony : colonies.values()) {
            colony.tick();
        }
    }

    public void onBodyLoaded(BodyId body, int colonyId, int citizenId) {
        Colony c = colonies.get(colonyId);
        if (c == null) {
            if (!lockedIds.contains(colonyId)) { // a colony we could not load still owns its bodies
                ctx.bodies().despawn(body);
            }
            return;
        }
        c.citizens().onBodyLoaded(body, citizenId);
    }

    public void onBodyUnloaded(BodyId body, int colonyId) {
        byId(colonyId).ifPresent(c -> c.citizens().onBodyUnloaded(body));
    }

    // ---- Persistence ----

    public void setStorage(ColonyStorage storage, MigrationChain migrations) {
        this.storage = storage;
        this.migrations = migrations;
    }

    /** False once listing the storage has failed: saves write nothing and founding is refused. */
    public boolean storageAvailable() {
        return !storageUnavailable;
    }

    public void loadAll() {
        try {
            reserveId(storage.highestIdEverUsed());
            for (int id : storage.colonyIds()) {
                loadOne(id);
            }
            // Second pass, once every initial square is claimed: a building never takes another colony's start.
            colonies.values().forEach(this::claimBuildings);
        } catch (IOException e) {
            storageUnavailable = true;
            LOG.log(
                    System.Logger.Level.ERROR,
                    "Cannot list colonies of " + ctx.world() + "; storage disabled until restart",
                    e);
        }
    }

    /** One colony's failure (corrupt file, I/O error, newer schema) must not stop the others from loading. */
    private void loadOne(int id) {
        try {
            Optional<JsonObject> raw = storage.load(id);
            if (raw.isEmpty()) {
                return;
            }
            JsonObject json = raw.get();
            int version = migrations.versionOf(json);
            if (version < migrations.current()) {
                storage.backupVersion(id, version, json.toString());
            }
            json = migrations.migrate(json);
            Colony colony = ColonySerializer.read(json, ctx, territory);
            register(colony);
            colony.clearDirty();
        } catch (SchemaTooNewException e) {
            lockedIds.add(id);
            LOG.log(System.Logger.Level.ERROR, "Colony " + id + " was saved by a newer HyColony; not loaded", e);
        } catch (IOException e) {
            lockedIds.add(id);
            LOG.log(System.Logger.Level.ERROR, "Colony " + id + " failed to load; file left untouched", e);
        } catch (RuntimeException e) {
            lockedIds.add(id);
            LOG.log(System.Logger.Level.ERROR, "Colony " + id + " failed to load; file left untouched", e);
        }
    }

    public void saveDirty() {
        for (Colony c : colonies.values()) {
            if (c.isDirty()) {
                save(c);
            }
        }
    }

    public void saveAll() {
        colonies.values().forEach(this::save);
    }

    private void save(Colony c) {
        if (storage == null || storageUnavailable || lockedIds.contains(c.id())) {
            return;
        }
        try {
            storage.save(c.id(), ColonySerializer.write(c).toString());
            c.clearDirty();
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Saving colony " + c.id() + " failed; will retry", e);
        }
    }

    // ---- Used by persistence ----

    int allocateId() {
        return nextId++;
    }

    void reserveId(int id) {
        nextId = Math.max(nextId, id + 1);
    }

    /** Claims the colony's initial square only; on load, {@link #claimBuildings} follows for every colony. */
    void register(Colony colony) {
        colonies.put(colony.id(), colony);
        reserveId(colony.id());
        territory.claimSquare(
                colony.id(), ClaimCell.of(colony.center()), ctx.config().initialColonySize());
        colony.markDirty();
    }

    /**
     * The territory is not saved: the cells finished buildings claimed are claimed again (level 0 claims none),
     * bounded and never stealing, as at completion. Loading changes nothing to save.
     */
    private void claimBuildings(Colony colony) {
        for (Building b : colony.buildings().all()) {
            if (b.level() > 0) {
                territory.claimSquareBounded(
                        colony.id(),
                        ClaimCell.of(b.position()),
                        ClaimRadius.of(b.type().id(), b.level()),
                        ClaimCell.of(colony.center()),
                        ctx.config().maxColonySize());
            }
        }
    }
}
