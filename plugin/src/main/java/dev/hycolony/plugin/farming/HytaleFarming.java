package dev.hycolony.plugin.farming;

import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.farming.CropState;
import dev.hycolony.core.farming.FarmingAccess;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * FarmingAccess over Hytale's soil and crops (sp3b-hytale-farming, plugin-b-api § 27). Blocks are placed through
 * {@link WorldBlocks}, which keeps a new block's entity (TilledSoil, FarmingBlock) so it decays and grows like a
 * player's. World thread only; never throws.
 */
public final class HytaleFarming implements FarmingAccess {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    // A Hytale state block id: "*<block>_State_Definitions_<state>".
    private static final String STATE = "_State_Definitions_";
    // A HyDomum variant id: "<template>__<material>..." (dev.hydomum.api.VariantKey.blockTypeKey).
    private static final String VARIANT = "__";

    private final WorldBlocks blocks;
    private final FarmBlocks farm;
    private final FarmingIds ids;
    private final Set<String> tillable;
    private final Set<String> crops;
    private final Set<String> barriers;
    private final String fieldBlock;
    private boolean warned;

    public HytaleFarming(World world, WorldBlocks blocks, FarmingIds ids, String fieldBlock) {
        this.blocks = blocks;
        this.farm = new FarmBlocks(world);
        this.ids = ids;
        this.tillable = Set.copyOf(ids.tillableSoils());
        this.crops = Set.copyOf(ids.seedCrops().values());
        this.barriers = Set.copyOf(ids.barriers());
        this.fieldBlock = fieldBlock;
    }

    @Override
    public boolean isTillable(BlockPos pos) {
        return key(pos).filter(tillable::contains).isPresent();
    }

    @Override
    public boolean isTilled(BlockPos pos) {
        return key(pos).filter(ids.tilledSoil()::equals).isPresent();
    }

    @Override
    public boolean till(BlockPos pos) {
        return safe("till", () -> isTillable(pos) && blocks.place(pos, state(ids.tilledSoil()), false));
    }

    @Override
    public boolean isFertilized(BlockPos pos) {
        return safe("isFertilized", () -> {
            TilledSoilBlock soil = farm.soil(pos);
            return soil != null && soil.isFertilized();
        });
    }

    @Override
    public boolean fertilize(BlockPos pos) {
        return safe("fertilize", () -> farm.fertilize(pos));
    }

    @Override
    public CropState crop(BlockPos pos) {
        if (key(pos).filter(crops::contains).isEmpty()) {
            return CropState.NONE;
        }
        try {
            return FarmBlocks.isHarvestable(farm.typeAt(pos)) ? CropState.MATURE : CropState.GROWING;
        } catch (RuntimeException e) {
            fail("crop", e);
            return CropState.GROWING;
        }
    }

    /** Only on air: the crop block of {@code seed}, from its first stage. */
    @Override
    public boolean plant(BlockPos pos, ItemKey seed) {
        String crop = ids.seedCrops().get(seed.id());
        return crop != null && safe("plant", () -> key(pos).isEmpty() && blocks.place(pos, state(crop), false));
    }

    /**
     * The harvest drops; a normal crop leaves the block empty, an eternal one is placed again from its first stage.
     * Deviation from vanilla Hytale: an eternal crop restarts at its first stage, where Hytale's HarvestCrop sends it
     * back to Stage1, a little further on.
     */
    @Override
    public List<ItemAmount> harvest(BlockPos pos) {
        try {
            BlockType type = farm.typeAt(pos);
            Optional<String> crop = key(pos).filter(crops::contains);
            if (type == null || crop.isEmpty() || !FarmBlocks.isHarvestable(type)) {
                return List.of();
            }
            List<ItemAmount> drops = FarmBlocks.harvestDrops(type);
            blocks.breakBlock(pos); // its soft drops are the harvest's for a normal crop: they are not kept twice
            if (crop.get().endsWith("_Eternal")) {
                blocks.place(pos, state(crop.get()), false);
            }
            return drops;
        } catch (RuntimeException e) {
            fail("harvest", e);
            return List.of();
        }
    }

    /**
     * Whether the block at pos bounds a field: a fence, gate or wall of the id-map's list, in any of its shapes or
     * states (a corner, an open gate) or HyDomum materials (MC FarmField.isValidDelimiter: any FenceBlock,
     * FenceGateBlock or WallBlock).
     */
    @Override
    public boolean isFieldBarrier(BlockPos pos) {
        return key(pos).map(HytaleFarming::barrierKey)
                .filter(barriers::contains)
                .isPresent();
    }

    /** The block a state id belongs to, then the HyDomum shape a variant id belongs to; any other id as it is. */
    static String barrierKey(String id) {
        int state = id.indexOf(STATE);
        String block = id.startsWith("*") && state > 0 ? id.substring(1, state) : id;
        int variant = block.indexOf(VARIANT);
        return variant > 0 ? block.substring(0, variant) : block;
    }

    @Override
    public List<ItemKey> seeds() {
        return ids.seedCrops().keySet().stream().sorted().map(ItemKey::new).toList();
    }

    @Override
    public boolean isFieldBlock(BlockPos pos) {
        return key(pos).filter(fieldBlock::equals).isPresent();
    }

    @Override
    public ItemKey fertilizerItem() {
        return new ItemKey(ids.fertilizerTool());
    }

    /** The base block id at {@code pos} (a crop's stage is its crop), empty for air or an unloaded chunk. */
    private Optional<String> key(BlockPos pos) {
        return blocks.get(pos).map(s -> s.key().id()).filter(id -> !BlockType.EMPTY_KEY.equals(id));
    }

    private static BlockState state(String blockId) {
        return new BlockState(new BlockKey(blockId), 0);
    }

    private boolean safe(String op, Supplier<Boolean> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            fail(op, e);
            return false;
        }
    }

    private void fail(String op, RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("Farming.%s failed", op);
        warned = true;
    }
}
