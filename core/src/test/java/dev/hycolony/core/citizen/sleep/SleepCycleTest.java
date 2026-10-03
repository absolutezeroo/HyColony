package dev.hycolony.core.citizen.sleep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.home.BedModule;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** A colony's night: work stops, citizens go to bed, and get up at dawn (MC CitizenAI with EntityAISleep). */
class SleepCycleTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private static final BlockPos HOUSE = new BlockPos(20, 64, 0);
    private static final BlockPos BED_POS = new BlockPos(21, 64, 0);
    private static final BlockKey BED = new BlockKey("bed");
    private static final int MAX_TICKS = 4000;

    private final TestContexts t = contexts();
    private final UUID owner = UUID.randomUUID();
    private final Colony c = colony();
    private final Building house = house();
    private final CitizenData d = new CitizenData(1);
    private BodyId body;

    private static TestContexts contexts() {
        TestContexts t = new TestContexts();
        t.bodies.instant = true;
        t.catalog.beds.add(BED);
        t.catalog.kinds.put(BED, BlockKind.NON_SOLID);
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String type, int level, int rotation) {
                return Optional.of(new Blueprint("k", List.of(), new BlockPos(-3, 0, -3), new BlockPos(3, 4, 3)));
            }

            @Override
            public List<String> styles() {
                return List.of("s");
            }
        };
        return t;
    }

    private Colony colony() {
        TerritoryIndex territory = new TerritoryIndex();
        territory.claimSquare(1, ClaimCell.of(HALL), 4);
        Colony colony = new Colony(
                t.context(), territory, new Colony.Founding(1, "T", HALL, Permissions.createDefault(owner, "A")));
        colony.buildings().add(Building.create(BuildingTypes.TOWN_HALL, HALL, 0));
        return colony;
    }

    private Building house() {
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, HOUSE, 0);
        b.setLevel(1);
        b.setBuilt(true);
        b.setStyle("s");
        c.buildings().add(b);
        b.module(BedModule.class).orElseThrow().addBed(BED_POS);
        t.blocks.blocks.put(BED_POS, new BlockState(BED, 0));
        t.bodies.beds.add(BED_POS);
        return b;
    }

    /** The citizen, housed, its body standing at {@code at} with an AI. */
    private void citizen(BlockPos at) {
        c.citizens().restore(d);
        house.module(LivingModule.class).orElseThrow().assign(c, house, d);
        body = t.bodies.existing(1, d.id(), Vec3.center(at));
        c.citizens().onBodyLoaded(body, d.id());
    }

    private CitizenState state() {
        return c.citizens().aiState(d.id()).orElseThrow();
    }

    private void tickUntil(BooleanSupplier done) {
        for (int i = 0; i < MAX_TICKS && !done.getAsBoolean(); i++) {
            t.clock.tick++;
            c.citizens().tickAi();
        }
        assertTrue(done.getAsBoolean(), "not reached in " + MAX_TICKS + " ticks");
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            t.clock.tick++;
            c.citizens().tickAi();
        }
    }

    private void nightfall() {
        t.clock.dayTime = SleepDecision.NIGHT;
    }

    @Test
    void workerStopsWorkingAndGoesToBedAtNight() {
        d.setJob(TestJobs.TYPE.factory().apply(d));
        citizen(HALL);
        d.vitals().track();
        tickUntil(() -> state() == CitizenState.WORKING);

        nightfall();
        tickUntil(() -> state() == CitizenState.SLEEP);
        tickUntil(d::asleep);

        assertEquals(BED_POS, t.bodies.bodies.get(body).inBed);
        assertTrue(
                d.vitals().history().stream()
                        .anyMatch(e -> e.detail().equals(Msg.of("hycolony.debug.history.leftWork.sleep", "SLEEP"))),
                "HyLens says why it stopped");
    }

    /** MC keeps the job's AI while its worker sleeps and resets it (resetAI) when it enters WORK again. */
    @Test
    void wakingWorkerResumesTheSameJobAiFromItsFirstState() {
        d.setJob(TestJobs.TYPE.factory().apply(d));
        citizen(HALL);
        tickUntil(() -> state() == CitizenState.WORKING);
        ticks(1);
        TestJobs.TestJobAI ai = jobAi();
        assertEquals(1, ai.resets, "reset on entering WORK");

        nightfall();
        tickUntil(d::asleep);
        assertSame(ai, jobAi(), "kept while asleep");
        t.clock.dayTime = 0;
        tickUntil(() -> state() == CitizenState.WORKING);
        ticks(1);

        assertSame(ai, jobAi());
        assertEquals(2, ai.resets);
    }

    /** MC keeps a courier's speed modifier (JobDeliveryman.onLevelUp) until it is unassigned, asleep too. */
    @Test
    void aSleepingWorkerKeepsTheSpeedItsJobGaveIt() {
        d.setJob(TestJobs.TYPE.factory().apply(d));
        citizen(HALL);
        tickUntil(() -> state() == CitizenState.WORKING);
        t.bodies.bodies.get(body).speed = 1.3; // as the courier's AI sets its Agility speed

        nightfall();
        tickUntil(d::asleep);

        assertEquals(1.3, t.bodies.bodies.get(body).speed);
    }

    /** MC DeliverymanAssignmentModule removes the speed modifier on unassignment, whatever the courier is doing. */
    @Test
    void aWorkerFiredInItsSleepLosesItsJobAiAndSpeed() {
        d.setJob(TestJobs.TYPE.factory().apply(d));
        citizen(HALL);
        tickUntil(() -> state() == CitizenState.WORKING);
        t.bodies.bodies.get(body).speed = 1.3;
        nightfall();
        tickUntil(d::asleep);

        WorkerModule.free(c, d);
        ticks(1);

        assertEquals(1.0, t.bodies.bodies.get(body).speed);
        assertTrue(c.citizens().ai(d.id()).orElseThrow().jobAi().isEmpty());
    }

    private TestJobs.TestJobAI jobAi() {
        return (TestJobs.TestJobAI)
                c.citizens().ai(d.id()).orElseThrow().jobAi().orElseThrow();
    }

    @Test
    void sleepOutranksLeisure() {
        citizen(HALL);
        nightfall();
        d.setLeisureTime(1000);

        tickUntil(() -> state() == CitizenState.SLEEP);
        tickUntil(d::asleep);

        assertEquals(0, d.leisureTime());
    }

    @Test
    void wakesAtDawnAndWorksAgain() {
        d.setJob(TestJobs.TYPE.factory().apply(d));
        citizen(HALL);
        nightfall();
        tickUntil(d::asleep);

        t.clock.dayTime = 0;
        tickUntil(() -> state() != CitizenState.SLEEP);

        assertFalse(d.asleep());
        assertNull(t.bodies.bodies.get(body).inBed);
        tickUntil(() -> state() == CitizenState.WORKING);
    }

    @Test
    void asleepDecidesOnlyEveryFifteenSeconds() {
        citizen(HOUSE);
        nightfall();
        tickUntil(d::asleep);
        ticks(SleepDecision.SLEEP_DECIDE_DELAY_TICKS); // past the decision made while it lay down

        t.clock.dayTime = 0; // dawn: the next decision wakes it
        int ticks = 0;
        while (state() == CitizenState.SLEEP && ticks < MAX_TICKS) {
            ticks(1);
            ticks++;
        }

        assertTrue(ticks > 10, "not at the next 10-tick decision: " + ticks);
        assertTrue(ticks <= SleepDecision.SLEEP_DECIDE_DELAY_TICKS, "within 15 s: " + ticks);
    }

    @Test
    void teleportWakesFirst() {
        citizen(HOUSE);
        nightfall();
        tickUntil(d::asleep);

        c.citizens().ai(d.id()).orElseThrow().teleport(Vec3.center(new BlockPos(5, 64, 5)));

        assertFalse(d.asleep());
        assertNull(t.bodies.bodies.get(body).inBed);
    }

    @Test
    void spawnedBodyWakesASavedSleeper() {
        d.setAsleep(true);
        d.setBedPos(BED_POS);

        citizen(HOUSE); // Hytale saves no NPC in bed: a body appears standing

        assertFalse(d.asleep());
        assertNull(d.bedPos());
    }

    /** MC CitizenData.initEntityValues: onWakeUp at every body appearance, which ends any leisure break. */
    @Test
    void anAppearingBodyEndsTheLeisureBreak() {
        d.setLeisureTime(500);

        citizen(HOUSE);

        assertEquals(0, d.leisureTime());
    }

    @Test
    void bodilessCitizenRespawnsAtDawn() {
        c.citizens().restore(d);

        c.citizens().onWakeUp();

        assertTrue(c.citizens().bodyOf(d.id()).filter(t.bodies::isAlive).isPresent());
    }

    @Test
    void removedHomeAtNightStillWakesAtDawn() {
        citizen(HOUSE);
        nightfall();
        tickUntil(d::asleep);
        house.module(LivingModule.class).orElseThrow().remove(c, house, d.id());

        t.clock.dayTime = 0;
        tickUntil(() -> !d.asleep());

        assertNull(t.bodies.bodies.get(body).inBed);
    }

    @Test
    void nightfallRearmsTheAllAsleepNotice() {
        t.players.online.put(owner, HALL);
        c.citizens().setAllAsleepAnnounced(true);
        t.clock.daytime = false;

        for (int i = 0; i < 100; i++) {
            t.clock.tick++;
            c.tick();
        }

        assertFalse(c.citizens().allAsleepAnnounced());
    }
}
