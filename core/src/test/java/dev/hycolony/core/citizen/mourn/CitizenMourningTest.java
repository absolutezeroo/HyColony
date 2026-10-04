package dev.hycolony.core.citizen.mourn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** MC CitizenMournHandler, CitizenManager.onWakeUp and EntityAIMournCitizen. */
class CitizenMourningTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);

    private final TestContexts t = new TestContexts();

    @Test
    void mourningStartsAtTheNextWakeUpAndEndsAtTheOneAfter() {
        CitizenMourning m = new CitizenMourning();
        m.onWakeUp();
        assertFalse(m.isMourning(), "nobody to grieve for");

        m.addDeceased("Bob");
        assertFalse(m.isMourning(), "MC: from the next morning");
        m.onWakeUp();
        assertTrue(m.isMourning());
        assertEquals(Set.of("Bob"), m.deceased());

        m.onWakeUp();
        assertFalse(m.isMourning());
        assertTrue(m.deceased().isEmpty(), "MC clearDeceasedCitizen");
    }

    @Test
    void aMourningCitizenStopsWorkingForTheDay() {
        BodyId body = t.bodies.existing(1, 1, new Vec3(3, 64, 3));
        Colony c = colony();
        CitizenData d = new CitizenData(1);
        d.setJob(TestJobs.TYPE.factory().apply(d));
        c.citizens().restore(d);
        CitizenAI ai = new CitizenAI(c, d, body);
        tick(ai, 30);
        assertEquals(CitizenState.WORKING, ai.state());

        d.mourning().addDeceased("Bob");
        d.mourning().onWakeUp();
        tick(ai, 11);
        assertEquals(CitizenState.MOURN, ai.state(), "MC calculateNextState: mourning before the work");

        d.mourning().onWakeUp();
        tick(ai, 30);
        assertEquals(CitizenState.WORKING, ai.state(), "back at work the day after");
    }

    @Test
    void farFromTheTownHallAMournerWalksThere() {
        t.random = () -> (RandomGenerator) () -> 0L; // nextBoolean false: no staring
        BodyId body = t.bodies.existing(1, 1, new Vec3(150.5, 64, 150.5));
        CitizenAI ai = mourner(body);

        tick(ai, 60);

        assertTrue(
                t.bodies.moves.stream().anyMatch(m -> m.distance(Vec3.center(HALL)) < 3),
                "MC walkToBuilding, more than 225 blocks away: " + t.bodies.moves);
    }

    @Test
    void aMournerStaresAtACitizenNextToIt() {
        t.random = () -> (RandomGenerator) () -> Long.MIN_VALUE; // nextBoolean true; nextInt(200) never 0
        BodyId body = t.bodies.existing(1, 1, new Vec3(5.5, 64, 5.5));
        t.bodies.existing(1, 2, new Vec3(7.5, 64, 5.5));
        CitizenAI ai = mourner(body);

        tick(ai, 60);

        assertTrue(
                t.bodies.looks.contains(new Vec3(7.5, 64 + MournAI.EYE_HEIGHT, 5.5)), "at its eyes: " + t.bodies.looks);
        assertTrue(t.bodies.moves.isEmpty());
    }

    @Test
    void aMournerWhoseWalksGoNowhereStillDecidesAgain() {
        t.random = () -> (RandomGenerator) () -> 0L; // nextBoolean false: walks, no staring
        t.bodies.frozen = true;
        BodyId body = t.bodies.existing(1, 1, new Vec3(300.5, 64, 300.5));
        CitizenAI ai = mourner(body);

        tick(ai, 20_000);

        assertTrue(t.bodies.looks.size() > 2, "MC: stuck walks end (navigator.stop), it decides again (CLAUDE.md § 4)");
    }

    @Test
    void aStareEndsOneTimeInTwoHundredAndItDecidesAgain() {
        long[] rolls = {Long.MIN_VALUE, 0L}; // nextBoolean true (stare), then nextInt(200) 0 (end); 0 after
        int[] next = {0};
        t.random = () -> (RandomGenerator) () -> next[0] < rolls.length ? rolls[next[0]++] : 0L;
        BodyId body = t.bodies.existing(1, 1, new Vec3(5.5, 64, 5.5));
        t.bodies.existing(1, 2, new Vec3(7.5, 64, 5.5));
        CitizenAI ai = mourner(body);

        tick(ai, 80);

        assertTrue(
                t.bodies.looks.contains(new Vec3(5.5, 54, 5.5)),
                "MC stare ended, then decide: it looks down: " + t.bodies.looks);
    }

    private CitizenAI mourner(BodyId body) {
        Colony c = colony();
        CitizenData d = new CitizenData(1);
        c.citizens().restore(d);
        CitizenData other = new CitizenData(2);
        c.citizens().restore(other);
        c.citizens().onBodyLoaded(body, 1);
        t.bodies.bodies.keySet().stream()
                .filter(b -> !b.equals(body))
                .findFirst()
                .ifPresent(b -> c.citizens().onBodyLoaded(b, 2));
        d.mourning().addDeceased("Bob");
        d.mourning().onWakeUp();
        return new CitizenAI(c, d, body);
    }

    private void tick(CitizenAI ai, int ticks) {
        for (int i = 0; i < ticks; i++) {
            t.clock.tick++;
            ai.tick();
        }
    }

    private Colony colony() {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", HALL, Permissions.createDefault(UUID.randomUUID(), "A")));
        c.buildings().add(Building.create(BuildingTypes.TOWN_HALL, HALL, 0));
        c.claimAround(HALL, 12);
        return c;
    }
}
