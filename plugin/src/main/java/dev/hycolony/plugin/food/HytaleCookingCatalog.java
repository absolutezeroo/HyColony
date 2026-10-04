package dev.hycolony.plugin.food;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import dev.hycolony.core.crafting.furnace.CookingCatalog;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * CookingCatalog over Hytale's assets: a station is a block whose bench cooks a food, the fuels are what the stations'
 * fuel slots burn ({@link CookingBenches}); the defaults are the id-map's; a dish's raw item comes from the cooking
 * recipes ({@link HytaleFoods}). Read on first use, cached; never throws (a failure answers no station, logged once as
 * a WARNING).
 */
public final class HytaleCookingCatalog implements CookingCatalog {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final FoodIds ids;
    private final HytaleFoods foods;
    private final Map<BlockKey, Boolean> stations = new HashMap<>();
    private boolean warned;

    public HytaleCookingCatalog(FoodIds ids, HytaleFoods foods) {
        this.ids = ids;
        this.foods = foods;
    }

    /** A block whose bench is a processing bench that cooks a food ({@link CookingBenches}). */
    @Override
    public boolean isStation(BlockKey block) {
        return stations.computeIfAbsent(block, k -> {
            try {
                BlockType type = BlockType.getAssetMap().getAsset(k.id());
                Bench bench = type == null ? null : type.getBench();
                return bench != null
                        && bench.getType() == BenchType.Processing
                        && foods.benches().benches().contains(bench.getId());
            } catch (RuntimeException e) {
                fail(e);
                return false;
            }
        });
    }

    /** What every cooking station's fuel slot burns. */
    @Override
    public List<ItemKey> fuels() {
        return foods.benches().fuels();
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
