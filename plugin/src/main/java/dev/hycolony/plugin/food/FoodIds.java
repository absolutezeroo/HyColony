package dev.hycolony.plugin.food;

import dev.hycolony.core.kernel.item.FoodQuality;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The {@code food} section of the id-map (sp4b-hytale-food § 8, spec 2026-10-04 § 5): the particle of a citizen
 * eating, the fuels a dining hall allows at first, and how a Hytale food without a HyColony food file is told (its item
 * category) and valued (its quality's rank). The foods themselves are the {@code Server/HyColony/Foods} files
 * ({@link FoodValueAsset}); the cooking stations are every bench that cooks one ({@link CookingBenches}). Absent from
 * an older id-map: no food by default.
 *
 * @param eatParticle the particle system of crumbs at a citizen's mouth
 * @param defaultFuels the fuel item ids a new dining hall allows (MC's coal and charcoal)
 * @param foodCategory the item category of Hytale's foods
 * @param qualityRanks Hytale item quality id -> {@link FoodQuality} constant name
 */
public record FoodIds(
        @Nullable String eatParticle,
        @Nullable List<String> defaultFuels,
        @Nullable String foodCategory,
        @Nullable Map<String, String> qualityRanks) {
    /** An id-map without food. */
    public static final FoodIds NONE = new FoodIds(null, null, null, null);

    public Optional<String> particle() {
        return Optional.ofNullable(eatParticle);
    }

    /** The fuels a new dining hall allows; none in an older id-map. */
    public List<String> fuels() {
        return Objects.requireNonNullElse(defaultFuels, List.of());
    }

    /** The item category of Hytale's foods; empty in an older id-map (no food without a file). */
    public Optional<String> category() {
        return Optional.ofNullable(foodCategory);
    }

    /** Quality id -> rank name, as written; none in an older id-map. */
    public Map<String, String> ranks() {
        return Objects.requireNonNullElse(qualityRanks, Map.of());
    }

    /** The rank of {@code qualityId}: COMMON for a quality the id-map does not rank, or ranks by an unknown name. */
    public FoodQuality rank(@Nullable String qualityId) {
        String name = qualityId == null ? null : ranks().get(qualityId);
        return name != null && isRank(name) ? FoodQuality.valueOf(name) : FoodQuality.COMMON;
    }

    /** Whether {@code name} names a {@link FoodQuality} constant. */
    public static boolean isRank(String name) {
        return Arrays.stream(FoodQuality.values()).anyMatch(q -> q.name().equals(name));
    }
}
