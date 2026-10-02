package dev.hycolony.core.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.debug.DebugAccess;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The api's spawnCitizen (MC /mc citizens spawnNew, an operator command; spec 2026-10-02 lot 2, § 3). */
class CoreDebugSpawnTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private static final Actor PLUGIN = new Actor.Plugin("Tests:HyLens");

    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final DebugAccess debug = new CoreColonyWorld(manager, () -> true).debug();
    private final UUID owner = UUID.randomUUID();
    private final Colony colony;
    private final ColonyRef ref;

    CoreDebugSpawnTest() {
        manager.foundation().begin(owner, "Owner", HALL, 0);
        colony = manager.foundation().confirm(owner, "Rivendell").orElseThrow();
        ref = new ColonyRef("world", colony.id());
    }

    @Test
    void anOperatorSpawnsACitizenEvenWithNewCitizensOff() {
        UUID op = UUID.randomUUID();
        t.players.operators.add(op);
        colony.settings().setMoveIn(false);
        int before = colony.citizens().all().size();

        assertEquals(new ActionResult.Done(), debug.spawnCitizen(new Actor.Player(op), ref));
        assertEquals(before + 1, colony.citizens().all().size());
    }

    @Test
    void aManagerWhoIsNotAnOperatorMayNotSpawnAsMcsOperatorCommand() {
        int before = colony.citizens().all().size();

        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.permission.denied", "Rivendell")),
                debug.spawnCitizen(new Actor.Player(owner), ref));
        assertEquals(before, colony.citizens().all().size());
    }

    @Test
    void aPluginMaySpawnAndTheColonyMayNot() {
        assertEquals(new ActionResult.Done(), debug.spawnCitizen(PLUGIN, ref));
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.colony")),
                debug.spawnCitizen(new Actor.Colony(), ref));
    }

    @Test
    void anUnloadedTownHallIsUnavailable() {
        t.world.unloaded.add(HALL);

        assertEquals(new ActionResult.Unavailable(), debug.spawnCitizen(PLUGIN, ref));
    }

    @Test
    void anUnknownColonyIsNotFound() {
        assertEquals(new ActionResult.NotFound(), debug.spawnCitizen(PLUGIN, new ColonyRef("world", 99)));
    }
}
