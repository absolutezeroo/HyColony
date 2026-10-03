package dev.hycolony.core.construction.builder;

import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.work.WorkerMachine;
import dev.hycolony.core.kernel.ai.AIBlockingEventType;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.model.RequestState;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The builder's work AI. Port of MineColonies' AbstractEntityAIStructure, AbstractEntityAIStructureWithWorkOrder,
 * EntityAIStructureBuilder and the dump / NEEDS_ITEM parts of AbstractEntityAIBasic. The plan is built once per
 * order (on load); each step scans at most {@link #SCAN_LIMIT} positions from the order's saved progress. The steps
 * delegate the block work to {@link BuilderBlockWork}, the materials to {@link BuilderGathering} and the loading of an
 * order to {@link StructureLoader}.
 */
public final class BuilderAI implements JobAI {
    static final int SCAN_LIMIT = 10_000;
    static final double XP_EACH_BUILDING = 8;

    private final BuilderContext ctx;
    private final BuildSite site;
    private final BuilderBlockWork blockWork;
    private final PlannedBlocks planned;
    private final BuilderGathering gathering;
    private final StructureLoader loader;
    private final WorkerMachine<BuilderState> machine;
    /** Whether its last building step found the next cell of its plan unloaded, and waits for it. */
    private boolean waitingForChunk;
    /** Whether this AI loaded a structure yet (MC AbstractEntityAIStructureWithWorkOrder recalculated). */
    private boolean recalculated;

    BuilderAI(BuilderContext ctx) {
        this.ctx = ctx;
        this.site = ctx.site();
        this.gathering = new BuilderGathering(ctx);
        this.planned = new PlannedBlocks(ctx);
        this.blockWork = new BuilderBlockWork(ctx, gathering, planned);
        this.loader = new StructureLoader(ctx);
        this.machine = new WorkerMachine<>(
                BuilderState.IDLE,
                () -> "builder " + ctx.citizen().name(),
                ctx.gestures()::waiting,
                ctx.gestures()::pause);
        event(AIBlockingEventType.AI_BLOCKING, this::needsItem, () -> BuilderState.NEEDS_ITEM, 20);
        event(AIBlockingEventType.STATE_BLOCKING, this::orderLost, this::dropOrder, 1);
        event(AIBlockingEventType.STATE_BLOCKING, this::inventoryNeedsDump, () -> BuilderState.INVENTORY_FULL, 100);
        target(BuilderState.IDLE, this::idle, 10);
        target(BuilderState.START_WORKING, this::startWorking, 20);
        target(BuilderState.LOAD_STRUCTURE, this::loadStructure, 5);
        target(BuilderState.GATHERING_REQUIRED_MATERIALS, gathering::gather, 20);
        target(BuilderState.NEEDS_ITEM, gathering::waitForRequests, 40);
        target(BuilderState.BUILDING_STEP, this::structureStep, 5);
        target(BuilderState.MINE_BLOCK, blockWork::mine, 10);
        target(BuilderState.INVENTORY_FULL, this::dumpInventory, 20);
        target(BuilderState.COMPLETE_BUILD, this::completeBuild, 5);
    }

    private void event(AIBlockingEventType type, BooleanSupplier when, IStateSupplier<BuilderState> then, int rate) {
        machine.event(type, when, then, rate);
    }

    private void target(BuilderState s, IStateSupplier<BuilderState> action, int rate) {
        machine.target(s, action, rate);
    }

    @Override
    public void tick() {
        machine.tick();
    }

    @Override
    public int failures() {
        return machine.failures();
    }

    @Override
    public String stateName() {
        return machine.state().name();
    }

    /** The last exception the AI caught; empty while none did (the tests assert so). */
    Optional<RuntimeException> lastError() {
        return machine.lastError();
    }

    @Override
    public Optional<Msg> describe() {
        return Optional.of(BuilderActivity.describe(
                machine.state(),
                site.order(),
                ctx.walker().walking(),
                ctx.gestures().inHand()));
    }

    /** MC isOkayToEat. */
    @Override
    public boolean canBeInterrupted() {
        return switch (machine.state()) {
            case IDLE, START_WORKING, NEEDS_ITEM, GATHERING_REQUIRED_MATERIALS -> true;
            default -> false;
        };
    }

    /** MC AbstractAISkeleton.resetAI: back to IDLE, its walk forgotten; its order, plan and progress stay. */
    @Override
    public void resetAI() {
        machine.reset();
        ctx.walker().forgetWalk();
    }

    /** Waiting for the items it asked for (NEEDS_ITEM), or for a cell of its plan to load, lasts as it must. */
    @Override
    public boolean waiting() {
        BuilderState s = machine.state();
        return s == BuilderState.NEEDS_ITEM || (s == BuilderState.BUILDING_STEP && waitingForChunk);
    }

    /** MC EntityAIStructureBuilder.canGoIdle: true when its hut has no active work order. */
    @Override
    public boolean canGoIdle() {
        return claimedOrder().isEmpty();
    }

    /**
     * MC checkIfNeedsItem: an open or completed sync request sends the builder to wait for / fetch it, except before
     * the claimed order's structure is loaded, with no needs yet and no completed request of its own to fetch
     * (MC AbstractEntityAIStructureWithWorkOrder.checkIfNeedsItem): the load comes first, once in this AI's life as
     * MC's {@code recalculated} is never reset. HyColony computes every need when it loads the structure
     * ({@link StructureLoader}), so an unloaded site stands for MC's unloaded placer or order not yet requested. The
     * hut's needs, as MC's building-level ones, still count for an AI made anew while they are kept (a builder hired
     * again without restart).
     */
    private boolean needsItem() {
        BuilderState s = machine.state();
        if (s == BuilderState.INVENTORY_FULL || s == BuilderState.NEEDS_ITEM || s == BuilderState.COMPLETE_BUILD) {
            return false;
        }
        boolean beforeLoad = !recalculated
                && !site.loaded()
                && claimedOrder().isPresent()
                && ctx.resources().needs().remaining().isEmpty()
                && ctx.sync().mine().stream().noneMatch(r -> r.state() == RequestState.COMPLETED);
        return !beforeLoad && ctx.sync().pending();
    }

    /** MC checkIfCanceled: the order vanished (cancelled, removed, completed elsewhere) or left this builder. */
    private boolean orderLost() {
        WorkOrder order = site.order();
        if (order == null) {
            return false;
        }
        return !ctx.colony().work().holds(order) || !order.isClaimedBy(ctx.hut().position());
    }

    private BuilderState dropOrder() {
        // WorkManager.cancel already did; defence in depth
        ctx.colony().requests().cancelAllFrom(ctx.hut().requesterId());
        resetStructure();
        return BuilderState.IDLE;
    }

    private boolean inventoryNeedsDump() {
        return machine.state() != BuilderState.INVENTORY_FULL && dumpDue();
    }

    private boolean dumpDue() {
        return ctx.stock().dumpDue(ctx.job().actionsDone());
    }

    private @Nullable BuilderState idle() {
        if (!ctx.walkToHut()) {
            return null;
        }
        if (claimedOrder().isEmpty()) {
            if (ctx.resources().orderId() != 0) {
                resetStructure();
            }
            return null;
        }
        return BuilderState.START_WORKING;
    }

    private @Nullable BuilderState startWorking() {
        if (!ctx.walkToHut()) {
            return null;
        }
        WorkOrder o = claimedOrder().orElse(null);
        if (o == null) {
            resetStructure();
            return BuilderState.IDLE;
        }
        return site.loaded() && o.equals(site.order()) ? BuilderState.BUILDING_STEP : BuilderState.LOAD_STRUCTURE;
    }

    private BuilderState loadStructure() {
        WorkOrder o = claimedOrder().orElse(null);
        resetStructure();
        if (o == null || !loader.load(o)) {
            return BuilderState.IDLE;
        }
        recalculated = true;
        return BuilderState.BUILDING_STEP;
    }

    private Optional<WorkOrder> claimedOrder() {
        return ctx.colony().work().claimedBy(ctx.hut().position());
    }

    private @Nullable BuilderState dumpInventory() {
        if (!ctx.walkToWorkPos(ctx.hut().position())) {
            return null;
        }
        ctx.stock().dumpKeepingHutRules(true); // MC BuildingBuilder keepX and getRequiredItemsAndAmount
        ctx.job().clearActions();
        return site.loaded() ? BuilderState.BUILDING_STEP : BuilderState.START_WORKING;
    }

    /** One step: find the next position of the current stage that needs work, from the saved progress. */
    private @Nullable BuilderState structureStep() {
        waitingForChunk = false;
        if (!site.loaded()) {
            return BuilderState.START_WORKING;
        }
        if (dumpDue()) {
            return BuilderState.INVENTORY_FULL;
        }
        Stage stage = site.loadedOrder().stage();
        if (stage == Stage.DONE) {
            return BuilderState.COMPLETE_BUILD;
        }
        int size = site.positions(stage).size();
        int from = site.loadedOrder().progressIndex();
        int limit = (int) Math.min(size, (long) from + SCAN_LIMIT);
        int i = ctx.scan().firstNeedingWork(site, stage, from, limit);
        planned.foundAsPlanned(stage, from, i);
        if (i >= limit) {
            if (i < size) {
                site.progress(stage, i); // scan budget spent: go on next step
                return BuilderState.BUILDING_STEP;
            }
            return stageDone(stage);
        }
        if (i != from) {
            site.progress(stage, i);
        }
        if (!ctx.blocks().isLoaded(site.positions(stage).get(i))) {
            // Deviation from MC: checkIfCanceled only waits while the order's own position is unloaded, as Minecraft
            // loads any other chunk on access; Hytale does not, so every unloaded cell of the plan is waited for
            // instead of being skipped (the order would complete with holes). MC goes IDLE meanwhile; the builder
            // stays in BUILDING_STEP here, so it takes no break while it waits.
            waitingForChunk = true;
            return null;
        }
        return blockWork.work(stage, i);
    }

    private BuilderState stageDone(Stage stage) {
        Stage next = site.finalCheckDone() && stage == Stage.DECORATE ? Stage.DONE : StructureScan.nextStage(stage);
        if (next == Stage.DONE && stage == Stage.CLEAR_LEFTOVERS && !site.finalCheckDone()) {
            // Deviation from MC (its iterator only goes forward): once per loaded order, SOLID and DECORATE are walked
            // again, so a block broken behind the builder is placed again before completion. It runs after
            // CLEAR_LEFTOVERS, whose removals the new plan does not want, so it only refills the new plan's cells.
            site.startFinalCheck();
            next = Stage.SOLID;
        }
        site.progress(next, 0);
        return next == Stage.DONE ? BuilderState.COMPLETE_BUILD : BuilderState.BUILDING_STEP;
    }

    private BuilderState completeBuild() {
        WorkOrder o = site.order();
        if (o == null
                || !Objects.equals(ctx.colony().buildings().at(o.buildingPos()).orElse(null), site.target())) {
            resetStructure();
            return BuilderState.IDLE;
        }
        ctx.colony().work().finish(o, site.target());
        ctx.job().incrementActionsAndDecSaturation(); // MC AbstractEntityAIStructure.completeBuild
        ctx.award(XP_EACH_BUILDING);
        // All builder requests are sync: leftovers (e.g. a next bucket no longer needed) would block it forever.
        ctx.colony().requests().cancelAllFrom(ctx.hut().requesterId());
        resetStructure();
        return BuilderState.IDLE;
    }

    /** Forgets the structure and the module's order (completion, cancellation, failure). */
    private void resetStructure() {
        ctx.walker().forgetWorkPos();
        blockWork.reset();
        gathering.reset();
        site.clear();
    }
}
