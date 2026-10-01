package dev.hycolony.core.citizen.happiness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.JobStatus;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC CitizenHappinessHandler and its factor functions. */
class CitizenHappinessTest {
    private static final double EPS = 1e-9;

    private final TestContexts t = new TestContexts();
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    private final CitizenData citizen = new CitizenData(1);

    CitizenHappinessTest() {
        colony.citizens().restore(citizen);
    }

    private double factor(String id) {
        return citizen.happiness().get(id).orElseThrow().factor(colony, citizen);
    }

    private Building residence(int level) {
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(10, 64, 10), 0);
        b.setLevel(level);
        colony.buildings().add(b);
        return b;
    }

    @Test
    void aNewCitizenHasMcTenModifiers() {
        assertEquals(10, citizen.happiness().modifiers().size());
    }

    @Test
    void securityWithoutGuardsFallsWithTheWorkers() {
        assertEquals(1 / (2 * 2 / 3.0), factor(HappinessIds.SECURITY), EPS); // guards 1, workers 1 + 1
        colony.citizens().restore(new CitizenData(2));
        assertEquals(1 / (3 * 2 / 3.0), factor(HappinessIds.SECURITY), EPS);
    }

    @Test
    void socialCountsUnemployedHomelessAndHungry() {
        assertEquals(-1, factor(HappinessIds.SOCIAL), EPS); // (1 - (unemployed + homeless)) / 1
        citizen.setJob(TestJobs.TYPE.factory().apply(citizen));
        citizen.setHomeBuilding(residence(1).position());
        assertEquals(1, factor(HappinessIds.SOCIAL), EPS);
        citizen.setSaturation(1);
        assertEquals(0, factor(HappinessIds.SOCIAL), EPS);
    }

    @Test
    void housingIsTheHomeLevelOverThree() {
        assertEquals(0, factor(HappinessIds.HOMELESSNESS), EPS);
        citizen.setHomeBuilding(residence(4).position());
        assertEquals(4 / 3.0, factor(HappinessIds.HOMELESSNESS), EPS);
    }

    @Test
    void unemploymentFollowsTheWorkHut() {
        assertEquals(0.5, factor(HappinessIds.UNEMPLOYMENT), EPS);
        Building hut = Building.create(ConstructionBuildingTypes.BUILDER, new BlockPos(20, 64, 0), 0);
        hut.setLevel(4);
        colony.buildings().add(hut);
        citizen.setWorkBuilding(hut.position());
        assertEquals(2, factor(HappinessIds.UNEMPLOYMENT), EPS);
        hut.setLevel(3);
        assertEquals(1, factor(HappinessIds.UNEMPLOYMENT), EPS);
        citizen.setChild(true);
        assertEquals(1, factor(HappinessIds.UNEMPLOYMENT), EPS);
        assertEquals(0, factor(HappinessIds.SCHOOL), EPS);
    }

    @Test
    void foodNeedsAHomeAndTenMeals() {
        ItemKey apple = t.catalog.food("apple", 4, 0);
        ItemKey pie = t.catalog.food("pie", 12, 3);
        citizen.setHomeBuilding(residence(4).position());
        assertEquals(1, factor(HappinessIds.FOOD), EPS);
        for (int i = 0; i < 9; i++) {
            citizen.hunger().history().add(apple);
        }
        citizen.hunger().history().add(pie);
        // diversity 2 / level 4, quality 1 / (4 - 2): (0.5 + 0.5) / 2
        assertEquals(0.5, factor(HappinessIds.FOOD), EPS);
    }

    @Test
    void idlingAtWorkHalvesItsFactor() {
        citizen.setJobStatus(JobStatus.STUCK);
        assertEquals(0.5, factor(HappinessIds.IDLEATJOB), EPS);
    }

    @Test
    void anUnhappyFactorWorsensAfterSevenAndFourteenDays() {
        HappinessModifier homeless =
                citizen.happiness().get(HappinessIds.HOMELESSNESS).orElseThrow();
        Building home = residence(1);
        citizen.setHomeBuilding(home.position());
        for (int day = 0; day < 7; day++) {
            homeless.dayEnd(colony, citizen);
        }
        assertEquals(7, homeless.days());
        assertEquals(1 / 3.0 * 0.75, factor(HappinessIds.HOMELESSNESS), EPS);
        for (int day = 0; day < 7; day++) {
            homeless.dayEnd(colony, citizen);
        }
        assertEquals(1 / 3.0 * 0.5, factor(HappinessIds.HOMELESSNESS), EPS);
        home.setLevel(3);
        homeless.dayEnd(colony, citizen); // happy again: the days start over
        assertEquals(0, homeless.days());
    }

    @Test
    void sleepingTonightKeepsItNeutralAndMissedNightsHurt() {
        HappinessModifier slept =
                citizen.happiness().get(HappinessIds.SLEPTTONIGHT).orElseThrow();
        assertEquals(1.0, factor(HappinessIds.SLEPTTONIGHT), EPS); // 0.5 x 2 the night it slept
        slept.dayEnd(colony, citizen);
        slept.dayEnd(colony, citizen);
        assertEquals(0.8, factor(HappinessIds.SLEPTTONIGHT), EPS);
        slept.dayEnd(colony, citizen);
        assertEquals(0.5, factor(HappinessIds.SLEPTTONIGHT), EPS);
        HappinessEvents.reachedBed(citizen);
        assertEquals(1.0, factor(HappinessIds.SLEPTTONIGHT), EPS);
    }

    @Test
    void anExpiringModifierLastsItsDaysAndAnotherOfTheSameIdReplacesIt() {
        HappinessEvents.hurt(citizen);
        assertEquals(0, factor(HappinessIds.DAMAGE), EPS);
        citizen.happiness().dayEnd(colony, citizen);
        assertEquals(1, factor(HappinessIds.DAMAGE), EPS);
        HappinessEvents.hurt(citizen);
        assertEquals(0, factor(HappinessIds.DAMAGE), EPS);
        HappinessEvents.greatFood(citizen);
        assertEquals(2, factor(HappinessIds.HADGREATFOOD), EPS);
        assertEquals(12, citizen.happiness().modifiers().size());
    }

    @Test
    void happinessIsTenTimesTheWeightedMeanOfTheNonNeutralFactors() {
        // A homeless, jobless adult alone: security 0.75 (w 4), social -1 (w 2), housing 0 (w 3), unemployment 0.5 (w
        // 2)
        double expected = 10 * (0.75 * 4 + -1 * 2 + 0 * 3 + 0.5 * 2) / (4 + 2 + 3 + 2);
        assertEquals(expected, citizen.happiness().happiness(colony, citizen), EPS);
    }

    @Test
    void theResultStaysCachedUntilAChange() {
        double before = citizen.happiness().happiness(colony, citizen);
        citizen.setHomeBuilding(residence(5).position());
        assertEquals(before, citizen.happiness().happiness(colony, citizen), EPS);
        citizen.happiness().dayEnd(colony, citizen);
        assertEquals(true, citizen.happiness().happiness(colony, citizen) != before);
    }

    @Test
    void everyFactorNeutralIsFullyHappyAndTheCapIsTen() {
        CitizenHappiness h = new CitizenHappiness();
        h.modifiers().forEach(_ -> {});
        h.add(new ExpirationModifier(HappinessIds.QUEST, 2.0, 50.0, 3));
        assertEquals(10, h.happiness(colony, citizen), EPS);
    }

    @Test
    void theColonyMeanIsFiveAndAHalfWithoutCitizens() {
        Colony empty = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(
                        2, "E", new BlockPos(500, 64, 0), Permissions.createDefault(UUID.randomUUID(), "B")));
        assertEquals(5.5, CitizenHappiness.overall(empty), EPS);
        assertEquals(citizen.happiness().happiness(colony, citizen), CitizenHappiness.overall(colony), EPS);
    }

    @Test
    void theDayEndsAtNightfallOnlyWithAPlayerInTheColony() {
        HappinessModifier homeless =
                citizen.happiness().get(HappinessIds.HOMELESSNESS).orElseThrow();
        HappinessEvents.onNightFall(colony);
        assertEquals(0, homeless.days());
        UUID player = UUID.randomUUID();
        t.players.online.put(player, new BlockPos(1, 64, 1));
        colony.claimAround(new BlockPos(0, 64, 0), 1);
        HappinessEvents.onNightFall(colony);
        assertEquals(1, homeless.days());
    }

    @Test
    void aHurtBodyHurtsItsCitizensHappinessAndAStrangersDoesNothing() {
        BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
        colony.citizens().onBodyLoaded(body, 1);
        HappinessEvents.hurt(colony, new BodyId(999));
        assertTrue(citizen.happiness().get(HappinessIds.DAMAGE).isEmpty());
        HappinessEvents.hurt(colony, body);
        assertTrue(citizen.happiness().get(HappinessIds.DAMAGE).isPresent());
    }
}
