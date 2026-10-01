package dev.hycolony.core.colony;

import dev.hycolony.core.kernel.port.Msg;
import java.util.UUID;

/** Telling a player that a colony refused his action (MC ColonyPermissionEventHandler.cancelEvent). */
public final class ColonyRefusal {
    private ColonyRefusal() {}

    /**
     * Sends "permission denied" for {@code colony} to {@code player}, unless that colony told him less than 10 seconds
     * ago (its {@link dev.hycolony.core.colony.permission.DenialNotices}, on its world's clock).
     */
    public static void tell(Colony colony, UUID player) {
        ColonyContext ctx = colony.context();
        if (colony.permissions().denials().shouldTell(player, ctx.clock().currentTick())) {
            ctx.notifier().send(player, Msg.of("hycolony.permission.denied", colony.name()));
        }
    }

    /**
     * MC AbstractColonyServerMessage: a colony action refused for a missing right tells the player every time, with
     * MC's TOOL_PERMISSION_SCEPTER_PERMISSION_DENY.
     */
    public static void tellNoPermission(Colony colony, UUID player) {
        colony.context().notifier().send(player, Msg.of("hycolony.permission.toolDenied"));
    }
}
