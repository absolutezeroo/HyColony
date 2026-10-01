package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.happiness.CitizenHappiness;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.job.JobStatus;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenManagerTest {
    private final TestContexts t = new TestContexts();
    private final BlockPos hall = new BlockPos(0, 64, 0);

    private Colony colonyWithTownHall() {
        TerritoryIndex territory = new TerritoryIndex();
        territory.claimSquare(1, ClaimCell.of(hall), 4);
        Colony c = new Colony(
                t.context(),
                territory,
                new Colony.Founding(1, "Test", hall, Permissions.createDefault(UUID.randomUUID(), "A")));
        c.buildings().add(Building.create(BuildingTypes.TOWN_HALL, hall, 0));
        return c;
    }

    /** Slow ticks as the colony would run them. */
    private void slowTicks(Colony c, int n) {
        for (int i = 0; i < n; i++) {
            c.citizens().onColonyTick();
        }
    }

    @Test
    void initialCitizensFollowMineColoniesTimers() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 1); // 600 - 500 = 100 > 0
        assertEquals(0, c.citizens().all().size());
        slowTicks(c, 1); // -400 -> spawn, reset to 1200
        assertEquals(1, c.citizens().all().size());
        slowTicks(c, 2); // 700, 200
        assertEquals(1, c.citizens().all().size());
        slowTicks(c, 1); // -300 -> spawn
        assertEquals(2, c.citizens().all().size());
        slowTicks(c, 30);
        assertEquals(4, c.citizens().all().size()); // capped at initialCitizenAmount
        assertEquals(4, t.bodies.aliveCount());
    }

    @Test
    void noSpawnWithoutTownHall() {
        TerritoryIndex territory = new TerritoryIndex();
        Colony c = new Colony(
                t.context(),
                territory,
                new Colony.Founding(1, "T", hall, Permissions.createDefault(UUID.randomUUID(), "A")));
        slowTicks(c, 20);
        assertEquals(0, c.citizens().all().size());
    }

    @Test
    void gendersAreBalancedAfterFirstCitizen() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 40);
        long females = c.citizens().all().stream()
                .filter(d -> d.gender() == Gender.FEMALE)
                .count();
        assertEquals(2, females);
    }

    @Test
    void idsStartAtOneAndSkillsRespectInitialCap() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 40);
        assertEquals(
                java.util.List.of(1, 2, 3, 4),
                c.citizens().all().stream().map(CitizenData::id).toList());
        for (CitizenData d : c.citizens().all()) {
            for (Skill s : Skill.values()) {
                assertTrue(d.skills().level(s) >= 1 && d.skills().level(s) <= 9); // cap (int)5.5*2 = 10
            }
            assertFalse(d.name().isBlank());
            assertEquals(CitizenData.MAX_SATURATION, d.saturation());
        }
    }

    @Test
    void deadBodyRespawnsOnlyAfterTimerAndIfLoaded() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 2);
        CitizenData d = c.citizens().all().iterator().next();
        BodyId body = c.citizens().bodyOf(d.id()).orElseThrow();
        t.bodies.despawn(body);
        t.world.loaded = false;
        slowTicks(c, 12); // respawn timer (6000) elapses, but chunk unloaded
        assertTrue(c.citizens().bodyOf(d.id()).map(b -> !t.bodies.isAlive(b)).orElse(true));
        t.world.loaded = true;
        slowTicks(c, 13);
        assertTrue(t.bodies.isAlive(c.citizens().bodyOf(d.id()).orElseThrow()));
    }

    /** MC updateEntityIfNecessary: respawn, last position, work and home buildings; the first loaded one wins. */
    @Test
    void aDeadCitizenRespawnsAtItsWorkBuildingWhenItsLastPositionIsUnloaded() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 2);
        CitizenData d = c.citizens().all().iterator().next();
        t.bodies.despawn(c.citizens().bodyOf(d.id()).orElseThrow());
        BlockPos last = new BlockPos(500, 64, 500);
        BlockPos work = new BlockPos(20, 64, 0);
        d.setLastPosition(Vec3.center(last));
        d.setWorkBuilding(work);
        t.world.unloaded.add(last);

        slowTicks(c, 13); // past the respawn timer (6000)

        BodyId body = c.citizens().bodyOf(d.id()).orElseThrow();
        assertTrue(t.bodies.isAlive(body));
        assertEquals(Vec3.center(work), t.bodies.position(body).orElseThrow());
    }

    /** MC spawnOrCreateCivilian: where no body can appear (getSpawnPoint null), the next candidate is tried. */
    @Test
    void aRespawnThatFailsAtItsLastPositionTriesItsWorkBuilding() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 2);
        CitizenData d = c.citizens().all().iterator().next();
        t.bodies.despawn(c.citizens().bodyOf(d.id()).orElseThrow());
        BlockPos last = new BlockPos(30, 64, 30);
        BlockPos work = new BlockPos(20, 64, 0);
        d.setLastPosition(Vec3.center(last));
        d.setWorkBuilding(work);
        t.bodies.refuseSpawnAt.add(last);

        slowTicks(c, 13);

        BodyId body = c.citizens().bodyOf(d.id()).orElseThrow();
        assertTrue(t.bodies.isAlive(body));
        assertEquals(Vec3.center(work), t.bodies.position(body).orElseThrow());
    }

    @Test
    void bodyLoadedTwiceKeepsFirstAndDespawnsSecond() {
        Colony c = colonyWithTownHall();
        CitizenData d = new CitizenData(7);
        c.citizens().restore(d);
        BodyId first = t.bodies.existing(1, 7, new Vec3(1, 64, 1));
        BodyId second = t.bodies.existing(1, 7, new Vec3(2, 64, 2));
        c.citizens().onBodyLoaded(first, 7);
        c.citizens().onBodyLoaded(second, 7);
        assertEquals(first, c.citizens().bodyOf(7).orElseThrow());
        assertTrue(t.bodies.isAlive(first));
        assertFalse(t.bodies.isAlive(second));
    }

    @Test
    void bodyOfUnknownCitizenIsDespawned() {
        Colony c = colonyWithTownHall();
        BodyId stray = t.bodies.existing(1, 99, new Vec3(0, 64, 0));
        c.citizens().onBodyLoaded(stray, 99);
        assertFalse(t.bodies.isAlive(stray));
    }

    @Test
    void loadingSameBodyTwiceKeepsAiState() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 2);
        CitizenData d = c.citizens().all().iterator().next();
        d.setJob(TestJobs.TYPE.factory().apply(d));
        BodyId body = c.citizens().bodyOf(d.id()).orElseThrow();
        for (int i = 0; i < 500 && c.citizens().aiState(d.id()).orElseThrow() != CitizenState.WORKING; i++) {
            c.citizens().tickAi();
        }
        assertEquals(CitizenState.WORKING, c.citizens().aiState(d.id()).orElseThrow());

        c.citizens().onBodyLoaded(body, d.id()); // the exact same body, loaded again

        assertEquals(CitizenState.WORKING, c.citizens().aiState(d.id()).orElseThrow());
    }

    @Test
    void tickDataRecordsLastPosition() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 2);
        CitizenData d = c.citizens().all().iterator().next();
        BodyId body = c.citizens().bodyOf(d.id()).orElseThrow();
        t.bodies.bodies.get(body).position = new Vec3(5, 64, 5);
        c.citizens().tickData();
        assertEquals(new Vec3(5, 64, 5), d.lastPosition());
    }

    @Test
    void aNewJobOrANewBodyPutsTheJobStatusBackToIdle() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 2);
        CitizenData d = c.citizens().all().iterator().next();
        d.setJobStatus(JobStatus.STUCK);
        d.setJob(TestJobs.TYPE.factory().apply(d));
        assertEquals(JobStatus.IDLE, d.jobStatus());

        d.setJobStatus(JobStatus.STUCK);
        t.bodies.despawn(c.citizens().bodyOf(d.id()).orElseThrow()); // its chunk unloaded, then loaded again
        c.citizens().onBodyLoaded(t.bodies.existing(1, d.id(), new Vec3(0, 64, 0)), d.id());
        assertEquals(JobStatus.IDLE, d.jobStatus()); // MC initEntityValues: a body loaded anew

        d.setJobStatus(JobStatus.STUCK);
        d.setJob(null);
        assertEquals(JobStatus.STUCK, d.jobStatus()); // MC onJobChanged(null) leaves it
    }

    @Test
    void aNewCitizensSkillsAreCappedByTheColonysHappiness() {
        Colony c = colonyWithTownHall();
        for (int id = 1; id <= 3; id++) {
            c.citizens().restore(new CitizenData(id)); // homeless and jobless: unhappy
        }
        assertTrue(((int) CitizenHappiness.overall(c)) * 2 < 5, "the test needs an unhappy colony");

        slowTicks(c, 2); // the fourth initial citizen: MC's floor of 5 for those

        CitizenData fourth = c.citizens().get(4).orElseThrow();
        for (Skill s : Skill.values()) {
            assertTrue(fourth.skills().level(s) < 5, s.name()); // MC initForNewCivilian: random below the cap
        }
    }

    @Test
    void walkingBetweenTwoSamplesMakesACitizenHungry() {
        Colony c = colonyWithTownHall();
        slowTicks(c, 2);
        CitizenData d = c.citizens().all().iterator().next();
        BodyId body = c.citizens().bodyOf(d.id()).orElseThrow();
        c.citizens().tickData();
        for (int x = 1; x <= 5; x++) {
            t.bodies.bodies.get(body).position = new Vec3(x * 10, 64, 0);
            c.citizens().tickData();
        }
        assertEquals(0.02, d.hunger().pending(), 1e-9); // 50 blocks x 0.6 = 30 > 25: one continuous action
    }
}
