package dev.hycolony.core.citizen.food;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.happiness.HappinessEvents;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.catalog.FoodCatalog;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;

/**
 * Eating one food (MC ItemStackUtils.consumeFood). Deviation from MC: the food's own effects (Hytale's heal and buffs)
 * are not applied, health follows saturation (HungerTicks.updateHealing), and no Hytale food leaves a bowl.
 */
public final class Meals {
    /** MC Player eye height (1.62), the citizen's model: where the crumbs fly. */
    private static final double EYE_HEIGHT = 1.62;

    private Meals() {}

    /** MC ItemParticleEffectMessage: food crumbs at the mouth of {@code body}; nothing for a body not loaded. */
    public static void crumbs(Colony colony, BodyId body) {
        colony.context()
                .bodies()
                .position(body)
                .ifPresent(
                        at -> colony.context().ports().effects().eating(new Vec3(at.x(), at.y() + EYE_HEIGHT, at.z())));
    }

    /**
     * MC consumeFood, once the caller took one {@code food} away: the citizen gains its value
     * ({@link FoodRules#foodValue}), and a great dish pleases it ({@link HappinessEvents#greatFood}); the colony is
     * marked dirty.
     */
    public static void eat(Colony colony, CitizenData citizen, ItemKey food) {
        FoodCatalog catalog = colony.context().ports().foods();
        citizen.hunger().increase(FoodRules.foodValue(catalog, food));
        if (FoodRules.tier(catalog, food) >= HappinessEvents.GREAT_FOOD_TIER) {
            HappinessEvents.greatFood(citizen);
        }
        colony.markDirty();
    }
}
