package dev.hycolony.core.app;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ColonyRefusal;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.BlockUse;
import dev.hycolony.core.colony.permission.Permissions;
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
     * Whether {@code player} may do {@code action} at {@code pos}; outside any colony everything is allowed. MC
     * Permissions.hasPermission(Player, Action): the player's rank, else the operator rank if they bypass.
     */
    public boolean isAllowed(UUID player, BlockPos pos, Action action) {
        return manager.colonyAt(pos)
                .map(c -> c.permissions().hasPermission(player, action)
                        || (bypassesPermissions(player) && Permissions.operatorRankHas(action)))
                .orElse(true);
    }

    /** Whether colony protection lets {@code player} do {@code action} at {@code pos}: allowed, or protection off. */
    public boolean allows(UUID player, BlockPos pos, Action action) {
        return !enabled() || isAllowed(player, pos, action);
    }

    /** MC checkEventCancelation: true when protection refuses {@code action} to {@code player} at {@code pos}, then told. */
    public boolean refuses(UUID player, BlockPos pos, Action action) {
        if (allows(player, pos, action)) {
            return false;
        }
        manager.colonyAt(pos).ifPresent(c -> ColonyRefusal.tell(c, player));
        return true;
    }

    /**
     * MC on(PlayerInteractEvent): true when protection refuses {@code player}'s {@code use} of the block at {@code pos}
     * (see {@link BlockUse#refused}), then told. False outside any colony.
     */
    public boolean refuses(UUID player, BlockPos pos, BlockUse use) {
        Optional<Colony> colony = manager.colonyAt(pos);
        if (colony.isEmpty()
                || use.refused(a -> isAllowed(player, pos, a), enabled()).isEmpty()) {
            return false;
        }
        ColonyRefusal.tell(colony.get(), player);
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

    /**
     * MC: a player in creative with at least operator level {@code PermissionEventBypassMinPermLevel}.
     *
     * <p>Deviation from MC: Hytale has no operator levels. Level 0 (MC: every player) lets any creative player
     * through; levels 1 to 4 need a Hytale operator in creative.
     */
    private boolean bypassesPermissions(UUID player) {
        return context().config().permissions().permissionEventBypassMinPermLevel() == 0
                ? context().players().isCreative(player)
                : context().players().isCreativeOperator(player);
    }

    private ColonyContext context() {
        return manager.context();
    }
}
