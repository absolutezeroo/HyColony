package dev.hycolony.core.farming.job;

import dev.hycolony.core.farming.CropState;
import dev.hycolony.core.farming.FarmingAccess;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.hut.FieldWalk;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One pass of the farmer over its current field, cell by cell (MC EntityAIWorkFarmer.workAtField with hoeIfAble,
 * tryToPlant and harvestIfAble), plus Hytale's fertilizer on each tilled cell (deviation 1 of the SP3b-2 spec).
 */
final class FieldPass {
    private static final System.Logger LOG = System.getLogger(FieldPass.class.getName());

    /** MC XP_PER_BLOCK, given by mineBlock for a block broken. */
    static final double XP_PER_BLOCK = 0.05;

    /** MC XP_PER_HARVEST, on top of the block's. */
    static final double XP_PER_HARVEST = 0.5;

    /** MC DEFAULT_DELAY: ticks after a cell, shortened by the primary skill. */
    static final int DEFAULT_DELAY = 40;

    /** MC walkToSafePos: the farmer works a cell from up to 4 blocks away. */
    static final int CELL_RANGE = 4;

    private final FarmWorkContext ctx;
    private final FieldScan scan;
    private final FarmWork work;
    private boolean didWork;
    private boolean warned;

    FieldPass(FarmWorkContext ctx, FieldScan scan, FarmWork work) {
        this.ctx = ctx;
        this.scan = scan;
        this.work = work;
    }

    /** MC workAtField: works the current cell, then moves to the next; IDLE once the pass is over. */
    FarmerState work(FarmerState state) {
        Optional<FarmField> current = ctx.fields().currentField(ctx.colony(), ctx.hut());
        if (current.isEmpty()) {
            return FarmerState.IDLE; // the field was broken or freed
        }
        FarmField field = current.get();
        FieldWalk walk = ctx.fields().walk();
        Optional<int[]> offset = walk.offset();
        if (offset.isPresent()) {
            BlockPos column = field.pos().offset(offset.get()[0], -1, offset.get()[1]);
            if (!ctx.walker().walkTo(column.offset(0, 1, 0), CELL_RANGE)) {
                return state;
            }
            holdTool(state, field);
            if (!workCell(state, field, column)) {
                return FarmerState.PREPARING;
            }
            walk.setPrevPos(Optional.of(column));
            work.delay().set(levelDelay());
        }
        if (!walk.advance(field.radii())) {
            endPass(field, walk);
            return FarmerState.IDLE;
        }
        return state;
    }

    /**
     * MC equipHoe, for every stage. Deviation from MC (asked for): the seed bag is held while planting and carried, as
     * a Hytale player sows with the seed in hand.
     */
    private void holdTool(FarmerState state, FarmField field) {
        Optional<ItemKey> seed =
                field.seed().filter(s -> ctx.stock().inventory().count(s) > 0);
        if (state == FarmerState.FARMER_PLANT && seed.isPresent()) {
            ctx.hands().hold(seed);
        } else {
            ctx.hands().holdTool(ctx.stock(), ToolType.HOE);
        }
    }

    /** The cell's work for {@code state}; false only when the seed ran out (MC tryToPlant false → PREPARING). */
    private boolean workCell(FarmerState state, FarmField field, BlockPos column) {
        return switch (state) {
            case FARMER_HOE -> {
                scan.hoeable(column).ifPresent(this::hoe);
                yield true;
            }
            case FARMER_PLANT ->
                scan.plantable(column).map(s -> plant(field, s)).orElse(true);
            case FARMER_HARVEST -> {
                scan.harvestable(column).ifPresent(this::harvest);
                yield true;
            }
            default -> true;
        };
    }

    /**
     * MC hoeIfAble: without a hoe the cell is skipped (and one is asked for); otherwise, facing the soil (a deviation
     * asked for: MC only looks at a plant it mines there), the plant on the cell is broken (its drops fall, as MC's
     * destroyBlock), the soil tilled with a stroke and the till sound, the hoe worn by one, then the fertilizer.
     * Deviation from MC: any block on the cell is handled like MC's replaceable plants, without action nor XP, where MC
     * mines a non-replaceable one (flower, torch) into the inventory: the core has no "replaceable" flag.
     */
    private void hoe(BlockPos surface) {
        OptionalInt hoe = ctx.stock().firstToolInInventory(ToolType.HOE); // the slot equipHoe holds
        if (hoe.isEmpty()) {
            ctx.tools().requestTool(ToolType.HOE);
            return;
        }
        ctx.hands().face(surface);
        BlockPos above = surface.offset(0, 1, 0);
        WorldBlocks world = ctx.colony().context().ports().blocks();
        if (world.get(above).map(s -> catalog().kind(s.key()) != BlockKind.AIR).orElse(false)) {
            world.drop(above, world.breakBlock(above));
        }
        if (!farming().till(surface)) {
            return;
        }
        ctx.hands().swing(BodyAnimation.TILL);
        ctx.colony().context().ports().effects().tilled(surface);
        didWork = true;
        ItemKey tool =
                ctx.stock().inventory().slot(hoe.getAsInt()).orElseThrow().item();
        // Deviation from MC: no research yet, so no TOOL_DURABILITY chance to spare the tool (damageItemInHand).
        if (ctx.stock().inventory().damage(hoe.getAsInt(), 1, catalog().durability(tool))) {
            // MC damageItemInHand: the broken hoe leaves the hand empty; equipHoe takes the next one at the next cell
            ctx.hands().hold(Optional.empty());
        }
        ctx.job().decreaseSaturationForContinuousAction(); // MC EntityAIWorkFarmer, after tilling
        fertilize(surface);
    }

    /**
     * MC tryToPlant / plantCrop: false when the seed ran out; the seed's crop placed, one seed used. Deviation from MC:
     * no melon/pumpkin gap (Hytale's pumpkin has no stem); asked
     * for, the farmer faces the cell, plays the seed-placing gesture and the crop's placing sound, as a Hytale player
     * sows (MC's crop just appears). A cell the world refuses keeps its seed and logs a warning, once per farmer AI.
     */
    private boolean plant(FarmField field, BlockPos surface) {
        Optional<ItemKey> seed = field.seed();
        if (seed.isEmpty() || ctx.stock().inventory().count(seed.get()) <= 0) {
            return false;
        }
        fertilize(surface);
        BlockPos crop = surface.offset(0, 1, 0);
        ctx.hands().face(crop);
        if (!farming().plant(crop, seed.get())) {
            // The scan found the cell free: a refusal here is an adapter bug, which would leave the field unsown.
            LOG.log(
                    warned ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING,
                    "Farmer could not plant at " + crop);
            warned = true;
            return true;
        }
        ctx.hands().swing(BodyAnimation.PLANT);
        ctx.colony().context().ports().effects().blockPlaced(crop);
        ctx.job().decreaseSaturationForContinuousAction(); // MC plantCrop, once the crop is placed
        ctx.stock().inventory().extract(seed.get(), 1);
        didWork = true;
        return true;
    }

    /**
     * MC harvestIfAble / mineBlock: the tool the world gives the crop in hand ({@link #holdToolFor}), facing the crop,
     * a stroke on it (MC hitBlockWithToolInHand looks at it and swings), then the harvest drops go to the inventory;
     * one action, the block's and harvest XP, even without drops. Nothing more when the crop is still mature (the
     * harvest failed).
     */
    private void harvest(BlockPos surface) {
        BlockPos crop = surface.offset(0, 1, 0);
        holdToolFor(crop);
        ctx.hands().face(crop);
        ctx.hands().swing(BodyAnimation.MINE);
        ctx.colony().context().ports().effects().blockHit(crop, 1f);
        List<ItemAmount> drops = farming().harvest(crop);
        if (drops.isEmpty() && farming().crop(crop) == CropState.MATURE) {
            return;
        }
        ctx.stock().storeDrops(drops);
        ctx.job().incrementActions();
        ctx.award(XP_PER_BLOCK);
        ctx.award(XP_PER_HARVEST);
        didWork = true;
    }

    /**
     * MC holdEfficientTool(target): the slot of the worker's best tool for the block at {@code pos} as the world tells
     * it (ItemCatalog.toolFor), else an empty hand (removeHeldItem), the held slot left as it is. A Hytale crop needs
     * none (Template_Crop_Block: Gathering.Soft, no Breaking; sp3b-hytale-farming § 201), so the farmer harvests
     * bare-handed, as MC's for a crop of hardness 0.
     */
    private void holdToolFor(BlockPos pos) {
        OptionalInt slot = ctx.colony()
                .context()
                .ports()
                .blocks()
                .get(pos)
                .flatMap(s -> catalog().toolFor(s.key()))
                .map(ctx.stock()::toolInInventory)
                .orElse(OptionalInt.empty());
        if (slot.isPresent()) {
            ctx.hands().holdSlot(slot.getAsInt());
        } else {
            ctx.hands().hold(Optional.empty());
        }
    }

    /** Deviation from MC (compost, bone meal): one use of a carried fertilizer tool on an unfertilized cell. */
    private void fertilize(BlockPos surface) {
        if (!ctx.settings().fertilize() || farming().isFertilized(surface)) {
            return;
        }
        ItemKey fertilizer = farming().fertilizerItem();
        for (int i = 0; i < ctx.stock().inventory().size(); i++) {
            Optional<ItemAmount> slot = ctx.stock().inventory().slot(i);
            if (slot.filter(a -> a.item().equals(fertilizer) && !catalog().wornOut(a))
                            .isPresent()
                    && farming().fertilize(surface)) {
                ctx.stock().inventory().damage(i, 1, catalog().durability(fertilizer));
                return;
            }
        }
    }

    /** MC: the pass is over; dump, next stage, and the field is left for today when the pass worked. */
    private void endPass(FarmField field, FieldWalk walk) {
        work.requestDump();
        field.nextStage();
        ctx.colony().markDirty();
        work.endPass(didWork);
        didWork = false;
        walk.reset();
    }

    /** MC getLevelDelay: {@code max(1, 40 - primary / 2)} ticks. */
    private int levelDelay() {
        int primary = ctx.citizen().skills().level(ctx.workers().primary());
        return (int) Math.max(1, DEFAULT_DELAY - primary / 2.0);
    }

    private FarmingAccess farming() {
        return ctx.farming();
    }

    private ItemCatalog catalog() {
        return ctx.colony().context().ports().catalog();
    }
}
