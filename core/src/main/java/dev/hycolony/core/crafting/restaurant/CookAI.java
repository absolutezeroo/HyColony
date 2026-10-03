package dev.hycolony.core.crafting.restaurant;

import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.work.SyncRequests;
import dev.hycolony.core.job.work.WorkDelay;
import dev.hycolony.core.job.work.WorkerMachine;
import dev.hycolony.core.kernel.ai.AIBlockingEventType;
import dev.hycolony.core.kernel.ai.IStateSupplier;

/**
 * The waiter's AI (MC EntityAIWorkCook over AbstractEntityAIUsesFurnace and AbstractEntityAIBasic): MC's targets and
 * rates on a {@link WorkerMachine}, the campfires through {@link FurnaceWork}, the service through
 * {@link CookService}. It dumps its inventory after each action (MC getActionsDoneUntilDumping 1).
 */
final class CookAI implements JobAI {
    /** MC AbstractEntityAIBasic: the rate of the inventoryNeedsDump event. */
    private static final int DUMP_CHECK_RATE = 100;
    /** MC AbstractEntityAIBasic: the rate of the cleanAsync event. */
    private static final int CLEAN_ASYNC_RATE = 200;
    /** MC STANDARD_DELAY. */
    private static final int STANDARD_DELAY = 5;
    /** MC TICKS_SECOND. */
    private static final int TICKS_SECOND = 20;
    /** MC AbstractEntityAIUsesFurnace: START_WORKING's rate. */
    private static final int START_WORKING_RATE = 60;
    /** MC EntityAIWorkCook.SERVE_DELAY. */
    private static final int SERVE_DELAY = 30;

    private final CookWorkContext ctx;
    private final FurnaceWork furnace;
    private final CookService service;
    private final WorkerMachine<CookState> machine;
    private final WorkDelay delay = new WorkDelay();

    CookAI(CookWorkContext ctx) {
        this.ctx = ctx;
        this.service = new CookService(ctx);
        this.furnace = new FurnaceWork(ctx, service);
        SyncRequests requests = new SyncRequests(ctx.colony(), ctx.citizen(), ctx.hall(), ctx.stock());
        this.machine = new WorkerMachine<>(
                CookState.IDLE,
                () -> "waiter " + ctx.citizen().name(),
                () -> delay.waiting(WorkerMachine.MACHINE_RATE),
                delay::set);
        machine.event(
                AIBlockingEventType.STATE_BLOCKING, this::dumpDue, () -> CookState.INVENTORY_FULL, DUMP_CHECK_RATE);
        target(CookState.INVENTORY_FULL, this::dump, TICKS_SECOND);
        machine.event(AIBlockingEventType.AI_BLOCKING, requests::cleanAsync, machine::state, CLEAN_ASYNC_RATE);
        machine.event(AIBlockingEventType.AI_BLOCKING, furnace::accelerate, machine::state, TICKS_SECOND);
        target(CookState.IDLE, () -> CookState.START_WORKING, STANDARD_DELAY);
        target(CookState.START_WORKING, furnace::startWorking, START_WORKING_RATE);
        target(CookState.FILL_UP_FURNACES, furnace::fillUp, STANDARD_DELAY);
        target(CookState.RETRIEVING_END_PRODUCT_FROM_FURNACE, furnace::retrieveProduct, STANDARD_DELAY);
        target(CookState.RETRIEVING_USED_FUEL_FROM_FURNACE, furnace::retrieveFuel, STANDARD_DELAY);
        target(CookState.GATHERING_REQUIRED_MATERIALS, furnace::gather, TICKS_SECOND);
        target(CookState.COOK_SERVE_FOOD_TO_CITIZEN, service::serveCitizen, SERVE_DELAY);
        target(CookState.COOK_SERVE_FOOD_TO_PLAYER, service::servePlayer, SERVE_DELAY);
    }

    private void target(CookState s, IStateSupplier<CookState> action, int rate) {
        machine.target(s, action, rate);
    }

    /** MC inventoryNeedsDump: after {@link CookWorkContext#ACTIONS_UNTIL_DUMP} action, or a full inventory. */
    private boolean dumpDue() {
        return machine.state().isOkayToEat() && ctx.stock().dumpDue(ctx.job().actionsDone());
    }

    /** MC dumpInventory then afterDump: at the hall, stores what it carries but what the hall keeps on it. */
    private CookState dump() {
        if (!ctx.walkToWorkPos(ctx.hall().position())) {
            return CookState.INVENTORY_FULL;
        }
        ctx.stock().dumpKeepingHutRules(true);
        ctx.job().clearActions();
        return CookState.IDLE;
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

    /** MC isOkayToEat of the current state. */
    @Override
    public boolean canBeInterrupted() {
        return machine.state().isOkayToEat();
    }

    /** MC AbstractAISkeleton.resetAI: back to its first state, its walk forgotten. */
    @Override
    public void resetAI() {
        machine.reset();
        ctx.approach().forget();
    }
}
