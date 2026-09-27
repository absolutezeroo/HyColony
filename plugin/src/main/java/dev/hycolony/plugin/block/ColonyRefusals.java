package dev.hycolony.plugin.block;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hycolony.core.colony.permission.DenialNotices;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.adapter.HytaleNotifier;

/**
 * Tells a player that a colony refused his action (MC ColonyPermissionEventHandler.cancelEvent), no more than the core
 * {@link DenialNotices} allow. Shared by every protection system. World thread only.
 */
final class ColonyRefusals {
    private final DenialNotices notices = new DenialNotices();

    /** Sends the denial message for {@code colonyName}, unless {@code player} was told in the last 10 seconds. */
    void tell(WorldRuntime rt, PlayerRef player, String colonyName) {
        if (notices.shouldTell(player.getUuid(), rt.manager().context().clock().currentTick())) {
            player.sendMessage(HytaleNotifier.toMessage(Msg.of("hycolony.permission.denied", colonyName)));
        }
    }
}
