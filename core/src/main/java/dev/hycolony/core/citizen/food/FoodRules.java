package dev.hycolony.core.citizen.food;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.Optional;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** What citizens may eat and what it gives them. Port of MC FoodUtils, research effects at 0. */
public final class FoodRules {
    /** MC Constants.MAX_BUILDING_LEVEL. */
    static final int MAX_BUILDING_LEVEL = 5;
    /** MC FoodUtils.canEatLevel: below this home level any food will do. */
    private static final int PICKY_LEVEL = 3;
    /** MC FoodUtils.getFoodValue: a prepared dish counts twice. */
    private static final double DISH_BONUS = 2.0;

    private FoodRules() {}

    /** MC FoodUtils.EDIBLE: a food that does not cook into something else (raw food is not eaten). */
    public static boolean edible(ItemCatalog catalog, ItemKey item) {
        return catalog.food(item).isPresent() && catalog.cooked(item).isEmpty();
    }

    /**
     * MC FoodUtils.canEatLevel: below home level 3 any food; from there a nutrition of at least the level + 1. Crops
     * (MC ItemCrop) need no rule here: every Hytale crop cooks, so none is {@link #edible}.
     */
    public static boolean canEatLevel(ItemCatalog catalog, ItemKey item, int homeLevel) {
        Optional<FoodInfo> food = catalog.food(item);
        if (homeLevel < PICKY_LEVEL) {
            return food.isPresent();
        }
        return food.map(f -> f.nutrition() >= homeLevel + 1).orElse(false);
    }

    /**
     * MC FoodUtils.canEat: edible, not poisonous, good enough for a home of {@code homeLevel} (0 without one) and let
     * go by the work hut ({@code workAllows}, MC IBuilding.canEat; always true without one).
     */
    public static boolean canEat(ItemCatalog catalog, ItemKey item, int homeLevel, Predicate<ItemKey> workAllows) {
        return edible(catalog, item)
                && !catalog.food(item).map(FoodInfo::poisonous).orElse(true)
                && canEatLevel(catalog, item, homeLevel)
                && workAllows.test(item);
    }

    /**
     * MC FoodUtils.canEat(stack, home, work) for {@code citizen}: its home's level and its work hut's
     * {@link EatingRule}s (none without a work hut).
     */
    public static boolean canEat(Colony colony, CitizenData citizen, ItemKey item) {
        return canEat(
                colony.context().ports().catalog(),
                item,
                HungerTicks.homeLevel(colony, citizen),
                workAllows(colony, citizen.workBuilding()));
    }

    /** What the hut at {@code work} lets its workers eat (MC IBuilding.canEat); everything without a hut there. */
    public static Predicate<ItemKey> workAllows(Colony colony, @Nullable BlockPos work) {
        Building hut = work == null ? null : colony.buildings().at(work).orElse(null);
        if (hut == null) {
            return _ -> true;
        }
        return item -> hut.modules().values().stream()
                .noneMatch(m -> m instanceof EatingRule rule && !rule.canEat(colony, hut, item));
    }

    /** MC AbstractBuilding.keepFood: whether {@code hut} keeps food for its workers (no rule says otherwise). */
    public static boolean keepsFood(Building hut) {
        return hut.modules().values().stream().noneMatch(m -> m instanceof EatingRule rule && !rule.keepsFood());
    }

    /** MC FoodUtils.getFoodValue: the saturation eating one gives, a dish counting twice; 0 for no food. */
    public static double foodValue(ItemCatalog catalog, ItemKey item) {
        return catalog.food(item)
                .map(f -> f.nutrition() * (f.isDish() ? DISH_BONUS : 1.0))
                .orElse(0.0);
    }

    /**
     * MC FoodUtils.getFoodTier: the dish tier, 0 for an ordinary food. Deviation from MC: MC also gives tier 1 to an
     * ordinary food of nutrition 12 and saturation 0.8; no Hytale food of the table comes close and saturation
     * modifiers are not ported.
     */
    public static int tier(ItemCatalog catalog, ItemKey item) {
        return catalog.food(item).map(FoodInfo::tier).orElse(0);
    }

    /** Whether {@code item} is a prepared dish (MC IMinecoloniesFoodItem). */
    public static boolean isDish(ItemCatalog catalog, ItemKey item) {
        return catalog.food(item).map(FoodInfo::isDish).orElse(false);
    }

    /** MC FoodUtils.getBuildingLevelForFood: the highest home level the food still feeds, 2 to 5. */
    public static int buildingLevelForFood(ItemCatalog catalog, ItemKey item) {
        int nutrition = catalog.food(item).map(FoodInfo::nutrition).orElse(0);
        return Math.max(2, Math.min(nutrition - 1, MAX_BUILDING_LEVEL));
    }

    /** MC FoodUtils.getMinFoodQualityRequirement: dishes wanted among the last meals at this home level. */
    public static int minQuality(int homeLevel) {
        return Math.max(0, homeLevel - 2);
    }

    /** MC FoodUtils.getMinFoodDiversityRequirement: different foods wanted among the last meals. */
    public static int minDiversity(int homeLevel) {
        return homeLevel;
    }

    /** MC FoodUtils.computeSaturationConsumptionFactor: saturation lost per minute at this home level. */
    public static double consumptionFactor(int homeLevel) {
        return switch (homeLevel) {
            case 1 -> 0.6;
            case 2 -> 0.725;
            case 3 -> 1.0;
            case 4 -> 1.2;
            case 5 -> 1.5;
            default -> 0.3;
        };
    }
}
