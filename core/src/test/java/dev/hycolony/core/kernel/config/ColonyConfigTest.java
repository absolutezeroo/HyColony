package dev.hycolony.core.kernel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColonyConfigTest {
    @Test
    void autosaveIntervalIsClampedToAtLeastOneMinute() {
        assertEquals(1, new ColonyConfig.HyColony(0, false, true).autosaveIntervalMinutes());
    }

    @Test
    void defaultsAreMineColoniesDefaults() {
        ColonyConfig c = ColonyConfig.defaults();
        assertEquals(4, c.gameplay().initialCitizenAmount());
        assertEquals(250, c.gameplay().maxCitizenPerColony());
        assertFalse(c.gameplay().workersAlwaysWorkInRain()); // MC workersalwaysworkinrain
        assertEquals(20, c.claims().maxColonySize());
        assertEquals(8, c.claims().minColonyDistance());
        assertEquals(4, c.claims().initialColonySize());
        assertEquals(30000, c.claims().maxDistanceFromWorldSpawn());
        assertEquals(0, c.claims().minDistanceFromWorldSpawn());
        assertTrue(c.permissions().enableColonyProtection());
        assertEquals(Explosions.DAMAGE_ENTITIES, c.permissions().turnOffExplosionsInColonies());
        assertEquals(2, c.permissions().permissionEventBypassMinPermLevel());
        assertTrue(c.commands().canPlayerUseShowColonyInfoCommand());
        assertTrue(c.commands().canPlayerUseAddOfficerCommand());
        assertFalse(c.commands().canPlayerUseDeleteColonyCommand());
        assertEquals(50, c.client().buildGoggleRange());
        assertEquals(5, c.hycolony().autosaveIntervalMinutes());
        assertFalse(c.hycolony().builderInfiniteResources());
        assertTrue(c.hycolony().creativeOperatorFreeBuilds());
    }

    @Test
    void claimsAreClampedToMineColoniesBounds() {
        ColonyConfig.Claims low = new ColonyConfig.Claims(0, 0, 0, 0, -5);
        assertEquals(1, low.maxColonySize());
        assertEquals(1, low.minColonyDistance());
        assertEquals(1, low.initialColonySize());
        assertEquals(1000, low.maxDistanceFromWorldSpawn());
        assertEquals(0, low.minDistanceFromWorldSpawn());
        ColonyConfig.Claims high = new ColonyConfig.Claims(999, 999, 99, Integer.MAX_VALUE, 5000);
        assertEquals(250, high.maxColonySize());
        assertEquals(200, high.minColonyDistance());
        assertEquals(15, high.initialColonySize());
        assertEquals(Integer.MAX_VALUE, high.maxDistanceFromWorldSpawn());
        assertEquals(1000, high.minDistanceFromWorldSpawn());
    }

    @Test
    void gameplayGogglesAndBypassLevelAreClampedToMineColoniesBounds() {
        assertEquals(1, new ColonyConfig.Gameplay(0, 0, false).initialCitizenAmount());
        assertEquals(25, new ColonyConfig.Gameplay(0, 0, false).maxCitizenPerColony());
        assertEquals(500, new ColonyConfig.Gameplay(99, 9999, false).maxCitizenPerColony());
        assertEquals(1, new ColonyConfig.Client(0).buildGoggleRange());
        assertEquals(250, new ColonyConfig.Client(999).buildGoggleRange());
        assertEquals(
                4,
                new ColonyConfig.Permissions(true, Explosions.DAMAGE_NOTHING, 9).permissionEventBypassMinPermLevel());
        assertEquals(
                0,
                new ColonyConfig.Permissions(true, Explosions.DAMAGE_NOTHING, -1).permissionEventBypassMinPermLevel());
    }

    @Test
    void unknownExplosionModeFallsBackToMineColoniesDefault() {
        assertEquals(Explosions.DAMAGE_NOTHING, Explosions.parse("DAMAGE_NOTHING"));
        assertEquals(Explosions.DAMAGE_PLAYERS, Explosions.parse("damage_players"));
        assertEquals(Explosions.DAMAGE_ENTITIES, Explosions.parse("boom"));
        assertEquals(Explosions.DAMAGE_ENTITIES, Explosions.parse(null));
    }
}
