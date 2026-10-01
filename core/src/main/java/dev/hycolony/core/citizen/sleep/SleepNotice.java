package dev.hycolony.core.citizen.sleep;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.port.Msg;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * "All citizens are tucked into bed", once a night: MC CitizenManager.onCitizenSleep, re-armed at nightfall
 * (Colony.checkDayTime's updateCitizenSleep(false)). Its flag lives on the colony's CitizenManager, as MC's
 * areCitizensSleeping. Deviation from MC: MC leaves its guards out, HyColony has none.
 */
public final class SleepNotice {
    private SleepNotice() {}

    /** MC onCitizenSleep: once every citizen is asleep, tells the colony's online members, once until nightfall. */
    public static void onCitizenSleep(Colony colony) {
        for (CitizenData d : colony.citizens().all()) {
            if (!d.asleep()) {
                return;
            }
        }
        if (!colony.citizens().allAsleepAnnounced()) {
            for (UUID player : members(colony)) {
                if (colony.context().players().isOnline(player)) {
                    colony.context().notifier().send(player, Msg.of("hycolony.citizen.allAsleep"));
                }
            }
        }
        colony.citizens().setAllAsleepAnnounced(true);
    }

    /** MC updateCitizenSleep(false) at nightfall: the notice may come again. */
    public static void onNightFall(Colony colony) {
        colony.citizens().setAllAsleepAnnounced(false);
    }

    /** MC MessageUtils.sendTo(colony).forAllPlayers: the owner and the members allowed RECEIVE_MESSAGES. */
    private static Set<UUID> members(Colony colony) {
        Set<UUID> to = new LinkedHashSet<>();
        to.add(colony.permissions().owner());
        to.addAll(colony.permissions().members().keySet());
        to.removeIf(p -> !colony.permissions().hasPermission(p, Action.RECEIVE_MESSAGES));
        return to;
    }
}
