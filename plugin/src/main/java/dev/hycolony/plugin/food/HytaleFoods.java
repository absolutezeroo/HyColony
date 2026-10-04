package dev.hycolony.plugin.food;

import dev.hycolony.core.kernel.catalog.FoodCatalog;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * FoodCatalog over HyColony's food files and Hytale's other foods ({@link FoodTable}), and what the cooking stations'
 * recipes turn each item into ({@link CookingBenches}, MC the furnace's smelting result). Both are read on first use,
 * the asset maps being loaded by then; an asset reload needs a restart, like {@code HytaleItemCatalog}. Never throws.
 *
 * <p>Deviation from MC (Hytale world): MC reads an item's FoodProperties and IMinecoloniesFoodItem tier → Hytale has
 * no hunger nor nutrition (sp4b-hytale-food § 2), so the values are HyColony's food files, after MC's for the matching
 * foods (spec SP4b § 2.2); MC's tier 1 for a plain food of nutrition 12 and saturation 0.8 has no match, and no Hytale
 * food gives back a container (MC's bowl).
 */
public final class HytaleFoods implements FoodCatalog {
    private final FoodIds ids;
    private final Set<ItemKey> excluded;
    private @Nullable FoodTable table;
    private @Nullable CookingBenches benches;

    /** The foods of {@code ids}' category and the food files, none of {@code excluded} (MC excluded_food tag). */
    public HytaleFoods(FoodIds ids, Set<ItemKey> excluded) {
        this.ids = ids;
        this.excluded = Set.copyOf(excluded);
    }

    @Override
    public Optional<FoodInfo> food(ItemKey item) {
        return Optional.ofNullable(table().foods().get(item));
    }

    /** Every food, by id. */
    @Override
    public List<ItemKey> foods() {
        return table().foods().keySet().stream()
                .sorted(Comparator.comparing(ItemKey::id))
                .toList();
    }

    /** The item ids whose food file was taken (for the selftest). */
    public Set<String> fromFiles() {
        return table().fromFiles();
    }

    /** The foods, read on first use, once the assets are loaded ({@link FoodTable}). */
    private FoodTable table() {
        FoodTable t = table;
        if (t == null) {
            t = FoodTable.load(ids, excluded);
            table = t;
        }
        return t;
    }

    /** The cooking stations, read on first use, once the assets are loaded ({@link CookingBenches}). */
    CookingBenches benches() {
        CookingBenches b = benches;
        if (b == null) {
            b = CookingBenches.load(item -> table().foods().containsKey(item));
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
