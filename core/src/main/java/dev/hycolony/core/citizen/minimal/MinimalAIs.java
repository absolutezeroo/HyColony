package dev.hycolony.core.citizen.minimal;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.hurt.FleeAI;
import dev.hycolony.core.citizen.mourn.MournAI;
import dev.hycolony.core.citizen.wander.CitizenWander;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.ai.AIOneTimeEventTarget;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A citizen's minimal AIs beside its sleep, meals, wander and work (MC CitizenAI.minimalAI): mourning
 * ({@link MournAI}) and fleeing ({@link FleeAI}), whose states and decisions it brings to the citizen's AI.
 */
public final class MinimalAIs {
    private final CitizenData data;
    private final MournAI mourn;
    private final FleeAI flee;
    private final TickRateStateMachine<CitizenState> machine;
    /** The state it fled from (MC lastState during FLEE). */
    private CitizenState fleeFrom = CitizenState.IDLE;

    public MinimalAIs(
            Colony colony,
            CitizenData data,
            BodyId body,
            CitizenWander wander,
            TickRateStateMachine<CitizenState> machine) {
        this.data = data;
        this.mourn = new MournAI(colony, data, body, wander);
        this.flee = new FleeAI(colony, data, body);
        this.machine = machine;
        machine.addTransition(
                new AITarget<>(CitizenState.MOURN, (IStateSupplier<CitizenState>) mourn::tick, MournAI.RATE_TICKS));
        machine.addTransition(
                new AITarget<>(CitizenState.FLEE, (IStateSupplier<CitizenState>) flee::tick, FleeAI.RATE_TICKS));
    }

    /**
     * MC calculateNextState's mourning part, after the sleep and hunger parts (which chose {@code next}, kept for SLEEP
     * and EATING) and before the rain and work: MOURN while it mourns (MC asks no canBeInterrupted), {@code leaving}
     * told the state it leaves; once it mourns no more, back to work when {@code shouldWork}.
     */
    public CitizenState decideMourning(CitizenState next, Consumer<CitizenState> leaving, BooleanSupplier shouldWork) {
        if (next == CitizenState.SLEEP || next == CitizenState.EATING) {
            return next;
        }
        if (data.mourning().isMourning()) {
            if (next != CitizenState.MOURN) {
                leaving.accept(next);
                mourn.reset();
            }
            return CitizenState.MOURN;
        }
        if (next == CitizenState.MOURN) {
            return shouldWork.getAsBoolean() ? CitizenState.WORKING : CitizenState.IDLE;
        }
        return next;
    }

    /**
     * {@link FleeAI#hit}: hit by {@code attacker}, it runs; then (MC AIOneTimeEventTarget(FLEE)) it flees from the next
     * tick, {@code leaving} told the state it leaves.
     */
    public void hit(Optional<Vec3> attacker, Consumer<CitizenState> leaving) {
        if (flee.hit(attacker)) {
            machine.addTransition(new AIOneTimeEventTarget<>(() -> {
                CitizenState now = machine.getState();
                leaving.accept(now);
                if (now != CitizenState.FLEE) {
                    fleeFrom = now;
                }
                flee.start();
                return CitizenState.FLEE;
            }));
        }
    }

    /**
     * MC decideAiTask in FLEE: while MC calculateNextState from the state it fled from (MC lastState) gives that
     * state, it keeps fleeing (null); another one takes over: a meal, bedtime, mourning ({@code decide}, null to stay
     * asleep at night), or the rain, a break or work to do ({@code shouldWork}, MC's rain and work parts). Deviation
     * from MC: MC's lastState is its last decision, taken every 10 ticks; ours is the state it fled from.
     */
    public @Nullable CitizenState decideWhileFleeing(
            Function<CitizenState, @Nullable CitizenState> decide, BooleanSupplier shouldWork) {
        CitizenState next = decide.apply(fleeFrom);
        if (next == CitizenState.IDLE || next == CitizenState.WORKING) {
            next = shouldWork.getAsBoolean() ? CitizenState.WORKING : CitizenState.IDLE;
        }
        return next == null || next == fleeFrom ? null : next;
    }
}
