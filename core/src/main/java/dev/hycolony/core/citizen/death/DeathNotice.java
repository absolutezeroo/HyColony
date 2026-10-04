package dev.hycolony.core.citizen.death;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Directions;
import dev.hycolony.core.kernel.port.Msg;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The colony's message about a death (MC EntityCitizen.die: the death message, where it died from the colony's
 * centre, and who will mourn), sent forManagers.
 */
final class DeathNotice {
    private DeathNotice() {}

    /**
     * Tells the colony's important players that {@code dead} died at {@code at} of {@code cause}. Deviation from MC
     * (Hytale world): Minecraft's death message (getCombatTracker().getDeathMessage()) → our text per Hytale damage
     * cause, or naming the killer (Hytale's own death texts speak to the player: "You were killed…"). Deviation from
     * MC: no hover with the position and distance (Hytale messages have none).
     */
    static void send(Colony colony, CitizenData dead, BlockPos at, DeathCause cause) {
        String where = "%hycolony.ui.direction.long." + Directions.of(colony.center(), at);
        Msg msg = cause.killer()
                .map(killer -> Msg.of("hycolony.citizen.diedKilledBy", dead.name(), killer, where))
                .orElseGet(() -> Msg.of(
                        "hycolony.citizen.died", dead.name(), "%hycolony.citizen.deathCause." + cause.cause(), where));
        for (UUID player : importantPlayers(colony)) {
            colony.context().notifier().send(player, msg);
        }
    }

    /**
     * MC Colony.getImportantMessageEntityPlayers: the owner and members allowed RECEIVE_MESSAGES, and the colony
     * managers by rank. Deviation from MC: MC reaches only the players close to the colony (its subscribers), as every
     * HyColony colony message to its members does (SleepNotice); the notifier skips offline players.
     */
    private static Set<UUID> importantPlayers(Colony colony) {
        Set<UUID> to = new LinkedHashSet<>();
        to.add(colony.permissions().owner());
        to.addAll(colony.permissions().members().keySet());
        to.removeIf(p -> !colony.permissions().hasPermission(p, Action.RECEIVE_MESSAGES)
                && !colony.permissions().rankOf(p).isColonyManager());
        return to;
    }
}
