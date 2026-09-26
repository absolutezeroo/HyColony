package dev.hycolony.core.citizen;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Top-level citizen AI. Idle, wander, or work its job if it has one. */
public final class CitizenAI {
    private static final System.Logger LOG = System.getLogger(CitizenAI.class.getName());
    private static final int WANDER_RADIUS = 10;
    private static final int IDLE_MIN_TICKS = 200, IDLE_MAX_TICKS = 400;
    private static final int WANDER_TIMEOUT_TICKS = 600;

    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final CitizenBodies bodies;
    private final RandomGenerator random;
    private final TickRateStateMachine<CitizenState> machine;
    private int idleTicksLeft;
    private int wanderTicks;
    private JobAI jobAI;
    /** The job and work building {@link #jobAI} was created for. */
    private Job aiJob;

    private BlockPos aiWorkBuilding;

    public CitizenAI(Colony colony, CitizenData data, BodyId body) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.bodies = colony.context().bodies();
        this.random = colony.context().random();
        this.idleTicksLeft = nextIdle();
        this.machine = new TickRateStateMachine<>(CitizenState.IDLE, this::onException);
        machine.addTransition(new AITarget<>(CitizenState.IDLE, (IStateSupplier<CitizenState>) this::idle, 20));
        machine.addTransition(new AITarget<>(CitizenState.WANDERING, (IStateSupplier<CitizenState>) this::wander, 5));
        machine.addTransition(new AITarget<>(CitizenState.WORKING, (IStateSupplier<CitizenState>) this::work, 1));
    }

    public void tick() {
        machine.tick();
    }

    public CitizenState state() {
        return machine.getState();
    }

    /** The job AI's own line while working. */
    public Optional<Msg> jobActivity() {
        return state() == CitizenState.WORKING && jobAI != null ? jobAI.describe() : Optional.empty();
    }

    private void onException(RuntimeException e) {
        LOG.log(System.Logger.Level.WARNING, "Citizen AI failed for " + data.name(), e);
        machine.reset();
    }

    private CitizenState idle() {
        if (data.job().isPresent() && bodies.isAlive(body)) {
            startJob(data.job().get());
            return CitizenState.WORKING;
        }
        idleTicksLeft -= 20;
        if (idleTicksLeft > 0) {
            return null;
        }
        Vec3 here = bodies.position(body).orElse(null);
        if (here == null) {
            return null;
        }
        BlockPos anchor = colony.buildings().townHall().map(b -> b.position()).orElse(here.toBlockPos());
        int dx = random.nextInt(2 * WANDER_RADIUS + 1) - WANDER_RADIUS;
        int dz = random.nextInt(2 * WANDER_RADIUS + 1) - WANDER_RADIUS;
        Vec3 target = new Vec3(anchor.x() + dx + 0.5, here.y(), anchor.z() + dz + 0.5);
        bodies.moveTo(body, target);
        wanderTicks = 0;
        return CitizenState.WANDERING;
    }

    private CitizenState wander() {
        wanderTicks += 5;
        NavStatus status = bodies.navStatus(body);
        boolean done = status == NavStatus.ARRIVED || status == NavStatus.BLOCKED || status == NavStatus.FAILED;
        if (done || wanderTicks >= WANDER_TIMEOUT_TICKS) {
            idleTicksLeft = nextIdle();
            return CitizenState.IDLE;
        }
        return null;
    }

    private CitizenState work() {
        Job job = data.job().orElse(null);
        if (job == null) {
            jobAI = null;
            aiJob = null;
            return CitizenState.IDLE;
        }
        if (job != aiJob || !Objects.equals(data.workBuilding(), aiWorkBuilding)) {
            startJob(job); // fired and hired again (elsewhere) between two ticks: bound to the new hut
        }
        jobAI.tick();
        return null;
    }

    private void startJob(Job job) {
        aiJob = job;
        aiWorkBuilding = data.workBuilding();
        jobAI = job.createAI(colony, body);
    }

    private int nextIdle() {
        return IDLE_MIN_TICKS + random.nextInt(IDLE_MAX_TICKS - IDLE_MIN_TICKS + 1);
    }
}
