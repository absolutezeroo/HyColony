package dev.hycolony.plugin.crafting;

import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The block, and so the item, that shows a bench id to players: a recipe names its bench by id ({@code Farmingbench}),
 * which no lang file translates, while the bench's item has a name ({@code Bench_Farming}). Among blocks sharing the
 * id, the one with the most tier levels wins, as in {@link BenchIndex}. Read once, lazily, on the world thread.
 */
public final class BenchItems {
    private static volatile Map<String, String> byBench = Map.of();

    private BenchItems() {}

    /** The item id of the block that shows {@code benchId}; empty if no crafting bench has that id. */
    public static Optional<String> of(String benchId) {
        Map<String, String> map = byBench;
        if (map.isEmpty()) {
            map = load();
            byBench = map;
        }
        return Optional.ofNullable(map.get(benchId));
    }

    private static Map<String, String> load() {
        Map<String, BlockType> best = new HashMap<>();
        for (BlockType type : BlockType.getAssetMap().getAssetMap().values()) {
            Bench bench = type == null ? null : type.getBench();
            if (bench != null && bench.getType() == BenchType.Crafting && bench.getId() != null) {
                best.merge(bench.getId(), type, (a, b) -> tiers(b) > tiers(a) ? b : a);
            }
        }
        Map<String, String> out = new HashMap<>();
        best.forEach((id, type) -> out.put(id, base(type)));
        return Map.copyOf(out);
    }

    /** A state variant's base block id (its item's id for vanilla benches). */
    private static String base(BlockType type) {
        String base = type.getDefaultStateKey();
        return base == null ? type.getId() : base;
    }

    private static int tiers(BlockType type) {
        Bench bench = type.getBench();
        return bench == null ? 0 : BenchIndex.tierCount(bench);
    }
}
