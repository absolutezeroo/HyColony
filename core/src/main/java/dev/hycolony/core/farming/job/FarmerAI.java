package dev.hycolony.core.farming.job;

import dev.hycolony.core.crafting.job.CraftingStep;
import dev.hycolony.core.crafting.job.CraftingWork;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.ai.AIBlockingEventType;
import dev.hycolony.core.kernel.ai.AIEventTarget;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The farmer's AI (MC EntityAIWorkFarmer over AbstractEntityAICrafting): its crafting tasks first, with
 * {@link CraftingWork}; without one, it prepares and works its fields with {@link FarmWork}. MC's targets and rates,
 * on a machine run every {@link #MACHINE_RATE} game ticks.
 */
final class FarmerAI implements JobAI {
    private static final System.Logger LOG = System.getLogger(FarmerAI.class.getName());

    /** MC ENTITY_AI_TICKRATE: the machine runs every 5 game ticks and counts 5 per run. */
    static final int MACHINE_RATE = 5;

    /** MC AbstractEntityAIBasic: the rate of the inventoryNeedsDump event. */
    private static final int DUMP_CHECK_RATE = 100;

    /** MC AbstractEntityAIBasic: the rates of the checkIfNeedsItem event and of the NEEDS_ITEM target. */
    private static final int NEEDS_ITEM_CHECK_RATE = 20;

    private static final int NEEDS_ITEM_RATE = 40;

    /** MC PREPARING's rate; the field states run every STANDARD_DELAY. */
    private static final int PREPARING_RATE = 20;

    private static final int EXCEPTION_DELAY = 100;

    private final CraftingWork crafting;
    private final FarmWork farm;
    private final FarmWorkContext ctx;
    private final TickRateStateMachine<FarmerState> machine;
    private int calls;

    FarmerAI(CraftingWork crafting, FarmWorkContext ctx) {
        this.crafting = crafting;
        this.ctx = ctx;
        this.farm = new FarmWork(ctx);
        this.machine = new TickRateStateMachine<>(FarmerState.IDLE, this::onException, MACHINE_RATE);
        machine.addTransition(new AIEventTarget<>(
                AIBlockingEventType.AI_BLOCKING, () -> farm.waiting(MACHINE_RATE), machine::getState, MACHINE_RATE));
        event(AIBlockingEventType.STATE_BLOCKING, this::dumpDue, FarmerState.INVENTORY_FULL, DUMP_CHECK_RATE);
        craft(FarmerState.INVENTORY_FULL, crafting::dump, CraftingWork.TICKS_SECOND);
        event(AIBlockingEventType.AI_BLOCKING, this::needsItem, FarmerState.NEEDS_ITEM, NEEDS_ITEM_CHECK_RATE);
        craft(FarmerState.NEEDS_ITEM, crafting::waitForRequests, NEEDS_ITEM_RATE);
        craft(FarmerState.GATHERING_REQUIRED_MATERIALS, crafting::gather, CraftingWork.TICKS_SECOND);
        // MC: the farmer's hasWorkToDo is always true, so IDLE always moves on.
        state(FarmerState.IDLE, () -> FarmerState.START_WORKING, CraftingWork.TICKS_SECOND);
        state(FarmerState.START_WORKING, this::decide, CraftingWork.STANDARD_DELAY);
        craft(FarmerState.QUERY_ITEMS, crafting::queryItems, CraftingWork.STANDARD_DELAY);
        craft(FarmerState.GET_RECIPE, crafting::getRecipe, CraftingWork.STANDARD_DELAY);
        craft(FarmerState.CRAFT, crafting::craft, CraftingWork.HIT_DELAY);
        state(FarmerState.PREPARING, farm::prepare, PREPARING_RATE);
        for (FarmerState field :
                new FarmerState[] {FarmerState.FARMER_HOE, FarmerState.FARMER_PLANT, FarmerState.FARMER_HARVEST}) {
            state(field, () -> farm.workAtField(field), CraftingWork.STANDARD_DELAY);
        }
    }

    private void event(AIBlockingEventType type, BooleanSupplier when, FarmerState then, int rate) {
        machine.addTransition(new AIEventTarget<>(type, when, () -> then, rate));
    }

    private void state(FarmerState s, IStateSupplier<FarmerState> action, int rate) {
        machine.addTransition(new AITarget<>(s, action, rate));
    }

    private void craft(FarmerState s, Supplier<CraftingStep> step, int rate) {
        state(s, () -> FarmerState.of(step.get()), rate);
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
        return machine.getState().isOkayToEat() && (farm.consumeDumpRequest() || crafting.inventoryNeedsDump());
    }

    /** MC checkIfNeedsItem: not while dumping (nor while already waiting). */
    private boolean needsItem() {
        FarmerState s = machine.getState();
        return s != FarmerState.INVENTORY_FULL && s != FarmerState.NEEDS_ITEM && crafting.needsItem();
    }

    private void onException(RuntimeException e) {
        LOG.log(
                System.Logger.Level.WARNING,
                "Farmer AI failed for " + ctx.citizen().name(),
                e);
        machine.reset();
        farm.setDelay(EXCEPTION_DELAY);
    }

    @Override
    public void tick() {
        if (++calls < MACHINE_RATE) {
            return;
        }
        calls = 0;
        machine.tick();
    }

    @Override
    public String stateName() {
        return machine.getState().name();
    }

    /** MC isOkayToEat of the current state. */
    @Override
    public boolean canBeInterrupted() {
        return machine.getState().isOkayToEat();
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
