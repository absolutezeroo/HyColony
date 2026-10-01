package dev.hycolony.core.citizen.happiness;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyState;
import dev.hycolony.core.kernel.port.BodyId;

/** What the colony's events do to its citizens' happiness (MC Colony, EntityCitizen, ItemStackUtils, EntityAISleep). */
public final class HappinessEvents {
    /** MC EntityCitizen.hurt: an injury weighs 2 for one day. */
    private static final int DAMAGE_DAYS = 1;
    /** MC ItemStackUtils.consumeFood: a tier 3 dish pleases (2, weight 2) for five days. */
    private static final int GREAT_FOOD_DAYS = 5;
    /** MC IMinecoloniesFoodItem tier from which a dish is great. */
    public static final int GREAT_FOOD_TIER = 3;

    private HappinessEvents() {}

    /**
     * MC Colony.checkDayTime at nightfall: with a player in the colony (MC close subscribers), every citizen's
     * modifiers end their day ({@link CitizenHappiness#dayEnd}).
     */
    public static void onNightFall(Colony colony) {
        if (!ColonyState.hasPlayerInside(colony)) {
            return;
        }
        for (CitizenData c : colony.citizens().all()) {
            c.happiness().dayEnd(colony, c);
        }
        colony.markDirty();
    }

    /** MC EntityCitizen.hurt: an injury (not by fire or lightning, which the caller skips) for one day. */
    public static void hurt(CitizenData citizen) {
        citizen.happiness().add(new ExpirationModifier(HappinessIds.DAMAGE, 2.0, 0.0, DAMAGE_DAYS));
    }

    /**
     * MC EntityCitizen.hurt: {@code body} of {@code colony} took damage, neither fire nor lightning (the adapter skips
     * those, as MC returns before): its citizen is hurt ({@link #hurt(CitizenData)}). Nothing for a body of no citizen.
     */
    public static void hurt(Colony colony, BodyId body) {
        colony.citizens().citizenOf(body).ifPresent(citizen -> {
            hurt(citizen);
            colony.markDirty();
        });
    }

    /** MC ItemStackUtils.consumeFood: a great meal (a dish of tier 3 or more) for five days. */
    public static void greatFood(CitizenData citizen) {
        citizen.happiness().add(new ExpirationModifier(HappinessIds.HADGREATFOOD, 2.0, 2.0, GREAT_FOOD_DAYS));
    }

    /** MC EntityAISleep.findBedAndTryToSleep: each arrival at bed resets the "slept tonight" days. */
    public static void reachedBed(CitizenData citizen) {
        citizen.happiness().reset(HappinessIds.SLEPTTONIGHT);
    }
}
