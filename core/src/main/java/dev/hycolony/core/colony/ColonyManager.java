package dev.hycolony.core.colony;

import dev.hycolony.core.colony.action.CitizenInventoryActions;
import dev.hycolony.core.colony.action.ColonyAdministration;
import dev.hycolony.core.colony.action.HutActions;
import dev.hycolony.core.colony.action.RequestActions;
import dev.hycolony.core.colony.action.WorkOrderActions;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.colony.view.ColonyWindows;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.Explosions;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * All colonies of one world, and the entry point the plugin calls, always on the world thread. It owns the colonies'
 * lifecycle (registration, ticking, bodies, deletion); founding, persistence, the windows and the players' actions
 * are its collaborators, reached through its accessors.
 */
public final class ColonyManager {
    public static final int MAX_NAME_LENGTH = 32;

    private final ColonyContext ctx;
    private final TerritoryIndex territory = new TerritoryIndex();
    private final Map<Integer, Colony> colonies = new LinkedHashMap<>();
    private int nextId = 1;

    private final ColonyPersistence persistence;
    private final ColonyWindows windows;
    private final HutActions huts;
    private final ColonyFoundation foundation;
    private final WorkOrderActions workOrders;
    private final RequestActions requestActions;
    private final ColonyAdministration administration;
    private final CitizenInventoryActions citizenInventories;

    public ColonyManager(ColonyContext ctx) {
        this.ctx = ctx;
        this.persistence = new ColonyPersistence(this);
        this.windows = new ColonyWindows(this);
        this.huts = new HutActions(this, windows);
        this.foundation = new ColonyFoundation(this, huts);
        this.workOrders = new WorkOrderActions(this, windows);
        this.requestActions = new RequestActions(this);
        this.administration = new ColonyAdministration(this, windows);
        this.citizenInventories = new CitizenInventoryActions(this);
    }

    public ColonyContext context() {
        return ctx;
    }

    public TerritoryIndex territory() {
        return territory;
    }

    public ColonyPersistence persistence() {
        return persistence;
    }

    public ColonyFoundation foundation() {
        return foundation;
    }

    public ColonyWindows windows() {
        return windows;
    }

    public HutActions huts() {
        return huts;
    }

    public WorkOrderActions workOrders() {
        return workOrders;
    }

    /** The players' "Fournir" / "Ajouter" and hut container changes. */
    public RequestActions requestActions() {
        return requestActions;
    }

    public ColonyAdministration administration() {
        return administration;
    }

    /** Opening a citizen's inventory and the player's moves in it. */
    public CitizenInventoryActions citizenInventories() {
        return citizenInventories;
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

    public boolean protectionEnabled() {
        return ctx.config().permissions().enableColonyProtection();
    }

    /**
     * Whether {@code player} may do {@code action} at {@code pos}; outside any colony everything is allowed. MC
     * Permissions.hasPermission(Player, Action): the player's rank, else the operator rank if they bypass.
     */
    public boolean isAllowed(UUID player, BlockPos pos, Action action) {
        return colonyAt(pos)
                .map(c -> c.permissions().hasPermission(player, action)
                        || bypassesPermissions(player) && Permissions.operatorRankHas(action))
                .orElse(true);
    }

    /**
     * Whether an explosion must leave the block at {@code pos} intact: inside a colony, unless the config is
     * DAMAGE_EVERYTHING. MC ColonyPermissionEventHandler.on(ExplosionEvent.Detonate), block part; like MC it ignores
     * EnableColonyProtection.
     */
    public boolean explosionSparesBlock(BlockPos pos) {
        return ctx.config().permissions().turnOffExplosionsInColonies() != Explosions.DAMAGE_EVERYTHING
                && colonyAt(pos).isPresent();
    }

    /**
     * MC: a player in creative with at least operator level {@code PermissionEventBypassMinPermLevel}.
     *
     * <p>Deviation from MC: Hytale has no operator levels. Level 0 (MC: every player) lets any creative player
     * through; levels 1 to 4 need a Hytale operator in creative.
     */
    private boolean bypassesPermissions(UUID player) {
        return ctx.config().permissions().permissionEventBypassMinPermLevel() == 0
                ? ctx.players().isCreative(player)
                : ctx.players().isCreativeOperator(player);
    }

    public void tick() {
        for (Colony colony : colonies.values()) {
            colony.tick();
        }
    }

    public void onBodyLoaded(BodyId body, int colonyId, int citizenId) {
        Colony c = colonies.get(colonyId);
        if (c == null) {
            if (!persistence.isLocked(colonyId)) { // a colony we could not load still owns its bodies
                ctx.bodies().despawn(body);
            }
            return;
        }
        c.citizens().onBodyLoaded(body, citizenId);
    }

    public void onBodyUnloaded(BodyId body, int colonyId) {
        byId(colonyId).ifPresent(c -> c.citizens().onBodyUnloaded(body));
    }

    /** Archives before freeing anything; if archiving fails the colony stays registered. */
    public boolean deleteColony(int colonyId) {
        Colony c = colonies.get(colonyId);
        if (c == null || !persistence.archive(colonyId)) {
            return false;
        }
        colonies.remove(colonyId);
        c.citizens().despawnAll();
        territory.releaseAll(colonyId);
        ctx.bus().post(new ColonyEvents.ColonyDeleted(colonyId));
        return true;
    }

    int allocateId() {
        return nextId++;
    }

    void reserveId(int id) {
        nextId = Math.max(nextId, id + 1);
    }

    /** Claims the colony's initial square only; on load, the buildings' claims follow for every colony. */
    void register(Colony colony) {
        colonies.put(colony.id(), colony);
        reserveId(colony.id());
        territory.claimSquare(
                colony.id(),
                ClaimCell.of(colony.center()),
                ctx.config().claims().initialColonySize());
        colony.markDirty();
    }
}
