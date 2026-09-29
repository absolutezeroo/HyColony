package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
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
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** MC's leisure time (CitizenData.update, CitizenAI.calculateNextState): now and then a worker takes a break. */
class CitizenLeisureTest {
    private final TestContexts t = new TestContexts();
    private final Rolls rolls = new Rolls();
    private final Colony colony;
    private final CitizenData citizen = new CitizenData(1);
    private final BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));

    CitizenLeisureTest() {
        t.random = () -> rolls; // every draw rolls 0: a break starts at the first leisure draw
        colony = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        colony.citizens().restore(citizen);
    }

    /** A random source that records the bounds it is asked for and always rolls 0. */
    private static final class Rolls implements RandomGenerator {
        final List<Integer> bounds = new ArrayList<>();

        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return 0;
        }
    }

    private CitizenAI working(JobType type) {
        citizen.setJob(type.factory().apply(citizen));
        CitizenAI ai = new CitizenAI(colony, citizen, body);
        for (int i = 0; i < 30 && ai.state() != CitizenState.WORKING; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.WORKING, ai.state());
        return ai;
    }

    private static void tick(CitizenAI ai, int ticks) {
        for (int i = 0; i < ticks; i++) {
            ai.tick();
        }
    }

    /** One colony data tick for {@code citizen}, its body loaded; returns the bounds drawn by that tick. */
    private List<Integer> dataTick() {
        colony.citizens().onBodyLoaded(body, citizen.id());
        rolls.bounds.clear();
        colony.citizens().tickData();
        return rolls.bounds;
    }

    @Test
    void leisureStartsWithMcChanceWhichABetterHomeRaises() {
        Rolls draws = new Rolls();

        citizen.tickLeisure(60, 1, draws);
        new CitizenData(2).tickLeisure(60, 5, draws);
        new CitizenData(3).tickLeisure(60, 0, draws); // a home not built yet counts as level 1

        assertEquals(List.of(2400, 480, 2400), draws.bounds, "one chance in 20 x (120 / home level) per draw");
        assertEquals(CitizenData.LEISURE_TICKS, citizen.leisureTime());
    }

    @Test
    void leisureRunsThreeMinutesWithoutNewDraws() {
        Rolls draws = new Rolls();
        citizen.tickLeisure(60, 1, draws);

        citizen.tickLeisure(60, 1, draws);

        assertEquals(CitizenData.LEISURE_TICKS - 60, citizen.leisureTime());
        assertEquals(1, draws.bounds.size());
    }

    @Test
    void workerOnBreakStaysOffWorkUntilTheBreakEnds() {
        CitizenAI ai = working(TestJobs.TYPE);

        citizen.setLeisureTime(CitizenData.LEISURE_TICKS);
        tick(ai, 20);
        for (int i = 0; i < 200; i++) {
            ai.tick();
            assertNotEquals(CitizenState.WORKING, ai.state(), "back at work during its break, tick " + i);
        }
        citizen.setLeisureTime(0);
        tick(ai, 40);

        assertEquals(CitizenState.WORKING, ai.state());
    }

    @Test
    void workerOnBreakGoesOnWithWorkWhichCannotBeInterrupted() {
        CitizenAI ai = working(new JobType("test:busy", c -> new Job(TestJobs.TYPE, c) {
            @Override
            public JobAI createAI(Colony colony, BodyId body) {
                return new JobAI() {
                    @Override
                    public void tick() {}

                    @Override
                    public String stateName() {
                        return "DUMPING";
                    }

                    @Override
                    public boolean canBeInterrupted() {
                        return false;
                    }
                };
            }
        }));

        citizen.setLeisureTime(CitizenData.LEISURE_TICKS);
        tick(ai, 20);

        assertEquals(CitizenState.WORKING, ai.state());
    }

    @Test
    void colonyDataTickGivesABreakToACitizenWithABody() {
        assertEquals(List.of(2400), dataTick(), "no home: level 1");

        assertEquals(CitizenData.LEISURE_TICKS, citizen.leisureTime());
    }

    @Test
    void colonyDataTickDrawsWithTheLevelOfTheHome() {
        Building home = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(10, 64, 0), 0);
        home.setLevel(5);
        colony.buildings().add(home);
        citizen.setHomeBuilding(home.position());

        assertEquals(List.of(480), dataTick());
    }

    @Test
    void colonyDataTickLeavesACitizenWithoutALiveBody() {
        colony.citizens().onBodyLoaded(body, citizen.id());
        citizen.setLeisureTime(600);
        t.bodies.despawn(body);

        assertTrue(dataTick().isEmpty(), "MC CitizenData.update: nothing without a live entity");
        assertEquals(600, citizen.leisureTime());
    }
}
