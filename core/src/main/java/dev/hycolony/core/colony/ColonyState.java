package dev.hycolony.core.colony;

import dev.hycolony.core.kernel.ai.IState;
import java.util.UUID;

/** A colony's activity, and the rule that picks it. */
public enum ColonyState implements IState {
    ACTIVE,
    UNLOADED,
    INACTIVE;

    /**
     * MC ColonyStateMachine over its important players (the colony managers online, EventHandler.onPlayerEnterWorld):
     * ACTIVE with a player inside the colony's territory, or with a manager online while its centre is loaded;
     * UNLOADED with a manager online elsewhere; INACTIVE otherwise. Deviation from MC: MC wants more than 40 chunks
     * loaded, here the centre's chunk (Hytale chunks are 32 blocks; spec SP0 § 3.2), and only this world's players;
     * the rank is read at each evaluation, where MC keeps the important players it found at login until logout.
     */
    static ColonyState of(Colony colony) {
        ColonyContext ctx = colony.context();
        boolean playerInside = false;
        boolean managerOnline = false;
        for (UUID player : ctx.players().onlineIn(ctx.world())) {
            if (ctx.players().position(player).map(colony::contains).orElse(false)) {
                playerInside = true;
            }
            if (colony.permissions().rankOf(player).isColonyManager()) {
                managerOnline = true;
            }
        }
        if (playerInside || (managerOnline && ctx.worldQuery().isLoaded(colony.center()))) {
            return ACTIVE;
        }
        return managerOnline ? UNLOADED : INACTIVE;
    }
}
