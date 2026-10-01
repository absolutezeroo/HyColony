package dev.hycolony.core.citizen.sleep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.building.module.ModuleProducer;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lying down and getting up (MC CitizenSleepHandler.trySleep and onWakeUp). */
class SleepHandlerTest {
    private static final BlockPos BED = new BlockPos(10, 64, 0);
    private final TestContexts t = new TestContexts();
    private final Colony c = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    private final CitizenData d = new CitizenData(1);
    private final BodyId body;
    private final SleepHandler handler;

    SleepHandlerTest() {
        c.citizens().restore(d);
        body = t.bodies.spawn(new WorldKey("world"), BED, 1, 1, "C1").orElseThrow();
        handler = new SleepHandler(c, d, body);
        t.bodies.beds.add(BED);
    }

    /** A building module counting its wake-ups. */
    static final class WakeCounter implements BuildingEventsModule {
        int wakeUps;

        @Override
        public void onWakeUp(Colony colony, Building building) {
            wakeUps++;
        }
    }

    /** A job counting its wake-ups. */
    static final class WakeJob extends Job {
        static final JobType TYPE = new JobType("test:wake", WakeJob::new);
        int wakeUps;

        WakeJob(CitizenData citizen) {
            super(TYPE, citizen);
        }

        @Override
        public JobAI createAI(Colony colony, BodyId body) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onWakeUp(Colony colony) {
            wakeUps++;
        }
    }

    private Building counted(BlockPos at) {
        BuildingType type =
                new BuildingType("test:counted", "hut.test", 5, List.of(new ModuleProducer("wake", WakeCounter::new)));
        Building b = Building.create(type, at, 0);
        c.buildings().add(b);
        return b;
    }

    @Test
    void lyingDownDropsTheHeldItemEndsLeisureAndRemembersTheBed() {
        t.bodies.setHeldItem(body, Optional.of(new ItemKey("axe")));
        d.setLeisureTime(500);

        assertTrue(handler.trySleep(BED));

        assertEquals(BED, t.bodies.bodies.get(body).inBed);
        assertNull(t.bodies.bodies.get(body).held);
        assertTrue(d.asleep());
        assertEquals(0, d.leisureTime());
        assertEquals(BED, d.bedPos());
    }

    @Test
    void refusedBedChangesNothing() {
        t.bodies.takenBeds.add(BED);

        assertFalse(handler.trySleep(BED));
        assertFalse(handler.trySleep(new BlockPos(50, 64, 0))); // no bed there

        assertFalse(d.asleep());
        assertNull(d.bedPos());
    }

    @Test
    void wakeUpTellsWorkJobAndHomeThenStandsUp() {
        Building work = counted(new BlockPos(30, 64, 0));
        Building home = counted(new BlockPos(40, 64, 0));
        d.setWorkBuilding(work.position());
        d.setHomeBuilding(home.position());
        WakeJob job = new WakeJob(d);
        d.setJob(job);
        handler.trySleep(BED);

        handler.wakeUp();

        assertEquals(1, work.module(WakeCounter.class).orElseThrow().wakeUps);
        assertEquals(1, home.module(WakeCounter.class).orElseThrow().wakeUps);
        assertEquals(1, job.wakeUps);
        assertNull(t.bodies.bodies.get(body).inBed);
        assertFalse(d.asleep());
        assertNull(d.bedPos());
    }

    @Test
    void wakeUpOfAnAwakeCitizenDoesNothing() {
        Building home = counted(new BlockPos(40, 64, 0));
        d.setHomeBuilding(home.position());

        handler.wakeUp();

        assertEquals(0, home.module(WakeCounter.class).orElseThrow().wakeUps);
    }

    @Test
    void leftBedForgetsTheBedWithoutHooks() {
        Building home = counted(new BlockPos(40, 64, 0));
        d.setHomeBuilding(home.position());
        handler.trySleep(BED);

        handler.leftBed();

        assertFalse(d.asleep());
        assertNull(d.bedPos());
        assertEquals(0, home.module(WakeCounter.class).orElseThrow().wakeUps);
    }
}
