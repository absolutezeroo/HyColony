package dev.hycolony.plugin.food;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import dev.hycolony.core.kernel.catalog.FoodCatalog;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.crafting.ResourceTypeIndex;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * FoodCatalog over HyColony's food files and Hytale's other foods ({@link FoodTable}), and what the cooking bench's
 * recipes turn each item into (MC the furnace's smelting result). Both are read on first use, the asset maps being
 * loaded by then; an asset reload needs a restart, like {@code HytaleItemCatalog}. Never throws.
 *
 * <p>Deviation from MC: Hytale has no hunger nor nutrition, so the values are HyColony's, after MC's for the matching
 * foods (spec SP4b § 2.2); MC's tier 1 for a plain food of nutrition 12 and saturation 0.8 has no match, and no Hytale
 * food gives back a container (MC's bowl).
 */
public final class HytaleFoods implements FoodCatalog {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final FoodIds ids;
    private @Nullable Map<ItemKey, FoodInfo> foods;
    private @Nullable Map<ItemKey, ItemKey> cooked;

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

    /** What the cooking bench makes of {@code item}; empty when it does not cook there. */
    @Override
    public Optional<ItemKey> cooked(ItemKey item) {
        return Optional.ofNullable(cookedMap().get(item));
    }

    /** The item that cooks into {@code dish}, the first by id when several do; empty when none does. */
    public Optional<ItemKey> rawFor(ItemKey dish) {
        return cookedMap().entrySet().stream()
                .filter(e -> e.getValue().equals(dish))
                .map(Map.Entry::getKey)
                .min(Comparator.comparing(ItemKey::id));
    }

    private Map<ItemKey, ItemKey> cookedMap() {
        Map<ItemKey, ItemKey> map = cooked;
        if (map == null) {
            map = loadCooked();
            cooked = map;
        }
        return map;
    }

    /**
     * Every input of the cooking bench's processing recipes, an item or each item of a resource type, mapped to the
     * recipe's primary output (the first recipe wins); empty without a cooking bench or when the assets fail.
     */
    private Map<ItemKey, ItemKey> loadCooked() {
        Map<ItemKey, ItemKey> out = new HashMap<>();
        String bench = ids.bench().orElse(null);
        if (bench == null) {
            return out;
        }
        try {
            ResourceTypeIndex resources = ResourceTypeIndex.load();
            for (CraftingRecipe r : CraftingRecipe.getAssetMap().getAssetMap().values()) {
                if (r != null && cooksAt(r, bench)) {
                    addCooking(r, resources, out);
                }
            }
            LOG.at(Level.INFO).log("Cooking: %d items cook at %s", out.size(), bench);
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("Cooking recipes could not be read; nothing cooks");
        }
        return out;
    }

    /** Maps each input of the cooking recipe {@code r} to its primary output in {@code out}; nothing without one. */
    private static void addCooking(CraftingRecipe r, ResourceTypeIndex resources, Map<ItemKey, ItemKey> out) {
        MaterialQuantity primary = r.getPrimaryOutput();
        if (primary == null || primary.getItemId() == null || r.getInput() == null) {
            return;
        }
        ItemKey result = new ItemKey(primary.getItemId());
        for (MaterialQuantity in : r.getInput()) {
            for (ItemKey item : inputs(in, resources)) {
                out.putIfAbsent(item, result);
            }
        }
    }

    /** Whether {@code r} is a processing recipe of the bench {@code bench}. */
    private static boolean cooksAt(CraftingRecipe r, String bench) {
        BenchRequirement[] all = r.getBenchRequirement();
        if (all == null) {
            return false;
        }
        for (BenchRequirement b : all) {
            if (b != null && b.type == BenchType.Processing && bench.equals(b.id)) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemKey> inputs(@Nullable MaterialQuantity in, ResourceTypeIndex resources) {
        if (in == null) {
            return List.of();
        }
        if (in.getItemId() != null) {
            return List.of(new ItemKey(in.getItemId()));
        }
        return in.getResourceTypeId() == null ? List.of() : resources.items(in.getResourceTypeId());
    }
}
