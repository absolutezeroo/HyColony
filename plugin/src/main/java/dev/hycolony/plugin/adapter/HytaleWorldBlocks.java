package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.VariantRotation;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.CustomTemplateConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.builtin.StairLikeConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.joml.Vector3i;
import org.jspecify.annotations.Nullable;

/**
 * WorldBlocks over the section API (cheat sheet § 1). Never loads a chunk, never throws. Fluids are reported as the
 * pseudo-key {@code ~fluid:<FluidId>}; state variants ({@code *…}) are reported as their base block, except
 * connected-block shapes ({@link #blockKey}). A filler cell holds its origin's block id and rotation
 * ({@code FillerBlockUtil.setFillerBlocksAt}), so it reports the origin's
 * key: a hut's filler cells read as the hut, which the catalog calls UNBREAKABLE. A block that cannot rotate
 * ({@code VariantRotation.None}) reads as rotation 0, like its blueprint entry. World thread only.
 */
public final class HytaleWorldBlocks implements WorldBlocks {
    /** A creative paste's settings: no block particles, no block sound. */
    private static final int QUIET = SetBlockSettings.NO_SEND_PARTICLES | SetBlockSettings.NO_SEND_AUDIO;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Pseudo-key prefix for fluids, shared with {@link HytaleItemCatalog}. */
    static final String FLUID_PREFIX = "~fluid:";

    private static final int ROTATIONS = 64; // RotationTuple.VALUES.length

    private final World world;
    private final Set<String> hutBlockIds;
    private final HytaleBlocks drops;
    /** {@code get} is hot: one Optional per (block runtime id, rotation index), built once. */
    private Optional<BlockState>[][] blockCache = newCache(1024);

    private Optional<BlockState>[] fluidCache = newRow(64);
    private boolean warned;

    /** {@code hutBlockIds}: the id-map's hut block ids; never broken nor built over. */
    public HytaleWorldBlocks(World world, Set<String> hutBlockIds) {
        this.world = world;
        this.hutBlockIds = Set.copyOf(hutBlockIds);
        this.drops = new HytaleBlocks(world);
    }

    private boolean isHut(BlockType type) {
        return hutBlockIds.contains(type.getId())
                || (type.getDefaultStateKey() != null && hutBlockIds.contains(type.getDefaultStateKey()));
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
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
                    return fluidState(fluid);
                }
            }
            return blockState(id, blocks.getRotationIndex(pos.x(), pos.y(), pos.z()));
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
                Fluid fluid = Fluid.getAssetMap().getAsset(key.substring(FLUID_PREFIX.length()));
                if (fluid == null || fluid == Fluid.EMPTY) {
                    return false;
                }
                store.ensureAndGetComponent(sec, FluidSection.getComponentType())
                        .setFluid(pos.x(), pos.y(), pos.z(), fluid, (byte) fluid.getMaxFluidLevel());
                return true;
            }
            int id = BlockType.getAssetMap().getIndex(key);
            BlockType type =
                    id == Integer.MIN_VALUE ? null : BlockType.getAssetMap().getAsset(id);
            BlockSection blocks = store.getComponent(sec, BlockSection.getComponentType());
            if (type == null || blocks == null) {
                return false;
            }
            // The builder mined the spot first, so leftovers are replaced, except a hut (a multi-cell block's hitbox
            // may reach one). The check also fails if part of the hitbox is in an unloaded section.
            if (!BlockOperations.testPlaceBlock(
                    store,
                    blocks,
                    pos.x(),
                    pos.y(),
                    pos.z(),
                    type,
                    state.rotation(),
                    (x, y, z, other, rot, filler) -> !isHut(other))) {
                return false;
            }
            BlockOperations.setBlock(
                    world.getChunkStore(), sec, pos.x(), pos.y(), pos.z(), id, type, state.rotation(), 0, settings);
            if (type.getMaterial() == BlockMaterial.Solid) {
                FluidSection fluids = store.getComponent(sec, FluidSection.getComponentType());
                if (fluids != null && fluids.getFluidId(pos.x(), pos.y(), pos.z()) != Fluid.EMPTY_ID) {
                    fluids.setFluid(pos.x(), pos.y(), pos.z(), Fluid.EMPTY_ID, (byte) 0);
                }
            }
            return blocks.get(pos.x(), pos.y(), pos.z()) == id;
        } catch (RuntimeException e) {
            fail("place", pos, e);
            return false;
        }
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
            Ref<ChunkStore> sec = section(pos);
            if (sec == null) {
                return List.of();
            }
            Store<ChunkStore> store = world.getChunkStore().getStore();
            BlockSection blocks = store.getComponent(sec, BlockSection.getComponentType());
            if (blocks == null) {
                return List.of();
            }
            int x = pos.x(), y = pos.y(), z = pos.z();
            int id = blocks.get(x, y, z);
            if (id == BlockType.EMPTY_ID) {
                FluidSection fluids = store.getComponent(sec, FluidSection.getComponentType());
                if (fluids != null && fluids.getFluidId(x, y, z) != Fluid.EMPTY_ID) {
                    fluids.setFluid(x, y, z, Fluid.EMPTY_ID, (byte) 0); // a fluid drops nothing
                }
                return List.of();
            }
            BlockType type = BlockType.getAssetMap().getAsset(id);
            if (type == null || type == BlockType.EMPTY || isHut(type)) {
                return List.of(); // a hut (origin or filler cell) only goes through the hut systems
            }
            // A filler cell belongs to its origin block: the origin holds the container, and the whole block goes.
            int filler = blocks.getFiller(x, y, z);
            int ox = x - FillerBlockUtil.unpackX(filler),
                    oy = y - FillerBlockUtil.unpackY(filler),
                    oz = z - FillerBlockUtil.unpackZ(filler);
            Ref<ChunkStore> originSec = filler == 0 ? sec : section(new BlockPos(ox, oy, oz));
            if (originSec == null) {
                return List.of(); // origin unloaded: Hytale would not remove it either, so no drops (no duplication)
            }
            BlockSection originBlocks = store.getComponent(originSec, BlockSection.getComponentType());
            // An orphan filler (its origin is another block) is only cleared: it drops nothing.
            boolean orphan = originBlocks == null || originBlocks.get(ox, oy, oz) != id;
            List<ItemStack> out = new ArrayList<>();
            if (!orphan) {
                ItemContainerBlock container =
                        BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, ox, oy, oz);
                if (container != null) {
                    // Emptied before removal, else the removal system drops it on the ground. No filter: all of it.
                    out.addAll(container.getItemContainer().dropAllItemStacks(false));
                }
                out.addAll(HytaleBlocks.drops(type));
            }
            BlockHarvestUtils.naturallyRemoveBlock(
                    new Vector3i(x, y, z),
                    type,
                    filler,
                    0,
                    null,
                    null,
                    settings,
                    sec,
                    world.getEntityStore().getStore(),
                    store);
            List<ItemAmount> amounts = new ArrayList<>(out.size());
            for (ItemStack s : out) {
                if (!ItemStack.isEmpty(s)) {
                    amounts.add(new ItemAmount(new ItemKey(s.getItemId()), s.getQuantity()));
                }
            }
            return amounts;
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

    private @Nullable Ref<ChunkStore> section(BlockPos pos) {
        Ref<ChunkStore> sec = world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
        return sec != null && sec.isValid() ? sec : null;
    }

    private Optional<BlockState> blockState(int id, int rotation) {
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
     * fence Corner/T/Cross) keeps its variant id, as vanilla prefab pasting writes it; any other state variant
     * ({@code *…}, e.g. an open door or chest) is its base block, so a player's interaction is not rebuilt.
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

    private Optional<BlockState> fluidState(int fluid) {
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

    private void fail(String op, BlockPos pos, RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("WorldBlocks.%s failed at %s", op, pos);
        warned = true;
    }
}
