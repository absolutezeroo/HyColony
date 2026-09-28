package dev.hycolony.core.testing.crafting;

import dev.hycolony.core.crafting.job.CraftingStep;
import dev.hycolony.core.crafting.job.CraftingWork;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.ai.AIBlockingEventType;
import dev.hycolony.core.kernel.ai.AIEventTarget;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import java.util.function.BooleanSupplier;

/**
 * The test crafter's AI, built on {@link CraftingWork} the way a concrete crafter AI (the farmer) is: MC
 * AbstractEntityAICrafting's targets, and the dump and wait-for-requests ones of AbstractEntityAIBasic, on a machine run
 * every {@link #MACHINE_RATE} game ticks. It goes idle (the citizen wanders) once it has no task and nothing to dump.
 */
public final class TestCrafterAI implements JobAI {
    /** MC ENTITY_AI_TICKRATE: the machine runs every 5 game ticks and counts 5 per run. */
    static final int MACHINE_RATE = 5;
    /** MC AbstractEntityAIBasic: the rate of the inventoryNeedsDump event. */
    private static final int DUMP_CHECK_RATE = 100;
    /** MC AbstractEntityAIBasic: the rates of the checkIfNeedsItem event and of the NEEDS_ITEM target. */
    private static final int NEEDS_ITEM_CHECK_RATE = 20;

    private static final int NEEDS_ITEM_RATE = 40;

    private final CraftingWork work;
    private final TickRateStateMachine<CraftingStep> machine;
    private int calls;

    TestCrafterAI(CraftingWork work) {
        this.work = work;
        this.machine = new TickRateStateMachine<>(CraftingStep.IDLE, TestCrafterAI::onException, MACHINE_RATE);
        event(AIBlockingEventType.STATE_BLOCKING, this::dumpDue, CraftingStep.INVENTORY_FULL, DUMP_CHECK_RATE);
        state(CraftingStep.INVENTORY_FULL, work::dump, CraftingWork.TICKS_SECOND);
        event(AIBlockingEventType.AI_BLOCKING, this::needsItem, CraftingStep.NEEDS_ITEM, NEEDS_ITEM_CHECK_RATE);
        state(CraftingStep.NEEDS_ITEM, work::waitForRequests, NEEDS_ITEM_RATE);
        state(CraftingStep.GATHERING_REQUIRED_MATERIALS, work::gather, CraftingWork.TICKS_SECOND);
        machine.addTransition(new AITarget<>(
                CraftingStep.IDLE, work::hasWorkToDo, () -> CraftingStep.START_WORKING, CraftingWork.TICKS_SECOND));
        state(CraftingStep.START_WORKING, work::decide, CraftingWork.STANDARD_DELAY);
        state(CraftingStep.QUERY_ITEMS, work::queryItems, CraftingWork.STANDARD_DELAY);
        state(CraftingStep.GET_RECIPE, work::getRecipe, CraftingWork.STANDARD_DELAY);
        state(CraftingStep.CRAFT, work::craft, CraftingWork.HIT_DELAY);
    }

    private void event(AIBlockingEventType type, BooleanSupplier when, CraftingStep then, int rate) {
        machine.addTransition(new AIEventTarget<>(type, when, () -> then, rate));
    }

    private void state(CraftingStep step, IStateSupplier<CraftingStep> action, int rate) {
        machine.addTransition(new AITarget<>(step, action, rate));
    }

    /** A test crafter fails the test at once, where MC's onException pauses the worker. */
    private static void onException(RuntimeException e) {
        throw e;
    }

    /** MC inventoryNeedsDump: only in a state okay to be interrupted, so never while dumping. */
    private boolean dumpDue() {
        return machine.getState().isOkayToEat() && work.inventoryNeedsDump();
    }

    /** MC checkIfNeedsItem: not while dumping (nor while already waiting). */
    private boolean needsItem() {
        CraftingStep step = machine.getState();
        return step != CraftingStep.INVENTORY_FULL && step != CraftingStep.NEEDS_ITEM && work.needsItem();
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

    /** Idle with no task and nothing left to dump: the citizen wanders until a task comes. */
    @Override
    public boolean canGoIdle() {
        return machine.getState() == CraftingStep.IDLE && !work.hasWorkToDo() && !work.inventoryNeedsDump();
    }
}
