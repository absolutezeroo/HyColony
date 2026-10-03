package dev.hycolony.plugin.block;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.VariantRotation;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.CustomTemplateConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.builtin.StairLikeConnectedBlockRuleSet;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import java.util.Arrays;
import java.util.Optional;

/**
 * Translates Hytale block and fluid runtime ids to core {@link BlockState}s, cached once per (id, rotation) because
 * world reads are hot. Fluids read as the pseudo-key {@code ~fluid:<FluidId>}; state variants as {@link #blockKey}.
 * Never throws on an unknown id: it is empty. World thread only.
 */
public final class HytaleBlockStates {
    /** Pseudo-key prefix for fluids, shared with the block catalog, the block traits and the world adapter. */
    public static final String FLUID_PREFIX = "~fluid:";

    private static final int ROTATIONS = 64; // RotationTuple.VALUES.length

    /** One Optional per (block runtime id, rotation index), built once. */
    private Optional<BlockState>[][] blockCache = newCache(1024);

    private Optional<BlockState>[] fluidCache = newRow(64);

    /**
     * The state of block {@code id} at rotation index {@code rotation}; empty for an out-of-range or unknown id. A
     * block that cannot rotate ({@code VariantRotation.None}) reads as rotation 0, like its blueprint entry.
     */
    public Optional<BlockState> block(int id, int rotation) {
        if (id < 0 || rotation < 0 || rotation >= ROTATIONS) {
            return Optional.empty();
        }
        if (id >= blockCache.length) {
            blockCache = Arrays.copyOf(blockCache, Math.max(id + 1, blockCache.length * 2));
        }
        Optional<BlockState>[] byRotation = blockCache[id];
        if (byRotation == null) {
            byRotation = blockCache[id] = newRow(ROTATIONS);
        }
        Optional<BlockState> cached = byRotation[rotation];
        if (cached == null) {
            BlockType type = BlockType.getAssetMap().getAsset(id);
            if (type == null) {
                return Optional.empty(); // not cached: the asset may appear later
            }
            String key = blockKey(type);
            // A block that cannot rotate still stores the index it was placed with (a prefab adds its yaw to every
            // block): reported as 0 so it matches its blueprint entry and natural terrain.
            int r = type.getVariantRotation() == VariantRotation.None ? 0 : rotation;
            cached = byRotation[rotation] = Optional.of(new BlockState(new BlockKey(key), r));
        }
        return cached;
    }

    /**
     * The key the builder places and compares: a connected-block shape state (stair or roof corner, roof Topper,
     * fence Post/End/Corner/T/Cross) keeps its variant id, as vanilla prefab pasting writes it; any other state
     * variant ({@code *…}, e.g. an open door or chest) is its base block, so a player's interaction is not rebuilt.
     */
    public static String blockKey(BlockType type) {
        String id = type.getId();
        String base = type.getDefaultStateKey();
        if (!id.startsWith("*") || base == null) {
            return id;
        }
        ConnectedBlockRuleSet rules = type.getConnectedBlockRuleSet();
        if (rules instanceof StairLikeConnectedBlockRuleSet) {
            return id; // every stair and roof state is a shape
        }
        if (rules instanceof CustomTemplateConnectedBlockRuleSet template
                && !template.getShapesForBlockType(BlockType.getAssetMap().getIndex(id))
                        .isEmpty()) {
            return id;
        }
        return base;
    }

    /** The state of fluid {@code fluid} ({@code ~fluid:<FluidId>}, rotation 0); empty for an unknown fluid. */
    public Optional<BlockState> fluid(int fluid) {
        if (fluid >= fluidCache.length) {
            fluidCache = Arrays.copyOf(fluidCache, Math.max(fluid + 1, fluidCache.length * 2));
        }
        Optional<BlockState> cached = fluidCache[fluid];
        if (cached == null) {
            Fluid f = Fluid.getAssetMap().getAsset(fluid);
            if (f == null) {
                return Optional.empty();
            }
            cached = fluidCache[fluid] = Optional.of(new BlockState(new BlockKey(FLUID_PREFIX + f.getId()), 0));
        }
        return cached;
    }

    @SuppressWarnings("unchecked")
    private static Optional<BlockState>[][] newCache(int size) {
        return new Optional[size][];
    }

    @SuppressWarnings("unchecked")
    private static Optional<BlockState>[] newRow(int size) {
        return new Optional[size];
    }
}
