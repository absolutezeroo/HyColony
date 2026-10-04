package dev.hycolony.core.citizen.hurt;

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
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC EntityCitizen.performMoveAway and EntityAICitizenAvoidEntity. */
class FleeAITest {
    private static final double EPS = 1e-6;
    private static final Vec3 HERE = new Vec3(10.5, 64, 0.5);

    private final TestContexts t = new TestContexts();
    private final BodyId body = t.bodies.existing(1, 1, HERE);
    private final CitizenData data = new CitizenData(1);
    private final CitizenAI ai = new CitizenAI(colony(), data, body);

    private Colony colony() {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        c.buildings().add(Building.create(BuildingTypes.TOWN_HALL, new BlockPos(0, 64, 0), 0));
        return c;
    }

    @Test
    void aHitCitizenRunsFifteenBlocksFromItsAttackerAndFlees() {
        ai.hit(new Vec3(5.5, 64, 0.5), false);

        assertEquals(new Vec3(20.5, 64, 0.5), t.bodies.moves.getLast(), "MC walkAwayFrom(attacker, 15)");
        tick(1);
        assertEquals(CitizenState.FLEE, ai.state());
    }

    @Test
    void hurtByNoEntityItStepsFiveBlocksAwayWithoutFleeing() {
        ai.hit(null, false);

        Vec3 to = t.bodies.moves.getLast();
        assertEquals(5, Math.hypot(to.x() - HERE.x(), to.z() - HERE.z()), EPS);
        tick(1);
        assertEquals(CitizenState.IDLE, ai.state());
    }

    @Test
    void aRunThatCannotMoveStillEndsAndTheFlightToo() {
        t.bodies.frozen = true;
        ai.hit(new Vec3(5.5, 64, 0.5), false);

        tick(2000);

        assertEquals(CitizenState.IDLE, ai.state(), "MC: the stuck handler stops a run; CLAUDE.md § 4");
    }

    @Test
    void mourningTakesOverAFlightAsMcDecideAiTask() {
        ai.hit(new Vec3(5.5, 64, 0.5), false);
        tick(1);
        data.mourning().addDeceased("Bob");
        data.mourning().onWakeUp();

        tick(11);

        assertEquals(CitizenState.MOURN, ai.state(), "MC: another state than lastState ends FLEE");
    }

    @Test
    void aHitWakesASleeper() {
        data.setAsleep(true);

        ai.hit(null, true);

        assertFalse(data.asleep(), "Minecraft LivingEntity.hurt: stopSleeping");
    }

    @Test
    void aFallMakesNoCitizenRun() {
        int moves = t.bodies.moves.size();
        ai.hit(null, true);
        assertEquals(moves, t.bodies.moves.size(), "MC: !damageSource.is(DamageTypes.FALL)");
    }

    @Test
    void aFleeingCitizenAvoidsAMonsterNearAndGoesBackOnceSafe() {
        ai.hit(new Vec3(5.5, 64, 0.5), false);
        tick(1);
        t.bodies.bodies.get(body).position = HERE; // its run is over
        t.bodies.bodies.get(body).status = NavStatus.ARRIVED;
        t.bodies.threats.add(new Vec3(13.5, 64, 0.5));
        int moves = t.bodies.moves.size();
        tick(FleeAI.RATE_TICKS);
        assertTrue(t.bodies.moves.size() > moves, "MC isEntityClose: it runs again");

        t.bodies.threats.clear();
        t.bodies.bodies.get(body).status = NavStatus.ARRIVED;
        tick(FleeAI.RATE_TICKS * (FleeAI.CHECKS_BEFORE_SAFE + 3));

        assertEquals(CitizenState.IDLE, ai.state());
        assertTrue(t.bodies.moves.contains(HERE), "MC reset: back where it started fleeing");
    }

    private void tick(int ticks) {
        for (int i = 0; i < ticks; i++) {
            t.clock.tick++;
            ai.tick();
        }
    }
}
