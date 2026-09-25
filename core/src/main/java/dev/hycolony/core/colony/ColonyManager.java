package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.ui.CitizenRow;
import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.ColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.kernel.persist.SchemaTooNewException;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.Deliverable;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.RequestState;
import dev.hycolony.core.request.RequestToken;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.StackRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    public ColonyContext context() { return ctx; }
    public TerritoryIndex territory() { return territory; }
    public Collection<Colony> all() { return Collections.unmodifiableCollection(colonies.values()); }
    public Optional<Colony> byId(int id) { return Optional.ofNullable(colonies.get(id)); }

    public Optional<Colony> colonyAt(BlockPos pos) {
        var id = territory.colonyAt(pos);
        return id.isPresent() ? byId(id.getAsInt()) : Optional.empty();
    }

    public Optional<Colony> ownedBy(UUID player) {
        return colonies.values().stream().filter(c -> c.permissions().owner().equals(player)).findFirst();
    }

    // ---- Hut placement (port of AbstractBlockHut.canPaste) ----

    public HutPlacement checkHutPlacement(UUID player, BlockPos pos, String buildingTypeId) {
        boolean isTownHall = BuildingTypes.TOWN_HALL.id().equals(buildingTypeId);
        Optional<Colony> colony = colonyAt(pos);
        if (colony.isEmpty()) {
            if (!isTownHall) {
                return new HutPlacement.Denied(Msg.of(ownedBy(player).isPresent() ? "hycolony.hut.tooFar" : "hycolony.hut.noTownHall"));
            }
            if (!storageAvailable()) {
                return new HutPlacement.Denied(Msg.of("hycolony.storage.unavailable"));
            }
            if (ownedBy(player).isPresent()) {
                return new HutPlacement.Denied(Msg.of("hycolony.colony.alreadyOwner"));
            }
            if (!territory.isFreeForNewColony(pos, ctx.config().initialColonySize(), ctx.config().minColonyDistance())) {
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
        Colony colony = new Colony(ctx, territory, allocateId(), name, p.pos(), Permissions.createDefault(player, p.playerName()));
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
                .filter(e -> e.getValue().pos().equals(pos)).map(Map.Entry::getKey).findFirst();
        owner.ifPresent(this::cancelFoundation);
        return owner;
    }

    public void placeHut(Colony colony, String buildingTypeId, BlockPos pos, int rotation) {
        BuildingType type = ctx.buildingTypes().byId(buildingTypeId).orElseThrow();
        Building building = Building.create(type, pos, rotation);
        colony.buildings().add(building);
        colony.log().add("buildingPlaced", colony.day(), type.id());
        colony.markDirty();
        ctx.bus().post(new ColonyEvents.BuildingPlaced(colony, building));
    }

    public void onHutRemoved(BlockPos pos) {
        colonyAt(pos).ifPresent(c -> c.buildings().remove(pos).ifPresent(b -> {
            c.log().add("buildingRemoved", c.day(), b.type().id());
            c.markDirty();
            ctx.bus().post(new ColonyEvents.BuildingRemoved(c, b));
        }));
    }

    // ---- Protection and management ----

    public boolean protectionEnabled() {
        return ctx.config().enableColonyProtection();
    }

    /** Outside any colony everything is allowed. */
    public boolean isAllowed(UUID player, BlockPos pos, Action action) {
        return colonyAt(pos).map(c -> c.permissions().hasPermission(player, action)).orElse(true);
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
        if (!c.permissions().hasPermission(player, Action.ACCESS_HUTS)) {
            ctx.notifier().send(player, Msg.of("hycolony.permission.denied", c.name()));
            return;
        }
        ctx.ui().showTownHall(player, townHallView(c, player));
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
        return new TownHallView(c.id(), c.name(), c.permissions().ownerName(), c.day(), rows,
                c.permissions().rankOf(viewer).isColonyManager());
    }

    private CitizenRow row(Colony c, CitizenData d) {
        boolean present = c.citizens().bodyOf(d.id()).map(ctx.bodies()::isAlive).orElse(false);
        String status = !present ? "absent"
                : c.citizens().aiState(d.id()).map(s -> s.name().toLowerCase(Locale.ROOT)).orElse("idle");
        return new CitizenRow(d.name(), d.gender(), status);
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

    // ---- Requests: the player's "Fournir" / "Ajouter" ----

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
        Optional<ItemKey> item = wanted instanceof StackRequest s ? Optional.of(s.item())
                : ports.playerInventory().contents(player).keySet().stream()
                        .filter(k -> wanted.matches(k, ports.catalog())).findFirst();
        if (item.isEmpty()) {
            return false;
        }
        int n = ports.playerInventory().take(player, item.get(), wanted.count());
        if (n <= 0) {
            return false;
        }
        ItemAmount taken = new ItemAmount(item.get(), n);
        Optional<CitizenData> citizen = req.citizenId() == -1 ? Optional.empty() : c.citizens().get(req.citizenId());
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
        c.requests().overrule(token, List.of(new ItemAmount(item.get(), moved)));
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

    /** A player changed a hut container's content: the building's stuck requests get another chance. */
    public void onContainerChanged(BlockPos containerPos) {
        colonyAt(containerPos).ifPresent(c -> c.buildings().owningContainer(containerPos).ifPresent(b ->
                c.requests().onColonyUpdate(r -> r.requester().equals(b.requesterId()))));
    }

    /** AbstractBuilding.overruleNextOpenRequestWithStack. */
    private void overruleNextOpenRequestWithStack(Colony c, Building b, ItemAmount stack) {
        RequestManager m = c.requests();
        for (Request r : m.byRequester(b.requesterId())) {
            String resolver = m.resolverOf(r.token()).map(Resolver::resolverId).orElse("");
            boolean stuck = resolver.equals(PlayerResolver.ID) || resolver.equals(RetryingResolver.ID);
            if (stuck && r.state().ordinal() < RequestState.COMPLETED.ordinal()
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
            LOG.log(System.Logger.Level.WARNING, "Player {0} inventory full: {1} x {2} lost", player, lost.count(),
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
        } catch (IOException e) {
            storageUnavailable = true;
            LOG.log(System.Logger.Level.ERROR, "Cannot list colonies of " + ctx.world() + "; storage disabled until restart", e);
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

    void register(Colony colony) {
        colonies.put(colony.id(), colony);
        reserveId(colony.id());
        territory.claimSquare(colony.id(), ClaimCell.of(colony.center()), ctx.config().initialColonySize());
        colony.markDirty();
    }
}
