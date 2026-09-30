package dev.hycolony.core.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.debug.DebugAccess;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * The api's debug actions: who may act (an operator or a colony manager, as MC's officer commands; a plugin; never the
 * colony itself), and what each does (spec 2026-09-30, § 4.1, § 5).
 */
class CoreDebugActionsTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private static final Pos THERE = new Pos(20, 64, 0);
    private static final Actor PLUGIN = new Actor.Plugin("Tests:HyLens");

    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private boolean onWorldThread = true;
    private final DebugAccess debug = new CoreColonyWorld(manager, () -> onWorldThread).debug();
    private final UUID owner = UUID.randomUUID();
    private final CitizenData citizen = new CitizenData(1);
    private final Colony colony;
    private final CitizenRef ref;
    private final BodyId body;

    CoreDebugActionsTest() {
        manager.foundation().begin(owner, "Owner", HALL, 0);
        colony = manager.foundation().confirm(owner, "Rivendell").orElseThrow();
        colony.citizens().restore(citizen);
        body = t.bodies.existing(colony.id(), 1, new Vec3(0.5, 64, 0.5));
        colony.citizens().onBodyLoaded(body, 1);
        ref = new CitizenRef(new ColonyRef("world", colony.id()), 1);
    }

    /** The four actions, each asked by {@code actor} for {@code citizen}. */
    private List<Function<CitizenRef, ActionResult>> actionsOf(Actor actor) {
        return List.of(
                r -> debug.walkTo(actor, r, THERE),
                r -> debug.forceLeisure(actor, r),
                r -> debug.teleport(actor, r, THERE),
                r -> debug.respawnBody(actor, r));
    }

    @Test
    void walkToSendsTheCitizenThere() {
        assertEquals(new ActionResult.Done(), debug.walkTo(PLUGIN, ref, THERE));

        assertEquals(
                Optional.of(new BlockPos(20, 64, 0)), colony.citizens().ai(1).map(ai -> {
                    t.clock.tick++;
                    ai.tick();
                    return citizen.vitals().walks().target().orElseThrow();
                }));
    }

    @Test
    void forceLeisureStartsABreakNow() {
        assertEquals(new ActionResult.Done(), debug.forceLeisure(PLUGIN, ref));

        assertEquals(CitizenData.LEISURE_TICKS, citizen.leisureTime());
    }

    @Test
    void teleportPutsTheBodyThere() {
        assertEquals(new ActionResult.Done(), debug.teleport(PLUGIN, ref, THERE));

        assertEquals(
                Vec3.center(new BlockPos(20, 64, 0)), t.bodies.position(body).orElseThrow());
    }

    @Test
    void respawnBodyGivesItANewOne() {
        CitizenAI before = colony.citizens().ai(1).orElseThrow();

        assertEquals(new ActionResult.Done(), debug.respawnBody(PLUGIN, ref));

        BodyId now = colony.citizens().bodyOf(1).orElseThrow();
        assertNotEquals(body, now);
        assertFalse(t.bodies.isAlive(body), "the old one is gone");
        assertTrue(t.bodies.isAlive(now));
        assertNotSame(before, colony.citizens().ai(1).orElseThrow(), "a new body, a new AI");
    }

    @Test
    void respawnBodyWithNowhereToAppearKeepsTheOldOne() {
        CitizenAI before = colony.citizens().ai(1).orElseThrow();
        t.bodies.refuseSpawn = true;

        assertEquals(new ActionResult.Unavailable(), debug.respawnBody(PLUGIN, ref));

        assertEquals(Optional.of(body), colony.citizens().bodyOf(1));
        assertTrue(t.bodies.isAlive(body));
        assertSame(before, colony.citizens().ai(1).orElseThrow());
        assertEquals(OptionalLong.empty(), colony.citizens().failedRespawns().since(1), "no failure: it has one");
    }

    @Test
    void walkAndTeleportNeedALivingBody() {
        t.bodies.despawn(body);

        assertEquals(new ActionResult.Unavailable(), debug.walkTo(PLUGIN, ref, THERE), "dead, still bound");
        assertEquals(new ActionResult.Unavailable(), debug.teleport(PLUGIN, ref, THERE));
        colony.citizens().onBodyUnloaded(body);
        assertEquals(new ActionResult.Unavailable(), debug.walkTo(PLUGIN, ref, THERE), "unloaded");
        assertEquals(new ActionResult.Unavailable(), debug.teleport(PLUGIN, ref, THERE));
    }

    @Test
    void actionsOnAnUnknownCitizenAreNotFound() {
        CitizenRef unknown = new CitizenRef(ref.colony(), 9);
        CitizenRef elsewhere = new CitizenRef(new ColonyRef("elsewhere", colony.id()), 1);

        for (Function<CitizenRef, ActionResult> action : actionsOf(PLUGIN)) {
            assertEquals(new ActionResult.NotFound(), action.apply(unknown));
            assertEquals(new ActionResult.NotFound(), action.apply(elsewhere));
        }
    }

    @Test
    void aStrangerIsRefusedBeforeTheCitizenIsLookedFor() {
        CitizenRef unknown = new CitizenRef(ref.colony(), 9);

        for (Function<CitizenRef, ActionResult> action : actionsOf(new Actor.Player(UUID.randomUUID()))) {
            assertEquals(
                    new ActionResult.Refused(ApiText.of("hycolony.permission.denied", "Rivendell")),
                    action.apply(unknown),
                    "MC IMCColonyOfficerCommand: the rights first");
        }
    }

    @Test
    void theColonyItselfIsRefused() {
        for (Function<CitizenRef, ActionResult> action : actionsOf(new Actor.Colony())) {
            assertEquals(
                    new ActionResult.Refused(ApiText.of("hycolony.debug.refused.colony")),
                    action.apply(ref),
                    "the colony is a cause, never one who asks");
        }
    }

    @Test
    void aPlayerNeitherOperatorNorManagerIsRefused() {
        Actor stranger = new Actor.Player(UUID.randomUUID());

        for (Function<CitizenRef, ActionResult> action : actionsOf(stranger)) {
            assertEquals(
                    new ActionResult.Refused(ApiText.of("hycolony.permission.denied", "Rivendell")), action.apply(ref));
        }
        assertEquals(0, citizen.leisureTime(), "nothing was done");
    }

    @Test
    void theColonysManagerAndAnOperatorMayAct() {
        UUID op = UUID.randomUUID();
        t.players.operators.add(op);

        assertEquals(new ActionResult.Done(), debug.forceLeisure(new Actor.Player(owner), ref));
        assertEquals(new ActionResult.Done(), debug.forceLeisure(new Actor.Player(op), ref));
    }

    @Test
    void actionsOffTheWorldThreadThrow() {
        onWorldThread = false;

        for (Function<CitizenRef, ActionResult> action : actionsOf(PLUGIN)) {
            assertThrows(IllegalStateException.class, () -> action.apply(ref));
        }
    }
}
