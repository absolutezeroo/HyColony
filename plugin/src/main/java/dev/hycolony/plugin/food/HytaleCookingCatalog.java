package dev.hycolony.plugin.food;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import dev.hycolony.core.crafting.furnace.CookingCatalog;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.crafting.ResourceTypeIndex;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * CookingCatalog over Hytale's assets: a station is a block whose bench is the id-map's processing bench (the
 * campfire); the fuels are the items of the {@code Fuel} resource type (what the campfire's fuel slot accepts); the
 * defaults are the id-map's; a dish's raw item comes from the cooking recipes ({@link HytaleFoods}). Read on first use,
 * cached; never throws (a failure answers no station, no fuel, logged once as a WARNING).
 */
public final class HytaleCookingCatalog implements CookingCatalog {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The resource type of everything a processing bench burns (Bench_Campfire.json's fuel slot). */
    static final String FUEL_RESOURCE_TYPE = "Fuel";

    private final FoodIds ids;
    private final HytaleFoods foods;
    private final Map<BlockKey, Boolean> stations = new HashMap<>();
    private @Nullable List<ItemKey> fuels;
    private boolean warned;

    public HytaleCookingCatalog(FoodIds ids, HytaleFoods foods) {
        this.ids = ids;
        this.foods = foods;
    }

    /** A block whose BlockType bench is the id-map's cooking bench, of type Processing. */
    @Override
    public boolean isStation(BlockKey block) {
        return stations.computeIfAbsent(block, k -> {
            try {
                BlockType type = BlockType.getAssetMap().getAsset(k.id());
                Bench bench = type == null ? null : type.getBench();
                return bench != null
                        && bench.getType() == BenchType.Processing
                        && ids.bench().map(bench.getId()::equals).orElse(false);
            } catch (RuntimeException e) {
                fail(e);
                return false;
            }
        });
    }

    @Override
    public List<ItemKey> fuels() {
        List<ItemKey> all = fuels;
        if (all == null) {
            try {
                all = ResourceTypeIndex.load().items(FUEL_RESOURCE_TYPE);
            } catch (RuntimeException e) {
                fail(e);
                all = List.of();
            }
            fuels = all;
        }
        return all;
    }

    @Override
    public List<ItemKey> defaultFuels() {
        return ids.fuels().stream().map(ItemKey::new).toList();
    }

    @Override
    public Optional<ItemKey> rawFor(ItemKey dish) {
        return foods.rawFor(dish);
    }

    private void fail(RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("CookingCatalog failed");
        warned = true;
    }
}
