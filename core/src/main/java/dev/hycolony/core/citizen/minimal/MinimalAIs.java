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
import java.util.function.Consumer;

/**
 * A citizen's minimal AIs beside its sleep, meals, wander and work (MC CitizenAI.minimalAI): mourning
 * ({@link MournAI}) and fleeing ({@link FleeAI}), whose states it registers on the citizen's AI.
 */
public final class MinimalAIs {
    private final MournAI mourn;
    private final FleeAI flee;
    private final TickRateStateMachine<CitizenState> machine;

    public MinimalAIs(
            Colony colony,
            CitizenData data,
            BodyId body,
            CitizenWander wander,
            TickRateStateMachine<CitizenState> machine) {
        this.mourn = new MournAI(colony, data, body, wander);
        this.flee = new FleeAI(colony, body);
        this.machine = machine;
        machine.addTransition(
                new AITarget<>(CitizenState.MOURN, (IStateSupplier<CitizenState>) mourn::tick, MournAI.RATE_TICKS));
        machine.addTransition(
                new AITarget<>(CitizenState.FLEE, (IStateSupplier<CitizenState>) flee::tick, FleeAI.RATE_TICKS));
    }

    /** MC EntityAIMournCitizen.reset, on entering MOURN. */
    public void startMourning() {
        mourn.reset();
    }

    /**
     * {@link FleeAI#hit}: hit by {@code attacker}, it runs; then (MC AIOneTimeEventTarget(FLEE)) it flees from the next
     * tick, {@code leaving} told the state it leaves.
     */
    public void hit(Optional<Vec3> attacker, Consumer<CitizenState> leaving) {
        if (flee.hit(attacker)) {
            machine.addTransition(new AIOneTimeEventTarget<>(() -> {
                leaving.accept(machine.getState());
                flee.start();
                return CitizenState.FLEE;
            }));
        }
    }
}
