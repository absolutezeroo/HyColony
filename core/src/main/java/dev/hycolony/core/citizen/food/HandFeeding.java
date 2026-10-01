package dev.hycolony.core.citizen.food;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Optional;

/**
 * A player feeding a citizen by hand (MC EntityCitizen.mobInteract with food). Any food will do, raw or not, full or
 * not (MC ISFOOD). Deviation from MC: a poisonous food makes no one sick (no disease), and a child's cookie-only rule
 * is not ported (no children).
 */
public final class HandFeeding {
    /** MC eatFoodInteraction: ticks before the next interaction. */
    static final int FED_COOLDOWN_TICKS = 100;
    /** MC: ticks before the next interaction after a poisonous food. */
    static final int POISON_COOLDOWN_TICKS = 20 * 20;

    /** What became of the food offered. */
    public enum Outcome {
        /** Not food: the citizen's window opens as usual. */
        NOT_FOOD,
        /** Too soon after the last one (MC WARNING_INTERACTION_CANT_DO_NOW): nothing taken. */
        NOT_NOW,
        /** Eaten: one taken. */
        FED,
        /** Poisonous: one taken, nothing gained. */
        POISONED
    }

    private HandFeeding() {}

    /** Feeds {@code food} to {@code citizen}; the caller takes one from the player unless NOT_FOOD or NOT_NOW. */
    public static Outcome feed(Colony colony, CitizenData citizen, ItemKey food) {
        Optional<FoodInfo> info = colony.context().ports().catalog().food(food);
        if (info.isEmpty()) {
            return Outcome.NOT_FOOD;
        }
        long now = colony.context().clock().currentTick();
        CitizenHunger hunger = citizen.hunger();
        if (now < hunger.interactionCooldownUntil()) {
            return Outcome.NOT_NOW;
        }
        if (info.get().poisonous()) {
            hunger.setInteractionCooldownUntil(now + POISON_COOLDOWN_TICKS);
            return Outcome.POISONED;
        }
        hunger.history().add(food);
        Meals.eat(colony, citizen, food);
        hunger.setInteractionCooldownUntil(now + FED_COOLDOWN_TICKS);
        return Outcome.FED;
    }
}
