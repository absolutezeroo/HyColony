package dev.hycolony.core.testing;

import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.blueprint.PackInfo;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Blueprints by building type and level, one style ("medieval"), plus the blocks they use. {@link #hut(boolean)}
 * is the 3x3x3 plan of the simulation: a SOLID floor and walls, a NON_SOLID torch and a chest with a container.
 */
public final class FakeBlueprints implements BlueprintSource {
    public static final String STYLE = "medieval";

    public static final BlockKey PLANKS = new BlockKey("test:planks");
    public static final BlockKey GLASS = new BlockKey("test:glass");
    public static final BlockKey TORCH = new BlockKey("test:torch");
    public static final BlockKey CHEST = new BlockKey("test:chest");
    public static final BlockKey DIRT = new BlockKey("test:dirt");
    public static final BlockKey HUT_BLOCK = new BlockKey("test:hut");
    public static final ItemKey PLANKS_I = new ItemKey("test:planks_item");
    public static final ItemKey GLASS_I = new ItemKey("test:glass_item");
    public static final ItemKey TORCH_I = new ItemKey("test:torch_item");
    public static final ItemKey CHEST_I = new ItemKey("test:chest_item");
    public static final ItemKey DIRT_I = new ItemKey("test:dirt_item");

    private final Map<String, Blueprint> plans = new HashMap<>();
    /** Every load, as "style/type/level". */
    public final List<String> loads = new ArrayList<>();
    /** The styles offered; every style has the same plans. */
    public final List<String> styleIds = new ArrayList<>(List.of(STYLE));
    /** Pack metadata by style; absent = {@link PackInfo#defaults}. */
    public final Map<String, PackInfo> packs = new HashMap<>();
    /** Blueprint folder by hut type; absent = {@link PackInfo#DEFAULT_CATEGORY}. */
    public final Map<String, String> categories = new HashMap<>();

    /** Kinds and items of the blocks above; every stack holds 64. */
    public static void registerBlocks(FakeCatalog catalog) {
        catalog.kinds.put(PLANKS, BlockKind.SOLID);
        catalog.kinds.put(GLASS, BlockKind.SOLID);
        catalog.kinds.put(TORCH, BlockKind.NON_SOLID);
        catalog.kinds.put(CHEST, BlockKind.SOLID);
        catalog.kinds.put(DIRT, BlockKind.SOLID);
        catalog.kinds.put(HUT_BLOCK, BlockKind.SOLID);
        catalog.itemForBlock.put(PLANKS, PLANKS_I);
        catalog.itemForBlock.put(GLASS, GLASS_I);
        catalog.itemForBlock.put(TORCH, TORCH_I);
        catalog.itemForBlock.put(CHEST, CHEST_I);
        catalog.itemForBlock.put(DIRT, DIRT_I);
    }

    public FakeBlueprints put(String buildingTypeId, int level, Blueprint bp) {
        plans.put(buildingTypeId + "/" + level, bp);
        return this;
    }

    @Override
    public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
        loads.add(style + "/" + buildingTypeId + "/" + level);
        return Optional.ofNullable(plans.get(buildingTypeId + "/" + level));
    }

    @Override
    public List<String> styles() {
        return styleIds;
    }

    @Override
    public PackInfo pack(String style) {
        return packs.getOrDefault(style, PackInfo.defaults(style));
    }

    @Override
    public String category(String buildingTypeId) {
        return categories.getOrDefault(buildingTypeId, PackInfo.DEFAULT_CATEGORY);
    }

    public static BlockState state(BlockKey key) {
        return new BlockState(key, 0);
    }

    public static BlueprintEntry entry(int x, int y, int z, BlockKey key) {
        return new BlueprintEntry(new BlockPos(x, y, z), state(key), key.equals(CHEST));
    }

    /**
     * 3x3x3 around the hut (x, z in -1..1, y in 0..2): 8 planks of floor around the hut block, 16 planks of walls,
     * a torch above the hut and a chest (container) on top. Level 2 ({@code glass}) swaps the two x-facing walls of
     * the middle layer for glass.
     */
    public static Blueprint hut(boolean glass) {
        List<BlueprintEntry> entries = new ArrayList<>();
        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) {
                        continue;
                    }
                    boolean window = glass && y == 1 && z == 0;
                    entries.add(entry(x, y, z, window ? GLASS : PLANKS));
                }
            }
        }
        entries.add(entry(0, 1, 0, TORCH));
        entries.add(entry(0, 2, 0, CHEST));
        return new Blueprint("hut", entries, new BlockPos(-1, 0, -1), new BlockPos(1, 2, 1));
    }

    /** {@code 50 x 20 x 20} = 20 000 planks, beside the hut (x 1..50, y 0..19, z 0..19). */
    public static Blueprint large() {
        List<BlueprintEntry> entries = new ArrayList<>(20_000);
        for (int x = 1; x <= 50; x++) {
            for (int y = 0; y < 20; y++) {
                for (int z = 0; z < 20; z++) {
                    entries.add(entry(x, y, z, PLANKS));
                }
            }
        }
        return new Blueprint("large", entries, new BlockPos(0, 0, 0), new BlockPos(50, 19, 19));
    }
}
