package dev.hycolony.core.construction;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobXp;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.ai.AIBlockingEventType;
import dev.hycolony.core.kernel.ai.AIEventTarget;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestState;
import dev.hycolony.core.request.StackRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The builder's work AI. Port of MineColonies' AbstractEntityAIStructure, AbstractEntityAIStructureWithWorkOrder,
 * EntityAIStructureBuilder and the dump / NEEDS_ITEM parts of AbstractEntityAIBasic. The plan is built once per
 * order (on load); each step scans at most {@link #SCAN_LIMIT} positions from the order's saved progress.
 */
public final class BuilderAI implements JobAI {
    private static final System.Logger LOG = System.getLogger(BuilderAI.class.getName());

    /** MC ENTITY_AI_TICKRATE: the machine runs every 5 game ticks and counts 5 per run. */
    static final int MACHINE_RATE = 5;
    static final int ACTIONS_UNTIL_DUMP = 4096;
    static final int SCAN_LIMIT = 10_000;
    static final double XP_PER_BLOCK = 0.05;
    static final double XP_EACH_BUILDING = 8;
    /** MC getResourceBatchMultiplier: 1 until research exists. */
    static final int RESOURCE_BATCH_MULTIPLIER = 1;
    /**
     * After a dump the full hut refused, the next full-inventory dump waits this many actions (drops meanwhile go to
     * the hut or are lost).
     */
    // ponytail: fixed retry, no hut capacity query in ContainerAccess; poll the hut's space if players complain.
    static final int DUMP_RETRY_ACTIONS = 32;
    private static final int EXCEPTION_DELAY = 100;

    private final Colony colony;
    private final CitizenData citizen;
    private final BodyId body;
    private final Building hut;
    private final Job job;
    private final CitizenBodies bodies;
    private final WorldBlocks blocks;
    private final ItemCatalog catalog;
    private final BuildingResourcesModule resources;
    private final BuilderStock stock;
    private final BuilderWalker walker;
    private final Skill primary;
    private final Skill secondary;
    private final TickRateStateMachine<BuilderState> machine;

    private int calls;
    private int delay;
    private BodyAnimation animation;
    private int dumpRetryAt;

    // The structure being worked on; null when none is loaded.
    private WorkOrder order;
    private StructurePlan plan;
    private Building target;
    private BlockPos mineTarget;
    private boolean mineDelayed;
    private ItemKey neededItem;
    private int lastRecomputeIndex = -1;

    /** Last exception the machine caught (tests assert there is none). */
    RuntimeException lastError;

    public BuilderAI(Colony colony, CitizenData citizen, BodyId body) {
        this.colony = colony;
        this.citizen = citizen;
        this.body = body;
        this.hut = citizen.workBuilding() == null ? null : colony.buildings().at(citizen.workBuilding()).orElse(null);
        this.job = citizen.job().orElse(null);
        this.bodies = colony.context().bodies();
        this.blocks = colony.context().ports().blocks();
        this.catalog = colony.context().ports().catalog();
        this.resources = hut == null ? null : hut.module(BuildingResourcesModule.class).orElse(null);
        this.stock = hut == null ? null : new BuilderStock(colony, citizen, hut);
        this.walker = new BuilderWalker(bodies, body);
        WorkerModule worker = hut == null ? null : hut.module(WorkerModule.class).orElse(null);
        this.primary = worker == null ? Skill.Adaptability : worker.primary();
        this.secondary = worker == null ? Skill.Athletics : worker.secondary();
        this.machine = new TickRateStateMachine<>(BuilderState.IDLE, this::onException, MACHINE_RATE);

        event(AIBlockingEventType.AI_BLOCKING, this::waitingForSomething, machine::getState, MACHINE_RATE);
        event(AIBlockingEventType.AI_BLOCKING, this::needsItem, () -> BuilderState.NEEDS_ITEM, 20);
        event(AIBlockingEventType.STATE_BLOCKING, this::orderLost, this::dropOrder, 1);
        event(AIBlockingEventType.STATE_BLOCKING, this::inventoryNeedsDump, () -> BuilderState.INVENTORY_FULL, 100);
        state(BuilderState.IDLE, this::idle, 10);
        state(BuilderState.START_WORKING, this::startWorking, 20);
        state(BuilderState.LOAD_STRUCTURE, this::loadStructure, 5);
        state(BuilderState.GATHERING_REQUIRED_MATERIALS, this::gather, 20);
        state(BuilderState.NEEDS_ITEM, this::waitForRequests, 40);
        state(BuilderState.BUILDING_STEP, this::structureStep, 5);
        state(BuilderState.MINE_BLOCK, this::mine, 10);
        state(BuilderState.INVENTORY_FULL, this::dumpInventory, 20);
        state(BuilderState.COMPLETE_BUILD, this::completeBuild, 5);
    }

    private void event(AIBlockingEventType type, BooleanSupplier when,
            IStateSupplier<BuilderState> then, int rate) {
        machine.addTransition(new AIEventTarget<>(type, when, then, rate));
    }

    private void state(BuilderState s, IStateSupplier<BuilderState> action, int rate) {
        machine.addTransition(new AITarget<>(s, action, rate));
    }

    @Override
    public void tick() {
        if (resources == null || job == null || ++calls < MACHINE_RATE) {
            return;
        }
        calls = 0;
        machine.tick();
    }

    @Override
    public String stateName() {
        return machine.getState().name();
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
        LOG.log(System.Logger.Level.WARNING, "Builder AI failed for " + citizen.name(), e);
        lastError = e;
        machine.reset();
        delay = EXCEPTION_DELAY;
    }

    // ---- blocking events ----

    /** MC waitingForSomething: the builder swings while it waits out the delay. */
    private boolean waitingForSomething() {
        if (delay <= 0) {
            return false;
        }
        if (animation != null) {
            bodies.playAnimation(body, animation);
        }
        delay -= MACHINE_RATE;
        if (delay <= 0) {
            delay = 0;
            animation = null;
        }
        return true;
    }

    /** MC checkIfNeedsItem: an open or completed sync request sends the builder to wait for / fetch it. */
    private boolean needsItem() {
        BuilderState s = machine.getState();
        return s != BuilderState.INVENTORY_FULL && s != BuilderState.NEEDS_ITEM && s != BuilderState.COMPLETE_BUILD
                && stock.hasSyncRequests();
    }

    /** MC checkIfCanceled: the order vanished (cancelled, removed, completed elsewhere) or left this builder. */
    private boolean orderLost() {
        if (order == null) {
            return false;
        }
        return !colony.work().holds(order) || !order.isClaimedBy(hut.position());
    }

    private BuilderState dropOrder() {
        resetStructure();
        return BuilderState.IDLE;
    }

    private boolean inventoryNeedsDump() {
        return machine.getState() != BuilderState.INVENTORY_FULL && dumpDue();
    }

    private boolean dumpDue() {
        return job.actionsDone() >= ACTIONS_UNTIL_DUMP
                || (stock.inventory().isFull() && job.actionsDone() >= dumpRetryAt);
    }

    // ---- states ----

    private BuilderState idle() {
        if (!walker.walkTo(hut.position())) {
            return null;
        }
        if (colony.work().claimedBy(hut.position()).isEmpty()) {
            if (resources.orderId() != 0) {
                resetStructure();
            }
            return null;
        }
        return BuilderState.START_WORKING;
    }

    private BuilderState startWorking() {
        if (!walker.walkTo(hut.position())) {
            return null;
        }
        WorkOrder o = colony.work().claimedBy(hut.position()).orElse(null);
        if (o == null) {
            resetStructure();
            return BuilderState.IDLE;
        }
        return plan != null && o == order ? BuilderState.BUILDING_STEP : BuilderState.LOAD_STRUCTURE;
    }

    private BuilderState loadStructure() {
        WorkOrder o = colony.work().claimedBy(hut.position()).orElse(null);
        if (o == null) {
            resetStructure();
            return BuilderState.IDLE;
        }
        Building b = colony.buildings().at(o.buildingPos()).orElse(null);
        Blueprint bp = b == null ? null : colony.context().ports().blueprints()
                .load(o.style(), b.type().id(), o.blueprintLevel(), o.rotation()).orElse(null);
        if (bp == null) {
            // MC handleSpecificCancelActions: an order that cannot be loaded is dropped.
            LOG.log(System.Logger.Level.WARNING, "No blueprint for work order {0} at {1}; removing it", o.id(),
                    o.buildingPos());
            colony.work().cancel(o.id());
            resetStructure();
            return BuilderState.IDLE;
        }
        resetStructure();
        order = o;
        target = b;
        plan = StructurePlan.build(bp, o.buildingPos(), catalog);
        // Takes the order's saved stage and index: the order is the single owner of progress.
        resources.start(o, NeededResources.compute(plan, blocks, catalog));
        return BuilderState.BUILDING_STEP;
    }

    /** Fetches the current bucket (and the item needed now) from the hut, then asks for what is still missing. */
    private BuilderState gather() {
        if (plan == null) {
            return BuilderState.START_WORKING;
        }
        if (!walker.walkTo(hut.position())) {
            return null;
        }
        resources.currentBucket().ifPresent(stock::takeBucket);
        ItemKey needed = neededItem;
        neededItem = null;
        if (needed != null && stock.inventory().count(needed) == 0) {
            stock.take(needed, requestAmount(needed));
        }
        Stage stage = order.stage();
        if (stage != Stage.CLEAR && stage != Stage.REMOVE) { // never request while clearing or removing
            Set<ItemKey> requested = stock.requestedItems();
            resources.missingForCurrentAndNext(stock.inventory(), stock::hutCount).forEach((item, n) -> {
                if (requested.add(item)) {
                    int count = n * RESOURCE_BATCH_MULTIPLIER;
                    stock.request(new StackRequest(item, count, count, true));
                }
            });
            if (needed != null && stock.inventory().count(needed) == 0 && requested.add(needed)) {
                // Not covered by the buckets (hasListOfResInInvOrRequest): ask for this placement's item directly.
                stock.request(new StackRequest(needed, requestAmount(needed), 1, true));
            }
        }
        return stock.hasSyncRequests() ? BuilderState.NEEDS_ITEM : BuilderState.BUILDING_STEP;
    }

    /** MC getTotalAmount: what is still needed of the item, capped to a stack, at least 1. */
    private int requestAmount(ItemKey item) {
        int left = resources.needs().remaining().getOrDefault(item, 1);
        return Math.max(1, Math.min(left, catalog.maxStack(item)));
    }

    /** MC waitForRequests / lookForRequests: fetch every completed request at the hut, wait for the open ones. */
    private BuilderState waitForRequests() {
        List<Request> mine = stock.mine();
        if (mine.isEmpty()) {
            return BuilderState.START_WORKING;
        }
        if (!walker.walkTo(hut.position())) {
            return null;
        }
        for (Request r : mine) {
            if (r.state() == RequestState.COMPLETED) {
                stock.pickUp(r);
            }
        }
        return stock.hasSyncRequests() ? null : BuilderState.START_WORKING;
    }

    private BuilderState dumpInventory() {
        if (!walker.walkTo(hut.position())) {
            return null;
        }
        boolean stored = stock.dump(resources.currentBucket().orElse(Map.of()));
        job.clearActions();
        // Hut full, or nothing left to store (all kept): retry later instead of bouncing back at once.
        dumpRetryAt = stored && !stock.inventory().isFull() ? 0 : DUMP_RETRY_ACTIONS;
        return plan != null ? BuilderState.BUILDING_STEP : BuilderState.START_WORKING;
    }

    /** One step: find the next position of the current stage that needs work, from the saved progress. */
    private BuilderState structureStep() {
        if (plan == null) {
            return BuilderState.START_WORKING;
        }
        if (dumpDue()) {
            return BuilderState.INVENTORY_FULL;
        }
        Stage stage = order.stage();
        if (stage == Stage.DONE) {
            return BuilderState.COMPLETE_BUILD;
        }
        List<BlockPos> positions = positions(stage);
        int size = positions.size();
        int i = order.progressIndex();
        int limit = (int) Math.min(size, (long) i + SCAN_LIMIT);
        while (i < limit && !needsWork(stage, i, positions.get(i))) {
            i++;
        }
        if (i >= limit) {
            if (i < size) {
                progress(stage, i); // scan budget spent: go on next step
                return BuilderState.BUILDING_STEP;
            }
            Stage next = nextStage(stage);
            progress(next, 0);
            return next == Stage.DONE ? BuilderState.COMPLETE_BUILD : BuilderState.BUILDING_STEP;
        }
        if (i != order.progressIndex()) {
            progress(stage, i);
        }
        BlockPos pos = positions.get(i);
        if (stage == Stage.CLEAR || stage == Stage.REMOVE || mustMineFirst(pos)) {
            return startMining(pos);
        }
        BlueprintEntry e = (stage == Stage.SOLID ? plan.solidList() : plan.decoList()).get(i);
        ItemKey item = catalog.itemForBlock(e.state().key()).orElse(null); // none: free to place
        if (item != null && stock.inventory().count(item) == 0) {
            return missing(item, i);
        }
        if (!walker.walkToWorkPos(pos, order.buildingPos())) {
            return null;
        }
        place(stage, i, pos, e, item);
        return null;
    }

    private void place(Stage stage, int i, BlockPos pos, BlueprintEntry e, ItemKey item) {
        if (!blocks.place(pos, e.state(), e.hasContainer())) {
            LOG.log(System.Logger.Level.WARNING, "Builder {0}: failed to place {1} at {2}; skipped", citizen.name(),
                    e.state().key().id(), pos);
            progress(stage, i + 1);
            return;
        }
        if (item != null) {
            stock.inventory().extract(item, 1);
            resources.onPlaced(item);
        }
        if (e.hasContainer()) {
            target.addContainer(pos); // MC: racks the builder places become the building's containers
        }
        award(XP_PER_BLOCK);
        job.incrementActions();
        progress(stage, i + 1);
        bodies.setHeldItem(body, Optional.ofNullable(item));
        startDelay(BuilderTimings.placeDelay(citizen.skills().level(primary)), BodyAnimation.BUILD);
    }

    /**
     * The item for this placement is not in the inventory. If it is in neither the current nor the next bucket (the
     * world changed since the needs were computed), the needs are recomputed first, at most once per position.
     */
    private BuilderState missing(ItemKey item, int index) {
        boolean inBuckets = resources.currentBucket().map(b -> b.containsKey(item)).orElse(false)
                || resources.nextBucket().map(b -> b.containsKey(item)).orElse(false);
        if (!inBuckets && resources.needs().remaining().containsKey(item) && lastRecomputeIndex != index) {
            lastRecomputeIndex = index;
            resources.start(order, NeededResources.compute(plan, blocks, catalog));
        }
        neededItem = item;
        return BuilderState.GATHERING_REQUIRED_MATERIALS;
    }

    private BuilderState startMining(BlockPos pos) {
        mineTarget = pos;
        mineDelayed = false;
        return BuilderState.MINE_BLOCK;
    }

    /** MC doMining + mineBlock: a tool if one is needed, a first pass that waits, a second that breaks. */
    private BuilderState mine() {
        BlockPos pos = mineTarget;
        BlockState state = pos == null ? null : blocks.get(pos).orElse(null);
        if (state == null || !mineable(state)) {
            mineTarget = null;
            return BuilderState.BUILDING_STEP;
        }
        ToolType type = catalog.toolFor(state.key()).orElse(null);
        ItemKey tool = null;
        if (type != null) {
            tool = stock.toolInInventory(type);
            if (tool == null) {
                return fetchTool(type);
            }
        }
        if (!walker.walkToWorkPos(pos, order.buildingPos())) {
            return null;
        }
        if (!mineDelayed) {
            mineDelayed = true;
            bodies.setHeldItem(body, Optional.ofNullable(tool));
            startDelay(BuilderTimings.breakDelay(citizen.skills().level(secondary), catalog.hardness(state.key()),
                    stock.toolSpeed(tool)), BodyAnimation.MINE);
            return null;
        }
        mineDelayed = false;
        mineTarget = null;
        var drops = blocks.breakBlock(pos);
        if (!(order.stage() == Stage.CLEAR && catalog.isOre(state.key()))) { // CLEAR voids ores
            stock.storeDrops(drops);
        }
        if (tool != null) {
            blocks.damageTool(body, tool);
        }
        award(XP_PER_BLOCK);
        job.incrementActions();
        return BuilderState.BUILDING_STEP;
    }

    /** MC checkForNeededTool: the hut's, else a tool request. */
    private BuilderState fetchTool(ToolType type) {
        ItemKey inHut = stock.toolInHut(type);
        if (inHut == null) {
            stock.requestTool(type);
            return BuilderState.NEEDS_ITEM;
        }
        if (!walker.walkTo(hut.position())) {
            return null;
        }
        stock.take(inHut, 1);
        return null;
    }

    private BuilderState completeBuild() {
        WorkOrder o = order;
        Building b = target;
        if (o == null || colony.buildings().at(o.buildingPos()).orElse(null) != b) {
            resetStructure();
            return BuilderState.IDLE;
        }
        BuildCompletion.apply(colony, o, b);
        job.incrementActions();
        award(XP_EACH_BUILDING);
        // All builder requests are sync: leftovers (e.g. a next bucket no longer needed) would block it forever.
        colony.requests().cancelAllFrom(hut.requesterId());
        resetStructure();
        return BuilderState.IDLE;
    }

    // ---- helpers ----

    private List<BlockPos> positions(Stage stage) {
        return switch (stage) {
            case CLEAR -> plan.clearList();
            case SOLID -> plan.solidPositions();
            case DECORATE -> plan.decoPositions();
            case REMOVE -> plan.removeList();
            case DONE -> List.of();
        };
    }

    private static Stage nextStage(Stage stage) {
        return switch (stage) {
            case CLEAR -> Stage.SOLID;
            case SOLID -> Stage.DECORATE;
            default -> Stage.DONE;
        };
    }

    /** CLEAR: a block the plan does not want there; REMOVE: any block; SOLID/DECORATE: not yet as planned. */
    private boolean needsWork(Stage stage, int i, BlockPos pos) {
        BlockState world = blocks.get(pos).orElse(null);
        return switch (stage) {
            case CLEAR -> world != null && mineable(world) && !world.equals(plan.stateAt(pos)) && notAHut(pos);
            case REMOVE -> world != null && mineable(world) && notAHut(pos);
            default -> {
                BlueprintEntry e = (stage == Stage.SOLID ? plan.solidList() : plan.decoList()).get(i);
                yield !e.state().equals(world) && (world == null || catalog.kind(world.key()) != BlockKind.UNBREAKABLE);
            }
        };
    }

    /** Air, fluids and unbreakable blocks are never mined. */
    private boolean mineable(BlockState state) {
        BlockKind kind = catalog.kind(state.key());
        return kind != BlockKind.AIR && kind != BlockKind.FLUID && kind != BlockKind.UNBREAKABLE;
    }

    private boolean mustMineFirst(BlockPos pos) {
        BlockState world = blocks.get(pos).orElse(null);
        return world != null && mineable(world);
    }

    private boolean notAHut(BlockPos pos) {
        return colony.buildings().at(pos).isEmpty();
    }

    private void progress(Stage stage, int index) {
        resources.progress(stage, index);
        colony.markDirty();
    }

    private void startDelay(int ticks, BodyAnimation anim) {
        delay = ticks;
        animation = anim;
        bodies.playAnimation(body, anim);
    }

    private void award(double xp) {
        int homeLevel = citizen.homeBuilding() == null ? 0
                : colony.buildings().at(citizen.homeBuilding()).map(Building::level).orElse(0);
        JobXp.award(citizen, primary, secondary, xp, hut.level(), homeLevel);
    }

    /** Forgets the structure and the module's order (completion, cancellation, failure). */
    private void resetStructure() {
        order = null;
        plan = null;
        target = null;
        walker.forgetWorkPos();
        mineTarget = null;
        mineDelayed = false;
        neededItem = null;
        lastRecomputeIndex = -1;
        resources.reset();
    }
}
