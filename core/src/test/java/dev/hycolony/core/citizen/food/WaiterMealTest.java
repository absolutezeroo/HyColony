package dev.hycolony.core.citizen.food;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** MC CitizenAI.shouldEat's {@code lastState == EATING}: a waiter whose meal ended hungry goes back at once. */
class WaiterMealTest {
    private static final int MAX_TICKS = 3000;

    /** Draws 0 (a waiter goes to eat) while {@link #lucky}, else 1 (MC's 199 times in 200: it does not). */
    private boolean lucky = true;

    private final TestContexts t = contexts();
    private final CitizenData citizen = new CitizenData(1);
    private final BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
    private final JobType waiter = new JobType("test:waiter", c -> new WaiterJob(this.waiter, c));
    private CitizenAI ai;

    /** A job serving food, like MC's cook; its AI does nothing. */
    private static final class WaiterJob extends Job {
        WaiterJob(JobType type, CitizenData citizen) {
            super(type, citizen);
        }

        @Override
        public boolean servesFood() {
            return true;
        }

        @Override
        public JobAI createAI(Colony colony, BodyId body) {
            return new JobAI() {
                @Override
                public void tick() {}

                @Override
                public String stateName() {
                    return "serving";
                }

                @Override
                public boolean canBeInterrupted() {
                    return true;
                }
            };
        }
    }

    private TestContexts contexts() {
        TestContexts c = new TestContexts();
        c.bodies.instant = true;
        c.random = () -> new RandomGenerator() {
            @Override
            public long nextLong() {
                return lucky ? 0 : 2L << 32; // nextInt(200) is then 0, else 1
            }
        };
        return c;
    }

    private void tickUntil(BooleanSupplier done) {
        for (int i = 0; i < MAX_TICKS && !done.getAsBoolean(); i++) {
            t.clock.tick++;
            ai.tick();
        }
        assertTrue(done.getAsBoolean(), "not reached; state " + ai.state());
    }

    @Test
    void aWaiterWhoseMealEndedHungryGoesBackToEatAtOnce() {
        Colony colony = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        Building hall =
                Building.create(new BuildingType("test:hall", "test:hall", 5, List.of()), new BlockPos(5, 64, 0), 0);
        hall.setLevel(1);
        colony.buildings().add(hall);
        colony.citizens().restore(citizen);
        colony.citizens().onBodyLoaded(body, citizen.id());
        citizen.setWorkBuilding(hall.position());
        citizen.setJob(waiter.factory().apply(citizen));
        citizen.setSaturation(2);
        ai = new CitizenAI(colony, citizen, body);
        tickUntil(() -> ai.state() == CitizenState.EATING);
        lucky = false;

        tickUntil(() -> ai.state() != CitizenState.EATING); // no food at its hut: MC reset, DONE, without justAte
        tickUntil(() -> ai.state() == CitizenState.EATING); // the next decision, without the 1 in 200 draw
    }
}
