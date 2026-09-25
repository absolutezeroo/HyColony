package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.ui.CitizenRow;
import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** All colonies of one world. Entry point the plugin calls, always on the world thread. */
public final class ColonyManager {
    public static final int MAX_NAME_LENGTH = 32;

    private record PendingFoundation(String playerName, BlockPos pos, int rotation) {}

    private final ColonyContext ctx;
    private final TerritoryIndex territory = new TerritoryIndex();
    private final Map<Integer, Colony> colonies = new LinkedHashMap<>();
    private final Map<UUID, PendingFoundation> pending = new HashMap<>();
    private int nextId = 1;

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
            pending.remove(player);
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
        return Optional.of(colony);
    }

    /** Returns where the unconfirmed town hall stands, so the adapter can remove it. */
    public Optional<BlockPos> cancelFoundation(UUID player) {
        PendingFoundation p = pending.remove(player);
        ctx.ui().close(player);
        return Optional.ofNullable(p).map(PendingFoundation::pos);
    }

    public void onPlayerLeft(UUID player) {
        pending.remove(player);
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

    public void deleteColony(int colonyId) {
        Colony c = colonies.remove(colonyId);
        if (c == null) {
            return;
        }
        c.citizens().despawnAll();
        territory.releaseAll(colonyId);
        ctx.bus().post(new ColonyEvents.ColonyDeleted(colonyId));
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
            ctx.bodies().despawn(body);
            return;
        }
        c.citizens().onBodyLoaded(body, citizenId);
    }

    public void onBodyUnloaded(BodyId body, int colonyId) {
        byId(colonyId).ifPresent(c -> c.citizens().onBodyUnloaded(body));
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
