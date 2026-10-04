package dev.hycolony.core.citizen.hurt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.StuckHandler;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC EntityCitizen.hurt: who may hurt a citizen, how much a hit takes, and the attacker's memory. */
class CitizenHurtTest {
    private static final double EPS = 1e-9;
    private static final UUID OWNER = UUID.randomUUID();

    private final TestContexts t = new TestContexts();
    private final CitizenData citizen = new CitizenData(1);
    private final BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
    private final Colony colony = colony();

    private Colony colony() {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(OWNER, "A")));
        c.citizens().restore(citizen);
        c.citizens().onBodyLoaded(body, citizen.id());
        return c;
    }

    @Test
    void aHitTakesAtMostAFifthOfMaxHealth() {
        assertEquals(20, CitizenHurt.allowed(colony, body, 60, new HurtSource.Creature()), EPS);
        assertEquals(20, CitizenHurt.allowed(colony, body, 60, new HurtSource.None()), EPS);
        assertEquals(7, CitizenHurt.allowed(colony, body, 7, new HurtSource.Creature()), EPS);
    }

    @Test
    void theCapFollowsTheBodysOwnMaximum() {
        t.bodies.bodies.get(body).maxHealth = 40;
        assertEquals(8, CitizenHurt.allowed(colony, body, 60, new HurtSource.None()), EPS);
    }

    @Test
    void aPlayerWithoutHurtRightDealsAtMostFive() {
        HurtSource stranger = new HurtSource.Player(UUID.randomUUID());
        assertEquals(5, CitizenHurt.allowed(colony, body, 5, stranger), EPS, "MC: damage <= 1, times 100 / 20");
        assertEquals(0, CitizenHurt.allowed(colony, body, 5.5, stranger), EPS);
        assertEquals(0, CitizenHurt.allowed(colony, body, 6, new HurtSource.Player(OWNER)), EPS, "MC: hostile only");

        UUID enemy = UUID.randomUUID();
        colony.permissions().addPlayer(enemy, "E", Permissions.HOSTILE);
        assertEquals(20, CitizenHurt.allowed(colony, body, 60, new HurtSource.Player(enemy)), EPS);
    }

    @Test
    void aCitizenOfTheSameColonyNeverHurtsIt() {
        assertEquals(0, CitizenHurt.allowed(colony, body, 3, new HurtSource.Citizen(1)), EPS);
        assertEquals(3, CitizenHurt.allowed(colony, body, 3, new HurtSource.Citizen(2)), EPS);
    }

    @Test
    void aCitizenInAWallIsMovedOutWhereItStands() {
        CitizenHurt.outOfWall(colony, body);
        assertEquals(
                List.of(new Vec3(0, 64, 0)), t.bodies.teleports, "MC TeleportHelper.teleportCitizen(blockPosition)");
    }

    @Test
    void aFullStuckCostsAFifthOfMaxHealthAndARepathNothing() {
        CitizenWalkReports walks = new CitizenWalkReports(colony, citizen);
        walks.stuck(new BlockPos(5, 64, 5), new Vec3(1, 64, 1), StuckHandler.Action.REPATH);
        assertEquals(100, t.bodies.bodies.get(body).health, EPS);

        walks.stuck(new BlockPos(5, 64, 5), new Vec3(1, 64, 1), StuckHandler.Action.TELEPORT);
        assertEquals(80, t.bodies.bodies.get(body).health, EPS, "MC withTakeDamageOnStuck(0.2f)");
    }

    @Test
    void anAttackerIsRememberedForThreeHundredTicks() {
        HurtMemory memory = citizen.vitals().hurtMemory();
        assertFalse(memory.recentlyAttacked(0));
        memory.attacked(1000);
        assertTrue(memory.recentlyAttacked(1000 + 299));
        assertFalse(memory.recentlyAttacked(1000 + 300));
    }
}
