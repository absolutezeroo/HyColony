package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.Permissions;
import dev.hycolony.core.colony.TerritoryIndex;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenAIWorkTest {
    private final TestContexts t = new TestContexts();

    @Test
    void fireReturnsCitizenToWander() {
        BlockPos hall = new BlockPos(0, 64, 0);
        Colony c = new Colony(t.context(), new TerritoryIndex(), 1, "T", hall,
                Permissions.createDefault(UUID.randomUUID(), "A"));
        CitizenData d = new CitizenData(1);
        c.citizens().restore(d);
        BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
        d.setJob(TestJobs.TYPE.factory().apply(d));

        CitizenAI ai = new CitizenAI(c, d, body);
        for (int i = 0; i < 30 && ai.state() != CitizenState.WORKING; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.WORKING, ai.state());

        d.setJob(null); // fire(): the job is removed
        for (int i = 0; i < 5 && ai.state() != CitizenState.IDLE; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.IDLE, ai.state()); // no longer working

        for (int i = 0; i < 420 && ai.state() != CitizenState.WANDERING; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.WANDERING, ai.state()); // and resumes its errand
    }

    /** Work buildings the AIs of {@link BoundJob} were created for, one entry per job AI tick. */
    private static final List<BlockPos> TICKED_FOR = new ArrayList<>();

    private static final class BoundJob extends Job {
        static final JobType TYPE = new JobType("test:bound", BoundJob::new);

        BoundJob(CitizenData citizen) { super(TYPE, citizen); }

        @Override
        public JobAI createAI(Colony colony, BodyId body) {
            BlockPos at = citizen().workBuilding();
            return new JobAI() {
                @Override public void tick() { TICKED_FOR.add(at); }
                @Override public String stateName() { return "working"; }
                @Override public boolean canBeInterrupted() { return true; }
            };
        }
    }

    @Test
    void rehiredElsewhereBeforeNextTickGetsANewJobAi() {
        TICKED_FOR.clear();
        BlockPos hutA = new BlockPos(10, 64, 0), hutB = new BlockPos(20, 64, 0);
        Colony c = new Colony(t.context(), new TerritoryIndex(), 1, "T", new BlockPos(0, 64, 0),
                Permissions.createDefault(UUID.randomUUID(), "A"));
        CitizenData d = new CitizenData(1);
        c.citizens().restore(d);
        BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
        d.setJob(new BoundJob(d));
        d.setWorkBuilding(hutA);
        CitizenAI ai = new CitizenAI(c, d, body);
        for (int i = 0; i < 30 && TICKED_FOR.isEmpty(); i++) {
            ai.tick();
        }
        assertEquals(List.of(hutA), TICKED_FOR);

        d.setJob(new BoundJob(d)); // hut A removed (fired), hired at hut B, all before the next AI tick
        d.setWorkBuilding(hutB);
        ai.tick();

        assertEquals(hutB, TICKED_FOR.get(TICKED_FOR.size() - 1));
        assertEquals(CitizenState.WORKING, ai.state());
    }
}
