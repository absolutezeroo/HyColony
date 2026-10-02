package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
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
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", hall, Permissions.createDefault(UUID.randomUUID(), "A")));
        c.claimAround(hall, 4); // in its territory, an idle citizen wanders
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

        t.bodies.bodies.get(body).status = NavStatus.ARRIVED; // the job's last walk is over
        int moves = t.bodies.moves.size();
        for (int i = 0; i < 120 && t.bodies.moves.size() == moves; i++) {
            ai.tick();
        }
        assertEquals(moves + 1, t.bodies.moves.size()); // and wanders again
        assertEquals(CitizenState.IDLE, ai.state());
    }

    @Test
    void aWorkerLosingItsJobWalksAtNormalSpeedAgain() {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        CitizenData d = new CitizenData(1);
        c.citizens().restore(d);
        BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
        d.setJob(TestJobs.TYPE.factory().apply(d));
        CitizenAI ai = new CitizenAI(c, d, body);
        for (int i = 0; i < 30 && ai.state() != CitizenState.WORKING; i++) {
            ai.tick();
        }
        t.bodies.setMovementSpeed(body, 1.5); // a courier's Agility bonus (MC JobDeliveryman.onLevelUp)

        d.setJob(null);
        for (int i = 0; i < 5 && ai.state() != CitizenState.IDLE; i++) {
            ai.tick();
        }

        assertEquals(1.0, t.bodies.bodies.get(body).speed); // MC BuildingDeliveryman removes the modifier
    }

    @Test
    void aJoblessCitizenReloadedWithASpeedBonusWalksAtNormalSpeed() {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        CitizenData d = new CitizenData(1);
        c.citizens().restore(d);
        BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
        t.bodies.setMovementSpeed(body, 1.5); // kept by the body after a crash between save and job loss

        new CitizenAI(c, d, body);

        assertEquals(1.0, t.bodies.bodies.get(body).speed);
    }

    @Test
    void aRehiredWorkerStartsAtNormalSpeed() {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        CitizenData d = new CitizenData(1);
        c.citizens().restore(d);
        BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
        d.setJob(TestJobs.TYPE.factory().apply(d));
        CitizenAI ai = new CitizenAI(c, d, body);
        for (int i = 0; i < 30 && ai.state() != CitizenState.WORKING; i++) {
            ai.tick();
        }
        t.bodies.setMovementSpeed(body, 1.5); // a courier's Agility bonus

        d.setJob(TestJobs.TYPE.factory().apply(d)); // another job, hired between two ticks
        ai.tick();

        assertEquals(1.0, t.bodies.bodies.get(body).speed);
    }

    /** Work buildings the AIs of {@link BoundJob} were created for, one entry per job AI tick. */
    private static final List<BlockPos> TICKED_FOR = new ArrayList<>();

    private static final class BoundJob extends Job {
        static final JobType TYPE = new JobType("test:bound", BoundJob::new);

        BoundJob(CitizenData citizen) {
            super(TYPE, citizen);
        }

        @Override
        public JobAI createAI(Colony colony, BodyId body) {
            BlockPos at = citizen().workBuilding();
            return new JobAI() {
                @Override
                public void tick() {
                    TICKED_FOR.add(at);
                }

                @Override
                public String stateName() {
                    return "working";
                }

                @Override
                public boolean canBeInterrupted() {
                    return true;
                }
            };
        }
    }

    private static final class ThrowingJob extends Job {
        static final JobType TYPE = new JobType("test:throwing", ThrowingJob::new);

        ThrowingJob(CitizenData citizen) {
            super(TYPE, citizen);
        }

        @Override
        public JobAI createAI(Colony colony, BodyId body) {
            return new JobAI() {
                @Override
                public void tick() {
                    throw new IllegalStateException("broken job AI");
                }

                @Override
                public String stateName() {
                    return "broken";
                }

                @Override
                public boolean canBeInterrupted() {
                    return false;
                }
            };
        }
    }

    /** MC AbstractEntityCitizen: the citizen AI's exception handler only logs; the citizen keeps its state. */
    @Test
    void aThrowingJobAiLeavesTheCitizenWorking() {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        CitizenData d = new CitizenData(1);
        c.citizens().restore(d);
        BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
        d.setJob(new ThrowingJob(d));
        CitizenAI ai = new CitizenAI(c, d, body);
        for (int i = 0; i < 30 && ai.state() != CitizenState.WORKING; i++) {
            ai.tick();
        }

        for (int i = 0; i < 20; i++) {
            ai.tick();
            assertEquals(CitizenState.WORKING, ai.state());
        }
    }

    @Test
    void rehiredElsewhereBeforeNextTickGetsANewJobAi() {
        TICKED_FOR.clear();
        BlockPos hutA = new BlockPos(10, 64, 0), hutB = new BlockPos(20, 64, 0);
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
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
