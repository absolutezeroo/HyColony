package dev.hycolony.plugin.item;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import dev.hycolony.core.kernel.catalog.BlockCatalog;
import dev.hycolony.core.kernel.item.BlockItems;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.plugin.block.HytaleBlockStates;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * BlockCatalog over the Hytale block and fluid asset maps (cheat sheet § 2 and § 1 "Hardness"). Results are cached
 * per key; the cache is not cleared on an asset reload (restart after changing assets). World thread only.
 *
 * <p>Hytale has no hardness and no MineColonies tool types, so these are mapped:
 * <ul>
 *   <li>tool type from the block's {@code GatherType}: Rocks, VolcanicRocks, GoblinMetal, Ore* → PICKAXE; Woods,
 *       SoftWoods → AXE; Soils → SHOVEL; anything else needs no tool;</li>
 *   <li>hardness = {@code 0.05 / unarmedPower}, clamped to [0.05, 3] ({@link HytaleItemInfo#hardness}), so that
 *       mining time follows Hytale's hits per block with the tool speeds of {@code HytaleItemCatalog}.</li>
 * </ul>
 * Hut blocks are UNBREAKABLE, so the builder never mines or builds over a hut, filler cells included (a filler cell
 * reports its origin's block, see {@code HytaleWorldBlocks}).
 */
public final class HytaleBlockCatalog implements BlockCatalog {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final BlockInfo UNKNOWN_BLOCK =
            new BlockInfo(BlockKind.UNBREAKABLE, BlockItems.NONE, false, Optional.empty(), 1f, false, false, false);
    private static final BlockInfo FLUID =
            new BlockInfo(BlockKind.FLUID, BlockItems.NONE, false, Optional.empty(), 0f, false, false, false);
    private static final BlockInfo AIR =
            new BlockInfo(BlockKind.AIR, BlockItems.NONE, false, Optional.empty(), 0f, false, false, false);

    private record BlockInfo(
            BlockKind kind,
            BlockItems items,
            boolean ore,
            Optional<ToolType> tool,
            float hardness,
            boolean harmful,
            boolean bed,
            boolean seat) {}

    private final Map<BlockKey, BlockInfo> blocks = new HashMap<>();
    private final Set<String> hutBlockIds;
    private final HytaleBlockItems blockItems = new HytaleBlockItems();
    private boolean warned;

    /** {@code hutBlockIds}: the id-map's hut block ids, which are never broken. */
    public HytaleBlockCatalog(Set<String> hutBlockIds) {
        this.hutBlockIds = Set.copyOf(hutBlockIds);
    }

    /** Read by {@link HytaleBlockItems} from the block's assets; cached with the rest of the block. */
    @Override
    public BlockItems blockItems(BlockKey block) {
        return block(block).items();
    }

    @Override
    public BlockKind kind(BlockKey block) {
        return block(block).kind();
    }

    @Override
    public boolean isOre(BlockKey block) {
        return block(block).ore();
    }

    @Override
    public Optional<ToolType> toolFor(BlockKey block) {
        return block(block).tool();
    }

    @Override
    public float hardness(BlockKey block) {
        return block(block).hardness();
    }

    /**
     * A fluid or block with {@code DamageToEntities} or a collision interaction: vanilla lava, fire, unlit campfires,
     * braziers and cacti hurt through their {@code Collision} interaction (their damage is 0), which
     * {@link Fluid#isTrigger()} and {@link BlockType#isTrigger()} report, the pair the collision module checks
     * ({@code CollisionConfig}). A few harmless triggers (traps, slowing seaweed) are avoided too. A fluid key absent
     * from the asset map counts as harmful; an unknown fluid read from a chunk is an {@code UNKNOWN} clone with no
     * damage and no interaction, so it does not.
     */
    @Override
    public boolean isHarmful(BlockKey block) {
        String id = block.id();
        if (!id.startsWith(HytaleBlockStates.FLUID_PREFIX)) {
            return block(block).harmful();
        }
        try {
            Fluid fluid = Fluid.getAssetMap().getAsset(id.substring(HytaleBlockStates.FLUID_PREFIX.length()));
            return fluid == null || fluid.getDamageToEntities() > 0 || fluid.isTrigger();
        } catch (RuntimeException e) {
            fail(id, e);
            return true;
        }
    }

    /** BlockType.getBeds() is non-null for every bed (vanilla and HyVanilla); an unknown block or a fluid is none. */
    @Override
    public boolean isBed(BlockKey block) {
        return block(block).bed();
    }

    /** BlockType.getSeats() is non-null for every seat (32 vanilla chairs, stools, benches, sp4b-hytale-food § 6). */
    @Override
    public boolean isSeat(BlockKey block) {
        return block(block).seat();
    }

    private BlockInfo block(BlockKey key) {
        BlockInfo info = blocks.get(key);
        if (info == null) {
            try {
                info = computeBlock(key.id());
            } catch (RuntimeException e) {
                fail(key.id(), e);
                info = UNKNOWN_BLOCK;
            }
            blocks.put(key, info);
        }
        return info;
    }

    private BlockInfo computeBlock(String id) {
        if (id.startsWith(HytaleBlockStates.FLUID_PREFIX)) {
            return FLUID;
        }
        if (BlockType.EMPTY_KEY.equals(id)) {
            return AIR;
        }
        BlockType type = baseType(id);
        if (type == null) {
            return UNKNOWN_BLOCK;
        }
        return type == BlockType.EMPTY ? AIR : describe(type);
    }

    /** The block type of {@code id}, a state variant's being its base block's; null when unknown. */
    private static @Nullable BlockType baseType(String id) {
        BlockType type = BlockType.getAssetMap().getAsset(id);
        if (type != null && id.startsWith("*") && type.getDefaultStateKey() != null) {
            type = BlockType.getAssetMap().getAsset(type.getDefaultStateKey());
        }
        return type == null || type.isUnknown() ? null : type;
    }

    /** The facts of a known, non-empty block type; a hut block or one without breaking is UNBREAKABLE. */
    private BlockInfo describe(BlockType type) {
        boolean harmful = type.getDamageToEntities() > 0 || type.isTrigger();
        boolean bed = type.getBeds() != null; // every bed has sleeping points (BlockMountAPI), vanilla and HyVanilla
        boolean seat = type.getSeats() != null; // chairs, stools, benches (BlockMountAPI takes a seat first)
        BlockItems blockItemsOf = blockItems.of(type);
        BlockGathering g = type.getGathering();
        BlockBreakingDropType breaking = g == null ? null : g.getBreaking();
        String gather = breaking == null ? null : breaking.getGatherType();
        if (g == null || "Unbreakable".equals(gather) || hutBlockIds.contains(type.getId())) {
            return new BlockInfo(BlockKind.UNBREAKABLE, blockItemsOf, false, Optional.empty(), 1f, harmful, bed, seat);
        }
        BlockKind kind = type.getMaterial() == BlockMaterial.Empty ? BlockKind.NON_SOLID : BlockKind.SOLID;
        return new BlockInfo(
                kind,
                blockItemsOf,
                gather != null && gather.startsWith("Ore"),
                Optional.ofNullable(toolType(gather)),
                HytaleItemInfo.hardness(gather),
                harmful,
                bed,
                seat);
    }

    private static @Nullable ToolType toolType(@Nullable String gather) {
        if (gather == null) {
            return null;
        }
        if (gather.startsWith("Ore")) {
            return ToolType.PICKAXE;
        }
        return switch (gather) {
            // Not "Metals": every U7 tool lists it IsIncorrect (power 0.001), so no tool is the right one.
            case "Rocks", "VolcanicRocks", "GoblinMetal" -> ToolType.PICKAXE;
            case "Woods", "SoftWoods" -> ToolType.AXE;
            case "Soils" -> ToolType.SHOVEL;
            default -> null;
        };
    }

    private void fail(String id, RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("BlockCatalog lookup failed for %s", id);
        warned = true;
    }
}
