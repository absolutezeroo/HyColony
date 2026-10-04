package dev.hycolony.plugin.food;

import dev.hycolony.core.kernel.catalog.FoodCatalog;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * FoodCatalog over HyColony's food files and Hytale's other foods ({@link FoodTable}), and what the cooking stations'
 * recipes turn each item into ({@link CookingBenches}, MC the furnace's smelting result). Both are read on first use,
 * the asset maps being loaded by then; an asset reload needs a restart, like {@code HytaleItemCatalog}. Never throws.
 *
 * <p>Deviation from MC: Hytale has no hunger nor nutrition, so the values are HyColony's, after MC's for the matching
 * foods (spec SP4b § 2.2); MC's tier 1 for a plain food of nutrition 12 and saturation 0.8 has no match, and no Hytale
 * food gives back a container (MC's bowl).
 */
public final class HytaleFoods implements FoodCatalog {
    private final FoodIds ids;
    private @Nullable Map<ItemKey, FoodInfo> foods;
    private @Nullable CookingBenches benches;

    public HytaleFoods(FoodIds ids) {
        this.ids = ids;
    }

    @Override
    public Optional<FoodInfo> food(ItemKey item) {
        return Optional.ofNullable(table().get(item));
    }

    /** Every food, by id. */
    @Override
    public List<ItemKey> foods() {
        return table().keySet().stream()
                .sorted(Comparator.comparing(ItemKey::id))
                .toList();
    }

    /** The foods, read on first use, once the assets are loaded ({@link FoodTable}). */
    private Map<ItemKey, FoodInfo> table() {
        Map<ItemKey, FoodInfo> map = foods;
        if (map == null) {
            map = FoodTable.load(ids);
            foods = map;
        }
        return map;
    }

    /** The cooking stations, read on first use, once the assets are loaded ({@link CookingBenches}). */
    CookingBenches benches() {
        CookingBenches b = benches;
        if (b == null) {
            b = CookingBenches.load(item -> table().containsKey(item));
            benches = b;
        }
        return b;
    }

    /** What a cooking station makes of {@code item}; empty when it does not cook. */
    @Override
    public Optional<ItemKey> cooked(ItemKey item) {
        return Optional.ofNullable(benches().cooked().get(item));
    }

    /** The item that cooks into {@code dish}, the first by id when several do; empty when none does. */
    public Optional<ItemKey> rawFor(ItemKey dish) {
        return benches().cooked().entrySet().stream()
                .filter(e -> e.getValue().equals(dish))
                .map(Map.Entry::getKey)
                .min(Comparator.comparing(ItemKey::id));
    }
}
