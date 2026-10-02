package dev.hycolony.core.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.debug.DebugAccess;
import dev.hycolony.api.debug.SaturationChange;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyState;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The api's modifySaturation (MC /mc citizens modify saturation; spec 2026-10-02 lot 2, § 3). */
class CoreDebugSaturationTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private static final Actor PLUGIN = new Actor.Plugin("Tests:HyLens");
    private static final ApiText OUT_OF_RANGE = ApiText.of("hycolony.debug.refused.value", "0", "60");

    /** One colony with citizen 1, its managers allowed to modify citizens or not, its food modifier. */
    private record World(
            TestContexts t, DebugAccess debug, Colony colony, CitizenData citizen, CitizenRef ref, UUID owner) {}

    private static World world(boolean managersMayModify, double foodModifier) {
        TestContexts t = new TestContexts();
        ColonyConfig d = ColonyConfig.defaults();
        ColonyConfig.Gameplay g = d.gameplay();
        t.config = new ColonyConfig(
                new ColonyConfig.Gameplay(
                        g.initialCitizenAmount(), g.maxCitizenPerColony(), g.workersAlwaysWorkInRain(), foodModifier),
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
        citizen.setSaturation(30);
        colony.citizens().restore(citizen);
        CitizenRef ref = new CitizenRef(new ColonyRef("world", colony.id()), 1);
        return new World(t, new CoreColonyWorld(manager, () -> true).debug(), colony, citizen, ref, owner);
    }

    private static World world() {
        return world(false, 1.0);
    }

    @Test
    void setPutsTheValueAndSaves() {
        World w = world();
        w.colony().clearDirty();

        assertEquals(new ActionResult.Done(), w.debug().modifySaturation(PLUGIN, w.ref(), SaturationChange.SET, 12.5));
        assertEquals(12.5, w.citizen().saturation());
        assertTrue(w.colony().isDirty());
    }

    @Test
    void increaseAddsUpToTheMaximumAsMcsIncreaseSaturation() {
        World w = world();

        w.debug().modifySaturation(PLUGIN, w.ref(), SaturationChange.INCREASE, 1);
        assertEquals(31, w.citizen().saturation());
        w.debug().modifySaturation(PLUGIN, w.ref(), SaturationChange.INCREASE, 60);
        assertEquals(CitizenData.MAX_SATURATION, w.citizen().saturation());
    }

    /** Makes the colony ACTIVE, as its owner stands in it (ColonyState). */
    private static void activate(World w) {
        w.t().players.online.put(w.owner(), HALL);
        for (int i = 0; i < 200; i++) {
            w.t().clock.tick++;
            w.colony().tick();
        }
        assertEquals(ColonyState.ACTIVE, w.colony().state());
    }

    @Test
    void zeroIsAcceptedAsMcsInclusiveBound() {
        World w = world();

        assertEquals(new ActionResult.Done(), w.debug().modifySaturation(PLUGIN, w.ref(), SaturationChange.SET, 0));
        assertEquals(0, w.citizen().saturation());
    }

    @Test
    void decreaseTakesTimesTheFoodModifierAndEndsJustAteAsMcsDecreaseSaturation() {
        World w = world(false, 2.0);
        activate(w);
        w.citizen().setSaturation(30);
        w.citizen().hunger().setJustAte(true);

        w.debug().modifySaturation(PLUGIN, w.ref(), SaturationChange.DECREASE, 1);

        assertEquals(28, w.citizen().saturation(), "1 times the food modifier 2");
        assertFalse(w.citizen().hunger().justAte(), "so it may eat again");
        w.debug().modifySaturation(PLUGIN, w.ref(), SaturationChange.DECREASE, 60);
        assertEquals(0, w.citizen().saturation());
    }

    @Test
    void decreaseDoesNothingToAnInactiveColonyAsMc() {
        World w = world();
        w.citizen().hunger().setJustAte(true);

        assertEquals(
                new ActionResult.Done(), w.debug().modifySaturation(PLUGIN, w.ref(), SaturationChange.DECREASE, 5));

        assertEquals(ColonyState.INACTIVE, w.colony().state());
        assertEquals(30, w.citizen().saturation());
        assertTrue(w.citizen().hunger().justAte());
    }

    @Test
    void aValueOutsideZeroToTheMaximumIsRefusedAsMcsArgumentBounds() {
        World w = world();

        for (double bad : new double[] {-3, 60.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(
                    new ActionResult.Refused(OUT_OF_RANGE),
                    w.debug().modifySaturation(PLUGIN, w.ref(), SaturationChange.SET, bad),
                    "value " + bad);
        }
        assertEquals(30, w.citizen().saturation(), "nothing was changed");
    }

    @Test
    void aManagerNeedsTheServerSettingAsMc() {
        World off = world(); // its owner not in creative: the setting is checked first, as MC
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.config")),
                off.debug().modifySaturation(new Actor.Player(off.owner()), off.ref(), SaturationChange.SET, 10));
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.config")),
                off.debug()
                        .modifySaturation(
                                new Actor.Player(off.owner()),
                                new CitizenRef(off.ref().colony(), 42),
                                SaturationChange.SET,
                                10),
                "refused before the citizen is looked up, as MC");

        World on = world(true, 1.0);
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.creative")),
                on.debug().modifySaturation(new Actor.Player(on.owner()), on.ref(), SaturationChange.SET, 10),
                "allowed by the setting, a manager must still be in creative mode, as MC");
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.creative")),
                on.debug()
                        .modifySaturation(
                                new Actor.Player(on.owner()),
                                new CitizenRef(on.ref().colony(), 42),
                                SaturationChange.SET,
                                10),
                "refused before the citizen is looked up, as MC");
        on.t().players.creative.add(on.owner());
        assertEquals(
                new ActionResult.Done(),
                on.debug().modifySaturation(new Actor.Player(on.owner()), on.ref(), SaturationChange.SET, 10));
    }

    @Test
    void aPlayerMustBeInCreativeEvenAnOperatorAsMc() {
        World w = world();
        UUID op = UUID.randomUUID();
        w.t().players.operators.add(op);

        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.creative")),
                w.debug().modifySaturation(new Actor.Player(op), w.ref(), SaturationChange.SET, 10));
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.creative")),
                w.debug()
                        .modifySaturation(
                                new Actor.Player(op),
                                new CitizenRef(new ColonyRef("world", 99), 1),
                                SaturationChange.SET,
                                10),
                "an operator passes MC's officer check without its colony, then fails creative mode");
        w.t().players.creative.add(op);
        assertEquals(
                new ActionResult.Done(),
                w.debug().modifySaturation(new Actor.Player(op), w.ref(), SaturationChange.SET, 10));
    }

    @Test
    void anUnknownCitizenIsNotFound() {
        World w = world();

        assertEquals(
                new ActionResult.NotFound(),
                w.debug().modifySaturation(PLUGIN, new CitizenRef(w.ref().colony(), 42), SaturationChange.SET, 10));
    }
}
