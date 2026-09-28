package dev.hycolony.core.construction.builder;

import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.BodyAnimation;
import java.util.List;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * Works the block the structure step chose: breaks it (MC doMining + mineBlock, with a tool when one is needed) or
 * places the planned block (MC EntityAIStructureBuilder.placeBlock).
 */
final class BuilderBlockWork {
    private static final System.Logger LOG = System.getLogger(BuilderAI.class.getName());

    static final double XP_PER_BLOCK = 0.05;

    private final BuilderContext ctx;
    private final BuilderGathering gathering;
    private @Nullable BlockPos mineTarget;
    private boolean mineDelayed;

    BuilderBlockWork(BuilderContext ctx, BuilderGathering gathering) {
        this.ctx = ctx;
        this.gathering = gathering;
    }

    /** The position at {@code i} needs work: mine it first, or place its block once the item is at hand. */
    @Nullable
    BuilderState work(Stage stage, int i) {
        BlockPos pos = ctx.site().positions(stage).get(i);
        if (stage == Stage.CLEAR
                || stage == Stage.REMOVE
                || stage == Stage.CLEAR_LEFTOVERS
                || ctx.scan().mustMineFirst(pos)) {
            return startMining(pos);
        }
        BlueprintEntry e = ctx.site().entry(stage, i);
        ItemKey item = ctx.catalog().itemForBlock(e.state().key()).orElse(null); // none: free to place
        if (item != null
                && !ctx.site().loadedOrder().free()
                && ctx.stock().inventory().count(item) == 0) {
            return gathering.missing(item, i);
        }
        if (!ctx.walkToWork(pos)) {
            return null;
        }
        place(stage, i, pos, e, item);
        return null;
    }

    private BuilderState startMining(BlockPos pos) {
        mineTarget = pos;
        mineDelayed = false;
        return BuilderState.MINE_BLOCK;
    }

    void reset() {
        mineTarget = null;
        mineDelayed = false;
    }

    /** A tool if one is needed, a first pass that waits (the break delay), a second that breaks. */
    @Nullable
    BuilderState mine() {
        WorkOrder order = ctx.site().order();
        boolean clearing = order != null && order.stage() == Stage.CLEAR;
        BlockPos pos = mineTarget;
        BlockState state = pos == null ? null : stillToBreak(pos, clearing);
        if (pos == null || state == null) {
            mineTarget = null;
            return BuilderState.BUILDING_STEP;
        }
        ToolType type = ctx.catalog().toolFor(state.key()).orElse(null);
        OptionalInt toolSlot = type == null ? OptionalInt.empty() : ctx.stock().toolInInventory(type);
        if (type != null && toolSlot.isEmpty()) {
            return fetchTool(type);
        }
        if (!ctx.walkToWork(pos)) {
            return null;
        }
        if (!mineDelayed) {
            startBreaking(pos, state, toolSlot);
            return null;
        }
        breakBlock(pos, state, toolSlot, clearing);
        return BuilderState.BUILDING_STEP;
    }

    /** The block at {@code pos} while it still has to go (CLEAR also takes fluids), else null. */
    private @Nullable BlockState stillToBreak(BlockPos pos, boolean clearing) {
        BlockState state = ctx.blocks().get(pos).orElse(null);
        if (state == null) {
            return null;
        }
        return (clearing ? ctx.scan().clearable(state) : ctx.scan().mineable(state)) ? state : null;
    }

    /** MC checkForNeededTool: the hut's, else a tool request. */
    private @Nullable BuilderState fetchTool(ToolType type) {
        ItemKey inHut = ctx.stock().toolInHut(type).orElse(null);
        if (inHut == null) {
            ctx.tools().requestTool(type);
            return BuilderState.NEEDS_ITEM;
        }
        if (!ctx.walkToHut()) {
            return null;
        }
        if (ctx.stock().take(inHut, 1) > 0) {
            return null;
        }
        ctx.stock().dumpNow();
        return BuilderState.INVENTORY_FULL;
    }

    /** The item in {@code slot} of the inventory; null for no slot (bare hands). */
    private @Nullable ItemKey itemIn(OptionalInt slot) {
        return slot.isEmpty()
                ? null
                : ctx.stock()
                        .inventory()
                        .slot(slot.getAsInt())
                        .map(ItemAmount::item)
                        .orElse(null);
    }

    private void startBreaking(BlockPos pos, BlockState state, OptionalInt toolSlot) {
        ItemKey tool = itemIn(toolSlot);
        mineDelayed = true;
        ctx.gestures().lookAt(pos);
        ctx.gestures().hold(tool);
        ctx.gestures()
                .startMining(
                        BuilderTimings.breakDelay(
                                ctx.citizen().skills().level(ctx.secondary()),
                                ctx.catalog().hardness(state.key()),
                                ctx.stock().toolSpeed(tool)),
                        pos);
    }

    /** MC mineBlock: breaks the block, keeps its drops, then wears the tool in hand by 1 (damageItemInHand). */
    private void breakBlock(BlockPos pos, BlockState state, OptionalInt toolSlot, boolean clearing) {
        ItemKey tool = itemIn(toolSlot);
        mineDelayed = false;
        mineTarget = null;
        List<ItemAmount> drops = ctx.blocks().breakBlock(pos);
        if (clearing && ctx.catalog().kind(state.key()) == BlockKind.FLUID) {
            // Deviation from MC: one removal per fluid cell; a neighbouring source may flow back, and looping on it
            // would never end. Refill after CLEAR is left as is (SOLID overwrites it, decorations sit in it).
            ctx.site().progress(Stage.CLEAR, ctx.site().loadedOrder().progressIndex() + 1);
        }
        // MC: a rack that leaves the world leaves its building's containers (TileEntityRack removal).
        ctx.colony()
                .buildings()
                .owningContainer(pos)
                .ifPresent(b -> b.registeredBlocks().removeContainer(pos));
        if (!ctx.catalog().isOre(state.key())) { // MC EntityAIStructureBuilder.mineBlock: getDrops = !isOre
            ctx.stock().storeDrops(drops);
        }
        if (tool != null) {
            // MC damageItemInHand: 1 per block; at its durability the tool breaks, no message (the next block asks).
            ctx.stock().inventory().damage(toolSlot.getAsInt(), 1, ctx.catalog().durability(tool));
        }
        ctx.award(XP_PER_BLOCK);
        ctx.job().incrementActions();
    }

    private void place(Stage stage, int i, BlockPos pos, BlueprintEntry e, @Nullable ItemKey item) {
        ctx.gestures().lookAt(pos); // MC BuildingStructureHandler.prePlacementLogic: faceBlock
        if (!ctx.blocks().place(pos, e.state(), e.hasContainer())) {
            LOG.log(
                    System.Logger.Level.WARNING,
                    "Builder {0}: failed to place {1} at {2}; skipped",
                    ctx.citizen().name(),
                    e.state().key().id(),
                    pos);
            ctx.site().progress(stage, i + 1);
            return;
        }
        if (item != null) {
            if (!ctx.site().loadedOrder().free()) {
                ctx.stock().inventory().extract(item, 1);
            }
            ctx.resources().onPlaced(item); // a free order still counts it, for the progress shown
        }
        if (e.hasContainer()) {
            // MC: racks the builder places become the building's containers
            ctx.site().target().registeredBlocks().addContainer(pos);
        }
        ctx.award(XP_PER_BLOCK);
        ctx.job().incrementActions();
        ctx.site().progress(stage, i + 1);
        ctx.gestures().hold(item);
        ctx.gestures()
                .startDelay(
                        BuilderTimings.placeDelay(ctx.citizen().skills().level(ctx.primary())), BodyAnimation.BUILD);
    }
}
