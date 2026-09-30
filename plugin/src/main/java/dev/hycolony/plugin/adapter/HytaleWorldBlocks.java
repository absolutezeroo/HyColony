package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.port.WorldBlocks;
import dev.hycolony.plugin.block.BenchTiers;
import dev.hycolony.plugin.block.HytaleBlockBreaker;
import dev.hycolony.plugin.block.HytaleBlockStates;
import dev.hycolony.plugin.block.HytaleSections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * WorldBlocks over the section API (cheat sheet § 1). Never loads a chunk, never throws. Fluids are reported as the
 * pseudo-key {@code ~fluid:<FluidId>}; state variants ({@code *…}) are reported as their base block, except
 * connected-block shapes ({@link HytaleBlockStates#blockKey}). A filler cell holds its origin's block id and rotation
 * ({@code FillerBlockUtil.setFillerBlocksAt}), so it reports the origin's
 * key: a hut's filler cells read as the hut, which the catalog calls UNBREAKABLE. A block that cannot rotate
 * ({@code VariantRotation.None}) reads as rotation 0, like its blueprint entry. World thread only.
 */
public final class HytaleWorldBlocks implements WorldBlocks {
    /** A creative paste's settings: no block particles, no block sound. */
    private static final int QUIET = SetBlockSettings.NO_SEND_PARTICLES | SetBlockSettings.NO_SEND_AUDIO;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String FLUID_PREFIX = HytaleBlockStates.FLUID_PREFIX;

    private final World world;
    private final Set<String> hutBlockIds;
    private final HytaleBlocks drops;
    private final HytaleBlockBreaker breaker;
    /** {@code get} is hot: the id to state translation is cached there. */
    private final HytaleBlockStates states = new HytaleBlockStates();

    private boolean warned;

    /** {@code hutBlockIds}: the id-map's hut block ids; never broken nor built over. */
    public HytaleWorldBlocks(World world, Set<String> hutBlockIds, HytaleBlocks drops) {
        this.world = world;
        this.hutBlockIds = Set.copyOf(hutBlockIds);
        this.drops = drops;
        this.breaker = new HytaleBlockBreaker(world, drops, this::isHut);
    }

    private boolean isHut(BlockType type) {
        return hutBlockIds.contains(type.getId())
                || (type.getDefaultStateKey() != null && hutBlockIds.contains(type.getDefaultStateKey()));
    }

    /** A cell outside the world's height (ChunkUtil.MIN_Y to HEIGHT) has no section ever: it counts as loaded. */
    @Override
    public boolean isLoaded(BlockPos pos) {
        if (pos.y() < ChunkUtil.MIN_Y || pos.y() >= ChunkUtil.HEIGHT) {
            return true;
        }
        try {
            return section(pos) != null;
        } catch (RuntimeException e) {
            fail("isLoaded", pos, e);
            return false;
        }
    }

    @Override
    public Optional<BlockState> get(BlockPos pos) {
        try {
            Ref<ChunkStore> sec = section(pos);
            if (sec == null) {
                return Optional.empty();
            }
            Store<ChunkStore> store = world.getChunkStore().getStore();
            BlockSection blocks = store.getComponent(sec, BlockSection.getComponentType());
            if (blocks == null) {
                return Optional.empty();
            }
            int id = blocks.get(pos.x(), pos.y(), pos.z());
            if (id == BlockType.EMPTY_ID) {
                FluidSection fluids = store.getComponent(sec, FluidSection.getComponentType());
                int fluid = fluids == null ? 0 : fluids.getFluidId(pos.x(), pos.y(), pos.z());
                if (fluid != Fluid.EMPTY_ID) {
                    return states.fluid(fluid);
                }
            }
            return states.block(id, blocks.getRotationIndex(pos.x(), pos.y(), pos.z()));
        } catch (RuntimeException e) {
            fail("get", pos, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean place(BlockPos pos, BlockState state, boolean withContainer) {
        return place(pos, state, SetBlockSettings.NONE);
    }

    /** Without the build particles (BlockOperations.setBlock skips them on NO_SEND_PARTICLES). */
    @Override
    public boolean placeQuietly(BlockPos pos, BlockState state, boolean withContainer) {
        return place(pos, state, QUIET);
    }

    /**
     * Places {@code state} with {@code settings}. {@code withContainer} (the blueprint's "has an ItemContainerBlock")
     * needs no setting: the block entity is part of the block, so a chest gets its container, a bench its state.
     */
    private boolean place(BlockPos pos, BlockState state, int settings) {
        try {
            Ref<ChunkStore> sec = section(pos);
            if (sec == null) {
                return false;
            }
            Store<ChunkStore> store = world.getChunkStore().getStore();
            String key = state.key().id();
            if (key.startsWith(FLUID_PREFIX)) {
                return placeFluid(store, sec, pos, key.substring(FLUID_PREFIX.length()));
            }
            int id = BlockType.getAssetMap().getIndex(key);
            BlockType type =
                    id == Integer.MIN_VALUE ? null : BlockType.getAssetMap().getAsset(id);
            BlockSection blocks = store.getComponent(sec, BlockSection.getComponentType());
            if (type == null || blocks == null) {
                return false;
            }
            if (!fits(store, blocks, pos, type, state.rotation())) {
                return false;
            }
            BlockOperations.setBlock(
                    world.getChunkStore(), sec, pos.x(), pos.y(), pos.z(), id, type, state.rotation(), 0, settings);
            if (type.getMaterial() == BlockMaterial.Solid) {
                HytaleSections.clearFluid(store, sec, pos);
            }
            return blocks.get(pos.x(), pos.y(), pos.z()) == id;
        } catch (RuntimeException e) {
            fail("place", pos, e);
            return false;
        }
    }

    /**
     * Whether {@code type} fits at {@code pos}. The builder cleared the spot first or turns the block already there,
     * so leftovers are replaced, except a hut (a multi-cell block's hitbox may reach one). Also false if part of the hitbox is in an unloaded section.
     */
    private boolean fits(Store<ChunkStore> store, BlockSection blocks, BlockPos pos, BlockType type, int rotation) {
        return BlockOperations.testPlaceBlock(
                store,
                blocks,
                pos.x(),
                pos.y(),
                pos.z(),
                type,
                rotation,
                (x, y, z, other, rot, filler) -> !isHut(other));
    }

    /** Fills {@code pos} with fluid {@code fluidId} at its full level; false for an unknown or empty fluid. */
    private static boolean placeFluid(Store<ChunkStore> store, Ref<ChunkStore> sec, BlockPos pos, String fluidId) {
        Fluid fluid = Fluid.getAssetMap().getAsset(fluidId);
        if (fluid == null || fluid == Fluid.EMPTY) {
            return false;
        }
        store.ensureAndGetComponent(sec, FluidSection.getComponentType())
                .setFluid(pos.x(), pos.y(), pos.z(), fluid, (byte) fluid.getMaxFluidLevel());
        return true;
    }

    @Override
    public List<ItemAmount> breakBlock(BlockPos pos) {
        return breakBlock(pos, SetBlockSettings.NO_DROP_ITEMS);
    }

    /** Without the break sound nor particles (BlockHarvestUtils.naturallyRemoveBlock, BlockOperations.setBlock). */
    @Override
    public List<ItemAmount> breakQuietly(BlockPos pos) {
        return breakBlock(pos, SetBlockSettings.NO_DROP_ITEMS | QUIET);
    }

    private List<ItemAmount> breakBlock(BlockPos pos, int settings) {
        try {
            return breaker.breakBlock(pos, settings);
        } catch (RuntimeException e) {
            fail("breakBlock", pos, e);
            return List.of();
        }
    }

    /** Drops {@code items} at the block like a broken block's drops (see {@link HytaleBlocks#drop}). */
    @Override
    public void drop(BlockPos pos, List<ItemAmount> items) {
        try {
            if (!items.isEmpty() && section(pos) != null) {
                drops.drop(pos, items);
            }
        } catch (RuntimeException e) {
            fail("drop", pos, e);
        }
    }

    /** See {@link BenchTiers#set}; false if the chunk is unloaded, the block is no bench, or Hytale fails. */
    @Override
    public boolean setBenchTier(BlockPos pos, int tier) {
        try {
            return BenchTiers.set(world, pos, tier);
        } catch (RuntimeException e) {
            fail("setBenchTier", pos, e);
            return false;
        }
    }

    private @Nullable Ref<ChunkStore> section(BlockPos pos) {
        return HytaleSections.section(world, pos);
    }

    private void fail(String op, BlockPos pos, RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("WorldBlocks.%s failed at %s", op, pos);
        warned = true;
    }
}
