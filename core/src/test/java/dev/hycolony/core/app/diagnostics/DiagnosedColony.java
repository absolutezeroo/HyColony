package dev.hycolony.core.app.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A founded colony whose one citizen has a body and, once hired, a job AI the test steers; for the invariants. */
final class DiagnosedColony {
    static final BlockPos HALL = new BlockPos(0, 64, 0);

    final TestContexts t = new TestContexts();
    final Colony colony;
    final CitizenData citizen = new CitizenData(1);
    final BodyId body;
    final CitizenWalkReports walks;
    /** What the job's AI says it is doing. */
    String step = "START_WORKING";
    /** Whether the job's AI waits legitimately. */
    boolean waiting;
    /** Whether the job's AI serves the head of {@link #queue}. */
    boolean servesHead;
    /** The exceptions the job's AI caught. */
    int failures;

    final List<RequestToken> queue = new ArrayList<>();

    DiagnosedColony() {
        ColonyManager manager = t.manager();
        UUID alice = UUID.randomUUID();
        manager.foundation().begin(alice, "Alice", HALL, 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        colony.citizens().restore(citizen);
        body = t.bodies.existing(colony.id(), 1, new Vec3(0.5, 64, 0.5));
        colony.citizens().onBodyLoaded(body, 1);
        walks = new CitizenWalkReports(colony, citizen);
    }

    /** Hires the citizen and ticks its AI until it works. */
    void working() {
        citizen.setJob(job());
        for (int i = 0; i < 30 && colony.citizens().aiState(1).orElse(null) != CitizenState.WORKING; i++) {
            tickAi();
        }
        assertEquals(Optional.of(CitizenState.WORKING), colony.citizens().aiState(1));
    }

    /** One tick of the clock and of the citizens' AIs. */
    void tickAi() {
        t.clock.tick++;
        colony.citizens().tickAi();
    }

    JobAI jobAi() {
        return colony.citizens().ai(1).flatMap(CitizenAI::jobAi).orElseThrow();
    }

    List<Violation.Code> codes() {
        return Invariants.check(colony).stream().map(Violation::code).toList();
    }

    /** The message keys of the broken invariants: which case of an invariant each is. */
    List<String> keys() {
        return Invariants.check(colony).stream().map(v -> v.detail().key()).toList();
    }

    /** A request of the town hall for this citizen, which the retrying resolver takes. */
    RequestToken request() {
        return colony.requests()
                .createAndAssign(
                        colony.buildings().at(HALL).orElseThrow(),
                        new StackRequest(new ItemKey("Wood_Planks"), 1, 1, true),
                        citizen.id());
    }

    private Job job() {
        return new JobType("test:invariants", c -> new Job(TestJobs.TYPE, c) {
                    @Override
                    public JobAI createAI(Colony colony, BodyId body) {
                        return new SteeredAI();
                    }
                })
                .factory()
                .apply(citizen);
    }

    /** A job AI saying what the test sets. */
    private final class SteeredAI implements JobAI {
        @Override
        public void tick() {}

        @Override
        public String stateName() {
            return step;
        }

        @Override
        public boolean canBeInterrupted() {
            return true;
        }

        @Override
        public int failures() {
            return failures;
        }

        @Override
        public boolean waiting() {
            return waiting;
        }

        @Override
        public boolean servesQueueHead() {
            return servesHead;
        }

        @Override
        public List<RequestToken> queue() {
            return queue;
        }
    }
}
