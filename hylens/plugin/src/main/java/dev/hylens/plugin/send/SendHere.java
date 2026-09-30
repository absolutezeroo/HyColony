package dev.hylens.plugin.send;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.api.Actor;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.Pos;
import dev.hylens.core.menu.ActionReport;
import dev.hylens.core.menu.Menus;
import dev.hylens.core.send.SendTarget;
import dev.hylens.core.watch.Watches;
import dev.hylens.plugin.HyColonyAccess;
import java.util.Optional;
import java.util.UUID;

/**
 * "Send here" (spec 2026-09-30, § 6.6): asks HyColony, in the operator's name, to walk a citizen to a cell. HyColony
 * lets an operator or a manager of the colony do it. World thread.
 */
public final class SendHere {
    private SendHere() {}

    /** Walks {@code citizen} to {@code cell} as {@code operator}; the text of HyColony's answer. */
    private static ApiText walk(ColonyWorld colonies, CitizenRef citizen, UUID operator, Pos cell) {
        return ActionReport.text(colonies.debug().walkTo(new Actor.Player(operator), citizen, cell));
    }

    /**
     * Walks the citizen {@code operator} watches in {@code world}, else the one chosen in their menu, to {@code cell};
     * the chat line telling what came of it, or why no citizen was sent.
     */
    public static ApiText watchedOrChosen(World world, Watches watches, Menus menus, UUID operator, Pos cell) {
        Optional<ColonyWorld> colonies = HyColonyAccess.world(world);
        if (colonies.isEmpty()) {
            return ApiText.of("hylens.notRunning");
        }
        Optional<CitizenRef> who = SendTarget.who(
                world.getName(),
                watches.watched(operator),
                menus.state(operator).citizen());
        if (who.isEmpty()) {
            return ApiText.of("hylens.send.noneChosen");
        }
        String at = cell.x() + " " + cell.y() + " " + cell.z();
        return ApiText.of("hylens.send.walk", at, walk(colonies.get(), who.get(), operator, cell));
    }
}
