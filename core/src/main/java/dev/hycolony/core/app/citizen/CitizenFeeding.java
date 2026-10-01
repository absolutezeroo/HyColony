package dev.hycolony.core.app.citizen;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.food.HandFeeding;
import dev.hycolony.core.citizen.food.Meals;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import java.util.UUID;

/** A player using a citizen with food in hand (MC EntityCitizen.mobInteract): feeding it instead of its window. */
public final class CitizenFeeding {
    private final ColonyManager manager;

    public CitizenFeeding(ColonyManager manager) {
        this.manager = manager;
    }

    /**
     * Offers {@code food} to the citizen; any player may (MC checks no permission). Too soon after the last offer the
     * player is told MC's "I can't do that right now". Returns the outcome; the caller takes one food from the player
     * on FED and POISONED, and opens the window on NOT_FOOD (also for an unknown citizen).
     */
    public HandFeeding.Outcome offer(UUID player, int colonyId, int citizenId, ItemKey food) {
        Colony colony = manager.byId(colonyId).orElse(null);
        CitizenData citizen =
                colony == null ? null : colony.citizens().get(citizenId).orElse(null);
        if (colony == null || citizen == null) {
            return HandFeeding.Outcome.NOT_FOOD;
        }
        HandFeeding.Outcome outcome = HandFeeding.feed(colony, citizen, food);
        if (outcome == HandFeeding.Outcome.NOT_NOW) {
            manager.context().notifier().send(player, Msg.of("hycolony.citizen.notNow", citizen.name()));
        }
        if (outcome == HandFeeding.Outcome.FED) {
            colony.citizens().bodyOf(citizenId).ifPresent(body -> Meals.crumbs(colony, body));
        }
        return outcome;
    }
}
