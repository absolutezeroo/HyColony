package dev.hycolony.core.app;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ColonyRefusal;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.BlockUse;
import dev.hycolony.core.colony.permission.PermissionEvents;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.Explosions;
import java.util.Optional;
import java.util.UUID;

/**
 * What players may do inside the colonies (MC ColonyPermissionEventHandler over Permissions.hasPermission): the rank
 * check with the creative operator bypass, EnableColonyProtection and TurnOffExplosionsInColonies. A refused player is
 * told, at most every 10 seconds per colony ({@link ColonyRefusal}).
 */
public final class ColonyProtection {
    private final ColonyManager manager;

    ColonyProtection(ColonyManager manager) {
        this.manager = manager;
    }

    /** MC EnableColonyProtection. */
    public boolean enabled() {
        return context().config().permissions().enableColonyProtection();
    }

    /**
     * Whether a hostile creature may appear naturally at {@code pos}: not in any colony's territory.
     *
     * <p>Deviation from MC (asked for): MC refuses it only inside its buildings (EventHandler.on(PositionCheck),
     * isInBuilding) and relies on Minecraft's light elsewhere; Hytale's surface monsters have no light condition.
     */
    public boolean allowsHostileSpawn(BlockPos pos) {
        return manager.colonyAt(pos).isEmpty();
    }

    /**
     * Whether {@code player} may do {@code action} at {@code pos}; outside any colony everything is allowed. MC
     * Permissions.hasPermission(Player, Action): the player's rank, else the operator rank if they bypass.
     */
    public boolean isAllowed(UUID player, BlockPos pos, Action action) {
        return manager.colonyAt(pos)
                .map(c -> ColonyAccess.allows(c, player, action))
                .orElse(true);
    }

    /** Whether colony protection lets {@code player} do {@code action} at {@code pos}: allowed, or protection off. */
    public boolean allows(UUID player, BlockPos pos, Action action) {
        return !enabled() || isAllowed(player, pos, action);
    }

    /**
     * MC checkEventCancelation: true when protection refuses {@code action} to {@code player} at {@code pos}; the
     * player is then told.
     */
    public boolean refuses(UUID player, BlockPos pos, Action action) {
        if (allows(player, pos, action)) {
            return false;
        }
        manager.colonyAt(pos).ifPresent(c -> {
            ColonyRefusal.tell(c, player);
            log(c, player, action, pos);
        });
        return true;
    }

    /** MC cancelEvent: the refusal joins the town hall's permission events, if the colony has its town hall. */
    private void log(Colony c, UUID player, Action action, BlockPos pos) {
        if (c.buildings().townHall().isEmpty()) {
            return;
        }
        String name = context().players().name(player).orElse("");
        if (c.permissions().events().add(new PermissionEvents.Event(Optional.of(player), name, action, pos))) {
            c.markDirty();
        }
    }

    /**
     * MC on(PlayerInteractEvent): true when protection refuses {@code player}'s {@code use} of the block at {@code pos}
     * (see {@link BlockUse#refused}), then told. False outside any colony.
     */
    public boolean refuses(UUID player, BlockPos pos, BlockUse use) {
        Optional<Colony> colony = manager.colonyAt(pos);
        Optional<Action> refused = colony.flatMap(c -> use.refused(a -> isAllowed(player, pos, a), enabled()));
        if (colony.isEmpty() || refused.isEmpty()) {
            return false;
        }
        ColonyRefusal.tell(colony.get(), player);
        log(colony.get(), player, refused.get(), pos);
        return true;
    }

    /**
     * Whether an explosion must leave the block at {@code pos} intact: inside a colony, unless the config is
     * DAMAGE_EVERYTHING. MC ColonyPermissionEventHandler.on(ExplosionEvent.Detonate), block part; like MC it ignores
     * EnableColonyProtection.
     */
    public boolean explosionSparesBlock(BlockPos pos) {
        return context().config().permissions().turnOffExplosionsInColonies() != Explosions.DAMAGE_EVERYTHING
                && manager.colonyAt(pos).isPresent();
    }

    private ColonyContext context() {
        return manager.context();
    }
}
