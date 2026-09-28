package dev.hycolony.core.colony;

import dev.hycolony.core.kernel.ai.IState;
import java.util.UUID;

/** A colony's activity, and the rule that picks it. */
public enum ColonyState implements IState {
    ACTIVE,
    UNLOADED,
    INACTIVE;

    /**
     * MineColonies-equivalent activity rule (spec § 3.2): ACTIVE with a player inside the colony's territory, or with a
     * member online while its centre is loaded; UNLOADED with a member online elsewhere; INACTIVE otherwise.
     */
    static ColonyState of(Colony colony) {
        ColonyContext ctx = colony.context();
        boolean playerInside = false;
        boolean memberOnline = false;
        for (UUID player : ctx.players().onlineIn(ctx.world())) {
            if (ctx.players().position(player).map(colony::contains).orElse(false)) {
                playerInside = true;
            }
            if (colony.permissions().isMember(player)) {
                memberOnline = true;
            }
        }
        if (playerInside || (memberOnline && ctx.worldQuery().isLoaded(colony.center()))) {
            return ACTIVE;
        }
        return memberOnline ? UNLOADED : INACTIVE;
    }
}
