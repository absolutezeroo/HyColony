package dev.hycolony.core.farming.job;

import dev.hycolony.core.crafting.job.CraftingStep;
import dev.hycolony.core.crafting.job.CraftingWork;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.work.WorkerMachine;
import dev.hycolony.core.kernel.ai.AIBlockingEventType;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The farmer's AI (MC EntityAIWorkFarmer over AbstractEntityAICrafting): its crafting tasks first, with
 * {@link CraftingWork}; without one, it prepares and works its fields with {@link FarmWork}. MC's targets and rates,
 * on a {@link WorkerMachine}.
 */
final class FarmerAI implements JobAI {
    /** MC AbstractEntityAIBasic: the rate of the inventoryNeedsDump event. */
    private static final int DUMP_CHECK_RATE = 100;

    /** MC AbstractEntityAIBasic: the rates of the checkIfNeedsItem event and of the NEEDS_ITEM target. */
    private static final int NEEDS_ITEM_CHECK_RATE = 20;

    private static final int NEEDS_ITEM_RATE = 40;

    /** MC AbstractEntityAIBasic: the rate of the cleanAsync event. */
    private static final int CLEAN_ASYNC_RATE = 200;

    /** MC PREPARING's rate; the field states run every STANDARD_DELAY. */
    private static final int PREPARING_RATE = 20;

    private final CraftingWork crafting;
    private final FarmWork farm;
    private final FarmWorkContext ctx;
    private final WorkerMachine<FarmerState> machine;

    FarmerAI(CraftingWork crafting, FarmWorkContext ctx) {
        this.crafting = crafting;
        this.ctx = ctx;
        this.farm = new FarmWork(ctx);
        this.machine = new WorkerMachine<>(
                FarmerState.IDLE,
                () -> "farmer " + ctx.citizen().name(),
                () -> farm.delay().waiting(WorkerMachine.MACHINE_RATE),
                farm.delay()::set);
        event(AIBlockingEventType.STATE_BLOCKING, this::dumpDue, FarmerState.INVENTORY_FULL, DUMP_CHECK_RATE);
        craft(FarmerState.INVENTORY_FULL, crafting::dump, CraftingWork.TICKS_SECOND);
        event(AIBlockingEventType.AI_BLOCKING, this::needsItem, FarmerState.NEEDS_ITEM, NEEDS_ITEM_CHECK_RATE);
        event(AIBlockingEventType.AI_BLOCKING, ctx.requests()::cleanAsync, FarmerState.NEEDS_ITEM, CLEAN_ASYNC_RATE);
        craft(FarmerState.NEEDS_ITEM, crafting::waitForRequests, NEEDS_ITEM_RATE);
        craft(FarmerState.GATHERING_REQUIRED_MATERIALS, crafting::gather, CraftingWork.TICKS_SECOND);
        // MC: the farmer's hasWorkToDo is always true, so IDLE always moves on.
        target(FarmerState.IDLE, () -> FarmerState.START_WORKING, CraftingWork.TICKS_SECOND);
        target(FarmerState.START_WORKING, this::decide, CraftingWork.STANDARD_DELAY);
        craft(FarmerState.QUERY_ITEMS, crafting::queryItems, CraftingWork.STANDARD_DELAY);
        craft(FarmerState.GET_RECIPE, crafting::getRecipe, CraftingWork.STANDARD_DELAY);
        craft(FarmerState.CRAFT, crafting::craft, CraftingWork.HIT_DELAY);
        target(FarmerState.PREPARING, farm::prepare, PREPARING_RATE);
        for (FarmerState field :
                new FarmerState[] {FarmerState.FARMER_HOE, FarmerState.FARMER_PLANT, FarmerState.FARMER_HARVEST}) {
            target(field, () -> farm.workAtField(field), CraftingWork.STANDARD_DELAY);
        }
    }

    private void event(AIBlockingEventType type, BooleanSupplier when, FarmerState then, int rate) {
        machine.event(type, when, () -> then, rate);
    }

    private void target(FarmerState s, IStateSupplier<FarmerState> action, int rate) {
        machine.target(s, action, rate);
    }

    private void craft(FarmerState s, Supplier<CraftingStep> step, int rate) {
        target(s, () -> FarmerState.of(step.get()), rate);
    }

    /**
     * MC decide, the farmer's version: a crafting task goes the crafter's way; without one, at the hut, the farmer
     * prepares its fields (MC maps the crafter's IDLE to PREPARING), unless a dump is due.
     */
    private FarmerState decide() {
        if (crafting.hasWorkToDo()) {
            return FarmerState.of(crafting.decide());
        }
        if (!ctx.walkToHut() || ctx.job().actionsDone() >= FarmWorkContext.ACTIONS_UNTIL_DUMP) {
            return FarmerState.START_WORKING;
        }
        return FarmerState.PREPARING;
    }

    /** MC inventoryNeedsDump (wantInventoryDumped): after each pass, at 64 actions or a full inventory. */
    private boolean dumpDue() {
        return machine.state().isOkayToEat() && (farm.consumeDumpRequest() || crafting.inventoryNeedsDump());
    }

    /** MC checkIfNeedsItem: not while dumping (nor while already waiting). */
    private boolean needsItem() {
        FarmerState s = machine.state();
        return s != FarmerState.INVENTORY_FULL && s != FarmerState.NEEDS_ITEM && crafting.needsItem();
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

    /** Waiting for the items it asked for (NEEDS_ITEM) lasts as long as their delivery. */
    @Override
    public boolean waiting() {
        return machine.state() == FarmerState.NEEDS_ITEM;
    }

    /** MC canGoIdle: with no field to work today, idle when no crafting task either. */
    @Override
    public boolean canGoIdle() {
        return ctx.fields().fieldToWorkOn(ctx.colony(), ctx.hut()).isEmpty() && !crafting.hasWorkToDo();
    }

    /** Why the farmer does not work (no field), for the citizen window. */
    @Override
    public Optional<Msg> describe() {
        return farm.status();
    }
}
