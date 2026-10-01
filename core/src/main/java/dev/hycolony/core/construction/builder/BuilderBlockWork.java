package dev.hycolony.core.construction.builder;

import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.resources.EntryCost;
import dev.hycolony.core.construction.shared.BuilderTimings;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.job.JobStatus;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.BodyAnimation;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * Works the block the structure step chose: breaks it (MC doMining + mineBlock, with a tool when one is needed) or
 * places the planned block with every item it costs (MC EntityAIStructureBuilder.placeBlock); a placed crafting bench
 * gets its planned tier and joins the hut.
 */
final class BuilderBlockWork {
    private static final System.Logger LOG = System.getLogger(BuilderAI.class.getName());

    static final double XP_PER_BLOCK = 0.05;

    private final BuilderContext ctx;
    private final BuilderGathering gathering;
    private @Nullable BlockPos mineTarget;
    private boolean mineDelayed;
    private final PlannedBlocks planned;

    BuilderBlockWork(BuilderContext ctx, BuilderGathering gathering, PlannedBlocks planned) {
        this.ctx = ctx;
        this.gathering = gathering;
        this.planned = planned;
    }

    /**
     * The position at {@code i} needs work: mine it (CLEAR, REMOVE), remove a leftover (CLEAR_LEFTOVERS), or place its
     * block once every item it costs is at hand (MC hasListOfResInInvOrRequest), replacing what is there; a free
     * order places without items.
     */
    @Nullable
    BuilderState work(Stage stage, int i) {
        BlockPos pos = ctx.site().positions(stage).get(i);
        if (stage == Stage.CLEAR || stage == Stage.REMOVE) {
            return startMining(pos);
        }
        if (stage == Stage.CLEAR_LEFTOVERS) {
            if (ctx.walkToWork(pos)) {
                clearLeftover(stage, i, pos);
            }
            return null;
        }
        BlueprintEntry e = ctx.site().entry(stage, i);
        boolean turn = ctx.site().plan().onlyTurns(e, ctx.blocks());
        List<ItemAmount> cost = turn ? List.of() : EntryCost.of(e, ctx.catalog(), ctx.recipes()); // empty: free
        Optional<ItemAmount> lacking = ctx.site().loadedOrder().free() ? Optional.empty() : lacking(cost);
        if (lacking.isPresent()) {
            return gathering.missing(lacking.get(), i);
        }
        if (!ctx.walkToWork(pos)) {
            return null;
        }
        place(stage, i, pos, e, cost);
        return null;
    }

    /** The first item of {@code cost} the inventory holds fewer of than asked; empty once all are at hand. */
    private Optional<ItemAmount> lacking(List<ItemAmount> cost) {
        for (ItemAmount a : cost) {
            if (ctx.stock().inventory().count(a.item()) < a.count()) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
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
        ctx.citizen().setJobStatus(JobStatus.WORKING); // MC holdEfficientTool: a tool at hand, or none needed
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
            ctx.citizen().setJobStatus(JobStatus.STUCK); // MC checkForToolOrWeapon: no tool to work with
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
        forgetRegistered(pos);
        if (!ctx.catalog().isOre(state.key())) { // MC EntityAIStructureBuilder.mineBlock: getDrops = !isOre
            ctx.stock().storeDrops(drops);
        }
        if (tool != null) {
            // MC damageItemInHand: 1 per block; at its durability the tool breaks, no message (the next block asks).
            ctx.stock().inventory().damage(toolSlot.getAsInt(), 1, ctx.catalog().durability(tool));
        }
        ctx.award(XP_PER_BLOCK);
        ctx.job().incrementActions();
        ctx.job().decreaseSaturationForContinuousAction(); // MC AbstractEntityAIStructure, after mineBlock
    }

    private void place(Stage stage, int i, BlockPos pos, BlueprintEntry e, List<ItemAmount> cost) {
        ctx.gestures().lookAt(pos); // MC BuildingStructureHandler.prePlacementLogic: faceBlock
        if (!ctx.site().plan().onlyTurns(e, ctx.blocks()) && ctx.scan().mustMineFirst(pos)) {
            removeForReplace(pos);
        }
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
        consume(cost);
        planned.placed(pos, e);
        ctx.award(XP_PER_BLOCK);
        ctx.job().incrementActions();
        ctx.site().progress(stage, i + 1);
        ctx.gestures().hold(cost.isEmpty() ? null : cost.getFirst().item());
        ctx.gestures()
                .startDelay(
                        BuilderTimings.placeDelay(ctx.citizen().skills().level(ctx.primary())), BodyAnimation.BUILD);
    }

    /**
     * MC CLEAR_NON_SOLIDS places the plan's air through Structurize's block placement: the leftover goes as a
     * replaced block ({@link #removeForReplace}), with the placement's experience and delay.
     */
    private void clearLeftover(Stage stage, int i, BlockPos pos) {
        ctx.gestures().lookAt(pos);
        removeForReplace(pos);
        ctx.award(XP_PER_BLOCK);
        ctx.job().incrementActions();
        ctx.site().progress(stage, i + 1);
        ctx.gestures().hold(null);
        ctx.gestures()
                .startDelay(
                        BuilderTimings.placeDelay(ctx.citizen().skills().level(ctx.primary())), BodyAnimation.BUILD);
    }

    /**
     * Structurize IPlacementHandler.handleRemoval (StructurePlacer, allowReplace outside CLEAR): the block in the way
     * goes without a mining delay, tool wear nor experience; its drops, ores included, are kept unless the order is
     * free (MC isCreative).
     */
    private void removeForReplace(BlockPos pos) {
        List<ItemAmount> drops = ctx.blocks().breakBlock(pos);
        forgetRegistered(pos);
        if (!ctx.site().loadedOrder().free()) {
            ctx.stock().storeDrops(drops);
        }
    }

    /**
     * MC: a rack that leaves the world leaves its building's containers (TileEntityRack removal), and a bench its
     * hut's benches (FurnaceUserModule.removeFromFurnaces once the furnace is gone).
     */
    private void forgetRegistered(BlockPos pos) {
        ctx.colony()
                .buildings()
                .owningContainer(pos)
                .ifPresent(b -> b.registeredBlocks().removeContainer(pos));
        ctx.colony().buildings().all().forEach(b -> b.registeredBlocks().removeWorkstation(pos));
    }

    /** MC StructurePlacer consume + reduceNeededResources: every item of the cell, each unit counted as placed. */
    private void consume(List<ItemAmount> cost) {
        boolean free = ctx.site().loadedOrder().free();
        for (ItemAmount a : cost) {
            if (!free) {
                ctx.stock().inventory().extract(a.item(), a.count());
            }
            for (int unit = 0; unit < a.count(); unit++) {
                ctx.resources().onPlaced(a.item()); // a free order still counts it, for the progress shown
            }
        }
    }
}
