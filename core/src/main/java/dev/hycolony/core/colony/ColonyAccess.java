package dev.hycolony.core.colony;

import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.Permissions;
import java.util.UUID;

/**
 * MC Permissions.hasPermission(Player, Action): what a player may do in a colony, their rank or, for a player who
 * bypasses the permissions, the operator rank. Every check of a player acting goes through here; a message sent to
 * the members (RECEIVE_MESSAGES) reads their rank only.
 */
public final class ColonyAccess {
    private ColonyAccess() {}

    /** Whether {@code player} may do {@code action} in {@code colony}. */
    public static boolean allows(Colony colony, UUID player, Action action) {
        return colony.permissions().hasPermission(player, action)
                || (bypassesPermissions(colony.context(), player) && Permissions.operatorRankHas(action));
    }

    /**
     * MC: a player in creative with at least operator level {@code PermissionEventBypassMinPermLevel}.
     *
     * <p>Deviation from MC: Hytale has no operator levels. Level 0 (MC: every player) lets any creative player
     * through; levels 1 to 4 need a Hytale operator in creative.
     */
    private static boolean bypassesPermissions(ColonyContext context, UUID player) {
        return context.config().permissions().permissionEventBypassMinPermLevel() == 0
                ? context.players().isCreative(player)
                : context.players().isCreativeOperator(player);
    }
}
