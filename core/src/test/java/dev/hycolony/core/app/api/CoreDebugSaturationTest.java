package dev.hycolony.core.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.debug.DebugAccess;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The api's setSaturation (MC /mc citizens modify saturation; spec 2026-10-02 lot 2, § 3). */
class CoreDebugSaturationTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private static final Actor PLUGIN = new Actor.Plugin("Tests:HyLens");

    /** One colony with citizen 1, its managers allowed to modify citizens or not. */
    private record World(DebugAccess debug, Colony colony, CitizenData citizen, CitizenRef ref, UUID owner) {}

    private static World world(boolean managersMayModify) {
        TestContexts t = new TestContexts();
        ColonyConfig d = ColonyConfig.defaults();
        t.config = new ColonyConfig(
                d.gameplay(),
                d.claims(),
                d.permissions(),
                new ColonyConfig.Commands(true, true, false, managersMayModify),
                d.client(),
                d.hycolony(),
                d.structurize());
        ColonyManager manager = t.manager();
        UUID owner = UUID.randomUUID();
        manager.foundation().begin(owner, "Owner", HALL, 0);
        Colony colony = manager.foundation().confirm(owner, "Rivendell").orElseThrow();
        CitizenData citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        CitizenRef ref = new CitizenRef(new ColonyRef("world", colony.id()), 1);
        return new World(new CoreColonyWorld(manager, () -> true).debug(), colony, citizen, ref, owner);
    }

    @Test
    void theSaturationIsSetKeptBetweenZeroAndTheMaximumAndSaved() {
        World w = world(false);
        w.colony().clearDirty();

        assertEquals(new ActionResult.Done(), w.debug().setSaturation(PLUGIN, w.ref(), 12.5));
        assertEquals(12.5, w.citizen().saturation());
        assertTrue(w.colony().isDirty());
        w.debug().setSaturation(PLUGIN, w.ref(), 99);
        assertEquals(CitizenData.MAX_SATURATION, w.citizen().saturation());
        w.debug().setSaturation(PLUGIN, w.ref(), -3);
        assertEquals(0, w.citizen().saturation());
    }

    @Test
    void notANumberIsRefused() {
        World w = world(false);
        double before = w.citizen().saturation();

        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.value")),
                w.debug().setSaturation(PLUGIN, w.ref(), Double.NaN));
        assertEquals(before, w.citizen().saturation());
    }

    @Test
    void aManagerNeedsTheServerSettingAsMc() {
        World off = world(false);
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.config")),
                off.debug().setSaturation(new Actor.Player(off.owner()), off.ref(), 10));
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.config")),
                off.debug()
                        .setSaturation(
                                new Actor.Player(off.owner()),
                                new CitizenRef(off.ref().colony(), 42),
                                10),
                "refused before the citizen is looked up, as MC");

        World on = world(true);
        assertEquals(new ActionResult.Done(), on.debug().setSaturation(new Actor.Player(on.owner()), on.ref(), 10));
    }

    @Test
    void anUnknownCitizenIsNotFound() {
        World w = world(false);

        assertEquals(
                new ActionResult.NotFound(),
                w.debug().setSaturation(PLUGIN, new CitizenRef(w.ref().colony(), 42), 10));
    }
}
