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

/**
 * Works the block the structure step chose: breaks it (MC doMining + mineBlock, with a tool when one is needed) or
 * places the planned block (MC EntityAIStructureBuilder.placeBlock).
 */
final class BuilderBlockWork {
    private static final System.Logger LOG = System.getLogger(BuilderAI.class.getName());

    static final double XP_PER_BLOCK = 0.05;

    private final BuilderContext ctx;
    private final BuilderGathering gathering;
    private BlockPos mineTarget;
    private boolean mineDelayed;

    BuilderBlockWork(BuilderContext ctx, BuilderGathering gathering) {
        this.ctx = ctx;
        this.gathering = gathering;
    }

    /** The position at {@code i} needs work: mine it first, or place its block once the item is at hand. */
    BuilderState work(Stage stage, int i) {
        BlockPos pos = ctx.site().positions(stage).get(i);
        if (stage == Stage.CLEAR || stage == Stage.REMOVE || ctx.scan().mustMineFirst(pos)) {
            return startMining(pos);
        }
        BlueprintEntry e = ctx.site().entry(stage, i);
        ItemKey item = ctx.catalog().itemForBlock(e.state().key()).orElse(null); // none: free to place
        if (item != null
                && !ctx.site().order().free()
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
    BuilderState mine() {
        WorkOrder order = ctx.site().order();
        boolean clearing = order != null && order.stage() == Stage.CLEAR;
        BlockState state = stillToBreak(clearing);
        if (state == null) {
            mineTarget = null;
            return BuilderState.BUILDING_STEP;
        }
        BlockPos pos = mineTarget;
        ToolType type = ctx.catalog().toolFor(state.key()).orElse(null);
        ItemKey tool = type == null ? null : ctx.stock().toolInInventory(type);
        if (type != null && tool == null) {
            return fetchTool(type);
        }
        if (!ctx.walkToWork(pos)) {
            return null;
        }
        if (!mineDelayed) {
            startBreaking(pos, state, tool);
            return null;
        }
        breakBlock(pos, state, tool, clearing);
        return BuilderState.BUILDING_STEP;
    }

    /** The mine target's block while it still has to go (CLEAR also takes fluids), else null. */
    private BlockState stillToBreak(boolean clearing) {
        BlockState state =
                mineTarget == null ? null : ctx.blocks().get(mineTarget).orElse(null);
        if (state == null) {
            return null;
        }
        return (clearing ? ctx.scan().clearable(state) : ctx.scan().mineable(state)) ? state : null;
    }

    /** MC checkForNeededTool: the hut's, else a tool request. */
    private BuilderState fetchTool(ToolType type) {
        ItemKey inHut = ctx.stock().toolInHut(type);
        if (inHut == null) {
            ctx.stock().requestTool(type);
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

    private void startBreaking(BlockPos pos, BlockState state, ItemKey tool) {
        mineDelayed = true;
        ctx.gestures().lookAt(pos);
        ctx.gestures().hold(tool);
        ctx.gestures()
                .startDelay(
                        BuilderTimings.breakDelay(
                                ctx.citizen().skills().level(ctx.secondary()),
                                ctx.catalog().hardness(state.key()),
                                ctx.stock().toolSpeed(tool)),
                        BodyAnimation.MINE);
    }

    private void breakBlock(BlockPos pos, BlockState state, ItemKey tool, boolean clearing) {
        mineDelayed = false;
        mineTarget = null;
        List<ItemAmount> drops = ctx.blocks().breakBlock(pos);
        if (clearing && ctx.catalog().kind(state.key()) == BlockKind.FLUID) {
            // ponytail: one removal per fluid cell; a neighbouring source may flow back, and looping on it would
            // never end. Refill after CLEAR is left as is (SOLID overwrites it, decorations sit in it).
            ctx.site().progress(Stage.CLEAR, ctx.site().order().progressIndex() + 1);
        }
        // MC: a rack that leaves the world leaves its building's containers (TileEntityRack removal).
        ctx.colony().buildings().owningContainer(pos).ifPresent(b -> b.removeContainer(pos));
        if (!ctx.catalog().isOre(state.key())) { // MC EntityAIStructureBuilder.mineBlock: getDrops = !isOre
            ctx.stock().storeDrops(drops);
        }
        if (tool != null
                && ctx.job() instanceof BuilderJob b
                && b.wear(tool, ctx.catalog().durability(tool))) {
            ctx.stock().inventory().extract(tool, 1); // worn out: it breaks
        }
        ctx.award(XP_PER_BLOCK);
        ctx.job().incrementActions();
    }

    private void place(Stage stage, int i, BlockPos pos, BlueprintEntry e, ItemKey item) {
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
            if (!ctx.site().order().free()) {
                ctx.stock().inventory().extract(item, 1);
            }
            ctx.resources().onPlaced(item); // a free order still counts it, for the progress shown
        }
        if (e.hasContainer()) {
            ctx.site().target().addContainer(pos); // MC: racks the builder places become the building's containers
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
