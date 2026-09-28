package dev.hycolony.core.testing.farming;

import dev.hycolony.core.farming.CropState;
import dev.hycolony.core.farming.FarmingAccess;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An in-memory world of soil and crops, following the Hytale rules of sp3b-hytale-farming: a mature normal crop is
 * gone once harvested, an eternal one ({@code _Eternal} seed) goes back to growing. Tests steer it through its fields.
 */
public final class FakeFarming implements FarmingAccess {
    public static final ItemKey WHEAT_SEEDS = new ItemKey("Plant_Seeds_Wheat");
    public static final ItemKey WHEAT = new ItemKey("Plant_Crop_Wheat_Item");
    public static final ItemKey ESSENCE = new ItemKey("Ingredient_Life_Essence");
    public static final ItemKey FERTILIZER = new ItemKey("Tool_Fertilizer");

    public final Set<BlockPos> tillable = new HashSet<>();
    public final Set<BlockPos> tilled = new HashSet<>();
    public final Set<BlockPos> fertilized = new HashSet<>();
    public final Set<BlockPos> barriers = new HashSet<>();
    public final Set<BlockPos> fieldBlocks = new HashSet<>();
    /** The seed each crop grew from, by crop position. */
    public final Map<BlockPos, ItemKey> crops = new HashMap<>();

    public final Map<BlockPos, CropState> cropState = new HashMap<>();

    @Override
    public boolean isTillable(BlockPos pos) {
        return tillable.contains(pos);
    }

    @Override
    public boolean isTilled(BlockPos pos) {
        return tilled.contains(pos);
    }

    @Override
    public boolean till(BlockPos pos) {
        if (!tillable.remove(pos)) {
            return false;
        }
        tilled.add(pos);
        return true;
    }

    @Override
    public boolean isFertilized(BlockPos pos) {
        return fertilized.contains(pos);
    }

    @Override
    public boolean fertilize(BlockPos pos) {
        return tilled.contains(pos) && fertilized.add(pos);
    }

    @Override
    public CropState crop(BlockPos pos) {
        return crops.containsKey(pos) ? cropState.getOrDefault(pos, CropState.GROWING) : CropState.NONE;
    }

    @Override
    public boolean plant(BlockPos pos, ItemKey seed) {
        if (crops.containsKey(pos)) {
            return false;
        }
        crops.put(pos, seed);
        cropState.put(pos, CropState.GROWING);
        return true;
    }

    /** One wheat and three essence per mature crop, whatever the seed. */
    @Override
    public List<ItemAmount> harvest(BlockPos pos) {
        if (crop(pos) != CropState.MATURE) {
            return List.of();
        }
        if (crops.get(pos).id().endsWith("_Eternal")) {
            cropState.put(pos, CropState.GROWING);
        } else {
            crops.remove(pos);
            cropState.remove(pos);
        }
        return List.of(new ItemAmount(WHEAT, 1), new ItemAmount(ESSENCE, 3));
    }

    @Override
    public boolean isFieldBarrier(BlockPos pos) {
        return barriers.contains(pos);
    }

    @Override
    public List<ItemKey> seeds() {
        return List.of(WHEAT_SEEDS, new ItemKey("Plant_Seeds_Wheat_Eternal"));
    }

    @Override
    public boolean isFieldBlock(BlockPos pos) {
        return fieldBlocks.contains(pos);
    }

    @Override
    public ItemKey fertilizerItem() {
        return FERTILIZER;
    }
}
