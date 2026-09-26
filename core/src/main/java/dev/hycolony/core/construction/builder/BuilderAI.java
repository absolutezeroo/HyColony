package dev.hycolony.core.construction.builder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.resources.NeededResources;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.ai.AIBlockingEventType;
import dev.hycolony.core.kernel.ai.AIEventTarget;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * The builder's work AI. Port of MineColonies' AbstractEntityAIStructure, AbstractEntityAIStructureWithWorkOrder,
 * EntityAIStructureBuilder and the dump / NEEDS_ITEM parts of AbstractEntityAIBasic. The plan is built once per
 * order (on load); each step scans at most {@link #SCAN_LIMIT} positions from the order's saved progress. The steps
 * delegate the block work to {@link BuilderBlockWork} and the materials to {@link BuilderGathering}.
 */
public final class BuilderAI implements JobAI {
    private static final System.Logger LOG = System.getLogger(BuilderAI.class.getName());

    /** MC ENTITY_AI_TICKRATE: the machine runs every 5 game ticks and counts 5 per run. */
    static final int MACHINE_RATE = 5;

    static final int SCAN_LIMIT = 10_000;
    static final double XP_EACH_BUILDING = 8;

    private static final int EXCEPTION_DELAY = 100;

    private final BuilderContext ctx;
    private final BuildSite site;
    private final BuilderBlockWork blockWork;
    private final BuilderGathering gathering;
    private final TickRateStateMachine<BuilderState> machine;

    private int calls;

    /** Last exception the machine caught (tests assert there is none). */
    RuntimeException lastError;

    public BuilderAI(Colony colony, CitizenData citizen, BodyId body) {
        this.ctx = BuilderContext.of(colony, citizen, body);
        this.site = ctx.site();
        this.gathering = new BuilderGathering(ctx);
        this.blockWork = new BuilderBlockWork(ctx, gathering);
        this.machine = new TickRateStateMachine<>(BuilderState.IDLE, this::onException, MACHINE_RATE);

        event(AIBlockingEventType.AI_BLOCKING, ctx.gestures()::waiting, machine::getState, MACHINE_RATE);
        event(AIBlockingEventType.AI_BLOCKING, this::needsItem, () -> BuilderState.NEEDS_ITEM, 20);
        event(AIBlockingEventType.STATE_BLOCKING, this::orderLost, this::dropOrder, 1);
        event(AIBlockingEventType.STATE_BLOCKING, this::inventoryNeedsDump, () -> BuilderState.INVENTORY_FULL, 100);
        state(BuilderState.IDLE, this::idle, 10);
        state(BuilderState.START_WORKING, this::startWorking, 20);
        state(BuilderState.LOAD_STRUCTURE, this::loadStructure, 5);
        state(BuilderState.GATHERING_REQUIRED_MATERIALS, gathering::gather, 20);
        state(BuilderState.NEEDS_ITEM, gathering::waitForRequests, 40);
        state(BuilderState.BUILDING_STEP, this::structureStep, 5);
        state(BuilderState.MINE_BLOCK, blockWork::mine, 10);
        state(BuilderState.INVENTORY_FULL, this::dumpInventory, 20);
        state(BuilderState.COMPLETE_BUILD, this::completeBuild, 5);
    }

    private void event(AIBlockingEventType type, BooleanSupplier when, IStateSupplier<BuilderState> then, int rate) {
        machine.addTransition(new AIEventTarget<>(type, when, then, rate));
    }

    private void state(BuilderState s, IStateSupplier<BuilderState> action, int rate) {
        machine.addTransition(new AITarget<>(s, action, rate));
    }

    @Override
    public void tick() {
        if (ctx.resources() == null || ctx.job() == null || ++calls < MACHINE_RATE) {
            return;
        }
        calls = 0;
        machine.tick();
    }

    @Override
    public String stateName() {
        return machine.getState().name();
    }

    @Override
    public Optional<Msg> describe() {
        return Optional.of(BuilderActivity.describe(
                machine.getState(),
                site.order(),
                ctx.walker().walking(),
                ctx.gestures().inHand()));
    }

    /** MC isOkayToEat. */
    @Override
    public boolean canBeInterrupted() {
        return switch (machine.getState()) {
            case IDLE, START_WORKING, NEEDS_ITEM, GATHERING_REQUIRED_MATERIALS -> true;
            default -> false;
        };
    }

    private void onException(RuntimeException e) {
        LOG.log(
                System.Logger.Level.WARNING,
                "Builder AI failed for " + ctx.citizen().name(),
                e);
        lastError = e;
        machine.reset();
        ctx.gestures().pause(EXCEPTION_DELAY);
    }

    /** MC checkIfNeedsItem: an open or completed sync request sends the builder to wait for / fetch it. */
    private boolean needsItem() {
        BuilderState s = machine.getState();
        return s != BuilderState.INVENTORY_FULL
                && s != BuilderState.NEEDS_ITEM
                && s != BuilderState.COMPLETE_BUILD
                && ctx.requests().hasSyncRequests();
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
        return machine.getState() != BuilderState.INVENTORY_FULL && dumpDue();
    }

    private boolean dumpDue() {
        return ctx.stock().dumpDue(ctx.job().actionsDone());
    }

    private BuilderState idle() {
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

    private BuilderState startWorking() {
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
        if (o == null) {
            resetStructure();
            return BuilderState.IDLE;
        }
        Building b = ctx.colony().buildings().at(o.buildingPos()).orElse(null);
        Blueprint bp = b == null ? null : blueprint(o, b, o.blueprintLevel()).orElse(null);
        if (bp == null) {
            // MC handleSpecificCancelActions: an order that cannot be loaded is dropped.
            LOG.log(
                    System.Logger.Level.WARNING,
                    "No blueprint for work order {0} at {1}; removing it",
                    o.id(),
                    o.buildingPos());
            ctx.colony().work().cancel(o.id());
            resetStructure();
            return BuilderState.IDLE;
        }
        resetStructure();
        site.load(o, b, StructurePlan.build(bp, o.buildingPos(), ctx.catalog()), previousPlan(o, b));
        // Takes the order's saved stage and index: the order is the single owner of progress.
        ctx.resources().start(o, NeededResources.compute(site.plan(), ctx.blocks(), ctx.catalog()));
        return BuilderState.BUILDING_STEP;
    }

    private Optional<Blueprint> blueprint(WorkOrder o, Building b, int level) {
        return ctx.colony()
                .context()
                .ports()
                .blueprints()
                .load(o.style(), b.type().id(), level, o.rotation());
    }

    /**
     * The plan of the level an UPGRADE replaces (same style and rotation), whose leftovers CLEAR_LEFTOVERS mines;
     * null for other orders or when that blueprint is missing (nothing is then removed).
     */
    private StructurePlan previousPlan(WorkOrder o, Building b) {
        if (o.type() != WorkOrderType.UPGRADE) {
            return null;
        }
        return blueprint(o, b, o.blueprintLevel() - 1)
                .map(old -> StructurePlan.build(old, o.buildingPos(), ctx.catalog()))
                .orElse(null);
    }

    private Optional<WorkOrder> claimedOrder() {
        return ctx.colony().work().claimedBy(ctx.hut().position());
    }

    private BuilderState dumpInventory() {
        if (!ctx.walkToHut()) {
            return null;
        }
        ctx.stock().dump(ctx.resources().currentBucket().orElse(Map.of()));
        ctx.job().clearActions();
        return site.loaded() ? BuilderState.BUILDING_STEP : BuilderState.START_WORKING;
    }

    /** One step: find the next position of the current stage that needs work, from the saved progress. */
    private BuilderState structureStep() {
        if (!site.loaded()) {
            return BuilderState.START_WORKING;
        }
        if (dumpDue()) {
            return BuilderState.INVENTORY_FULL;
        }
        Stage stage = site.order().stage();
        if (stage == Stage.DONE) {
            return BuilderState.COMPLETE_BUILD;
        }
        int size = site.positions(stage).size();
        int from = site.order().progressIndex();
        int limit = (int) Math.min(size, (long) from + SCAN_LIMIT);
        int i = ctx.scan().firstNeedingWork(site, stage, from, limit);
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
        Building b = site.target();
        if (o == null
                || !Objects.equals(ctx.colony().buildings().at(o.buildingPos()).orElse(null), b)) {
            resetStructure();
            return BuilderState.IDLE;
        }
        ctx.colony().work().finish(o, b);
        ctx.job().incrementActions();
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
