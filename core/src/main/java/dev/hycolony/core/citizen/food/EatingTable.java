package dev.hycolony.core.citizen.food;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The meal itself of {@link EatAI}: the slot of the food chosen, the bites and the foods eaten in this meal, noted in
 * the citizen's history as MC does. Port of MC EntityAIEatTask.eat's state ({@code foodSlot}, {@code eatenFood}).
 */
final class EatingTable {
    /** The outcome of one transition of EAT. */
    enum Bite {
        /** Still chewing (fewer than {@link EatAI#REQUIRED_TIME_TO_EAT} bites). */
        CHEWING,
        /** One food eaten, more to come: the bites start again. */
        AGAIN,
        /** The chosen food may not be eaten: it looks for food again. */
        NOT_EDIBLE,
        /** Full, or nothing left of that food: the meal is over. */
        FULL
    }

    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final CitizenBodies bodies;
    private final Set<ItemKey> eaten = new LinkedHashSet<>();
    private int foodSlot = -1;
    /** MC waitingTicks: shared by the waits and the bites, as in MC (a citizen who waited long eats at once). */
    private int waiting;

    EatingTable(Colony colony, CitizenData data, BodyId body) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.bodies = colony.context().bodies();
    }

    /** MC hasFood: keeps {@code slot} as the food to eat; false (keeping the old one) for -1. */
    boolean choose(int slot) {
        if (slot == -1) {
            return false;
        }
        foodSlot = slot;
        return true;
    }

    /**
     * One more wait (MC {@code ++waitingTicks > MINUTES_WAITING_TIME * SECONDS_A_MINUTE}): true once it waited
     * {@link DiningVisit#WAITING_TRANSITIONS}, the count then starting again.
     */
    boolean waitedTooLong() {
        if (++waiting > DiningVisit.WAITING_TRANSITIONS) {
            waiting = 0;
            return true;
        }
        return false;
    }

    /**
     * MC eat: holds and bites the chosen food (animation and crumbs); at the {@link EatAI#REQUIRED_TIME_TO_EAT}th bite
     * it eats one (the first of a meal at a dining hall noted at once), then goes on while not full and that slot still
     * holds food, else notes the meal's foods, is "just ate", and is done.
     */
    Bite bite(boolean atRestaurant) {
        ItemAmount stack = data.inventory().slot(foodSlot).orElse(null);
        if (stack == null || !FoodRules.canEat(colony, data, stack.item())) {
            return Bite.NOT_EDIBLE;
        }
        bodies.setHeldItem(body, Optional.of(stack.item())); // MC setItemInHand: the held slot is left as it is
        bodies.playAnimation(body, BodyAnimation.EAT);
        Meals.crumbs(colony, body);
        if (++waiting < EatAI.REQUIRED_TIME_TO_EAT) {
            return Bite.CHEWING;
        }
        swallow(stack, atRestaurant);
        if (data.saturation() < CitizenData.MAX_SATURATION
                && data.inventory().slot(foodSlot).isPresent()) {
            waiting = 0;
            return Bite.AGAIN;
        }
        endMeal();
        return Bite.FULL;
    }

    /** Eats one of {@code stack} (MC: the first food of a meal at a dining hall goes in its history at once). */
    private void swallow(ItemAmount stack, boolean atRestaurant) {
        ItemKey food = stack.item();
        if (eaten.isEmpty() && atRestaurant) {
            data.hunger().history().add(food);
        }
        eaten.add(food);
        data.inventory()
                .set(foodSlot, stack.count() > 1 ? Optional.of(stack.withCount(stack.count() - 1)) : Optional.empty());
        Meals.eat(colony, data, food);
        bodies.setHeldItem(body, Optional.empty()); // MC: the entity's hand only
    }

    /** MC: the meal's foods go in its history, but not twice in a row; it is "just ate". */
    private void endMeal() {
        FoodHistory history = data.hunger().history();
        for (ItemKey item : eaten) {
            if (!history.last().map(item::equals).orElse(false)) {
                history.add(item);
            }
        }
        eaten.clear();
        data.hunger().setJustAte(true);
    }

    /** MC reset: no food chosen, an empty hand, the meal and the waits forgotten. */
    void clear() {
        foodSlot = -1;
        waiting = 0;
        eaten.clear();
        bodies.setHeldItem(body, Optional.empty()); // MC: the entity's hand only
    }
}
