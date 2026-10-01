package dev.hycolony.plugin.food;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The {@code food} section of the id-map (sp4b-hytale-food § 8): what each Hytale food gives a citizen, the bench that
 * cooks raw food (MC's furnace) and the particle of a citizen eating. Hytale has no hunger nor nutrition, so the
 * values are HyColony's, set after MineColonies' for the like food (spec SP4b § 2.2). Absent from an older id-map:
 * nothing is food.
 *
 * @param foods item id -> what eating it gives
 * @param cookingBench the processing bench whose recipes cook food (Hytale's campfire)
 * @param eatParticle the particle system of crumbs at a citizen's mouth
 */
public record FoodIds(
        @Nullable Map<String, Food> foods,
        @Nullable String cookingBench,
        @Nullable String eatParticle) {
    /** An id-map without food. */
    public static final FoodIds NONE = new FoodIds(null, null, null);

    /** One food: MC nutrition, dish tier (0 for an ordinary food) and MC's {@code poisonous_food} tag. */
    public record Food(int nutrition, int tier, boolean poisonous) {}

    public Map<String, Food> table() {
        return Objects.requireNonNullElse(foods, Map.of());
    }

    /** The cooking bench id; empty when the id-map has none (nothing cooks). */
    public Optional<String> bench() {
        return Optional.ofNullable(cookingBench);
    }

    public Optional<String> particle() {
        return Optional.ofNullable(eatParticle);
    }
}
