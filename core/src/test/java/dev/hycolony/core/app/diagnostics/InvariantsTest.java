package dev.hycolony.core.app.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.citizen.vitals.AiWatch;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.StuckHandler;
import dev.hycolony.core.kernel.nav.WalkEnd;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.request.BrokenRequests;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * What a healthy colony never shows (spec 2026-09-30, § 5; debug-mod.md § 5): the walk, body, failure and request
 * invariants (1, 4, 7, 9, 10); {@link JobInvariantsTest} has the job's.
 */
class InvariantsTest {
    private static final BlockPos HUT = new BlockPos(8, 64, 0);
    /** The colony's respawn check runs when its countdown, lowered every slow tick, goes below 0. */
    private static final int SLOW_TICKS_TO_RESPAWN_CHECK = 13;

    private final DiagnosedColony c = new DiagnosedColony();

    /** The codes of the citizen's broken invariants, apart from those of the citizens the colony spawns. */
    private List<Violation.Code> citizenCodes() {
        return Invariants.check(c.colony).stream()
                .filter(v -> v.citizen().equals(OptionalInt.of(1)))
                .map(Violation::code)
                .toList();
    }

    private void respawnCheck() {
        for (int i = 0; i < SLOW_TICKS_TO_RESPAWN_CHECK; i++) {
            c.colony.citizens().onColonyTick();
        }
    }

    @Test
    void healthyWorkerBreaksNoInvariant() {
        c.working();
        c.walks.walkEnded(HUT, new Vec3(8.5, 64, 1.5), WalkEnd.NAV_ENDED, 1.0, NavStatus.ARRIVED);

        assertEquals(List.of(), Invariants.check(c.colony));
    }

    @Test
    void walkTheNavEndedFarFromItsTargetIsReported() {
        c.walks.walkEnded(HUT, new Vec3(12.5, 64, 0.5), WalkEnd.NAV_ENDED, 4.0, NavStatus.BLOCKED);

        assertEquals(
                List.of(new Violation(
                        Violation.Code.WALK_ENDED_AWAY,
                        Msg.of("hycolony.debug.violation.walkEndedAway", "8 64 0", "4.0", "BLOCKED"),
                        OptionalInt.of(1),
                        Optional.of(new BlockPos(12, 64, 0)))),
                Invariants.check(c.colony));
    }

    @Test
    void walkTheNavEndedOnAnotherFloorIsReported() {
        c.walks.walkEnded(HUT, new Vec3(8.5, 65, 0.5), WalkEnd.NAV_ENDED, 1.0, NavStatus.ARRIVED);

        assertEquals(List.of(Violation.Code.WALK_ENDED_AWAY), c.codes(), "one floor up: on the roof");
    }

    @Test
    void walkEndedAHairBelowItsFloorIsNotReported() {
        c.walks.walkEnded(HUT, new Vec3(8.5, 63.999, 0.5), WalkEnd.NAV_ENDED, 0.0, NavStatus.ARRIVED);

        assertEquals(List.of(), c.codes());
    }

    @Test
    void walkEndedInReachOfItsBlockIsNotReported() {
        c.walks.walkEnded(HUT, new Vec3(11.5, 64, 2.5), WalkEnd.IN_REACH, 3.6, NavStatus.BLOCKED);

        assertEquals(List.of(), c.codes(), "MC walkCloseToXNearY counts it arrived");
    }

    @Test
    void walkEndedInReachOnTheRoofIsReported() {
        c.walks.walkEnded(HUT, new Vec3(9.5, 69, 0.5), WalkEnd.IN_REACH, 5.0, NavStatus.ARRIVED);

        assertEquals(List.of(Violation.Code.WALK_ENDED_AWAY), c.codes());
    }

    @Test
    void walkEndedCloseOrTeleportedIsNotReported() {
        c.walks.walkEnded(HUT, new Vec3(11.5, 64, 0.5), WalkEnd.CLOSE, 3.0, NavStatus.MOVING);
        assertEquals(List.of(), c.codes(), "close within the range its caller gave");

        c.walks.walkEnded(HUT, new Vec3(8.5, 64, 0.5), WalkEnd.TELEPORTED, 0.0, NavStatus.MOVING);
        assertEquals(List.of(), c.codes());
    }

    @Test
    void stuckHandlerTeleportingOrGivingUpOnTheWalkIsReported() {
        Vec3 at = new Vec3(3.5, 64, 0.5);
        c.t.clock.tick = 10;
        c.walks.walkStarted(HUT, new Vec3(0.5, 64, 0.5));
        c.t.clock.tick = 20;
        c.walks.stuck(HUT, at, StuckHandler.Action.REPATH);
        assertEquals(List.of(), c.codes(), "a repath is routine");
        c.walks.stuck(HUT, at, StuckHandler.Action.TELEPORT);
        assertEquals(List.of(Violation.Code.STUCK_ESCALATED), c.codes());

        c.t.clock.tick = 30;
        c.walks.walkStarted(HUT.offset(4, 0, 0), at);
        assertEquals(List.of(), c.codes(), "the next walk starts clean");
        c.t.clock.tick = 140;
        c.walks.stuck(HUT.offset(4, 0, 0), at, StuckHandler.Action.GIVE_UP);
        assertEquals(List.of(Violation.Code.STUCK_ESCALATED), c.codes());
        c.walks.walkStarted(HUT, at);
        assertEquals(List.of(), c.codes(), "given up on the walk that ended as this one started");
    }

    @Test
    void repeatedAiExceptionsAreReported() {
        AiWatch watch = new AiWatch(c.colony, c.citizen);
        watch.failed();
        assertEquals(List.of(), c.codes(), "one may be bad luck");

        watch.failed();

        assertEquals(List.of(Violation.Code.AI_FAILING), c.codes());
    }

    @Test
    void repeatedJobExceptionsAreReported() {
        c.working();

        c.failures = Invariants.REPEATED_FAILURES;
        c.tickAi();

        assertEquals(List.of(Violation.Code.AI_FAILING), c.codes());
    }

    @Test
    void citizenWhoseRespawnFailedIsReported() {
        c.t.bodies.despawn(c.body);
        c.t.bodies.refuseSpawnAt.add(DiagnosedColony.HALL);
        assertEquals(List.of(), citizenCodes(), "no respawn tried yet");

        respawnCheck();

        assertEquals(List.of(Violation.Code.RESPAWN_FAILED), citizenCodes());
    }

    @Test
    void citizenWithNoLoadedSpotToRespawnIsNotReported() {
        c.t.bodies.despawn(c.body);
        c.t.world.unloaded.add(DiagnosedColony.HALL);

        respawnCheck();

        assertEquals(List.of(), citizenCodes(), "its chunk unloaded: MC waits as well");
    }

    @Test
    void citizenRespawnedAtLastIsNotReported() {
        c.t.bodies.despawn(c.body);
        c.t.bodies.refuseSpawnAt.add(DiagnosedColony.HALL);
        respawnCheck();
        c.t.bodies.refuseSpawnAt.clear();

        respawnCheck();

        assertEquals(List.of(), citizenCodes());
    }

    @Test
    void requestInProgressWithoutResolverIsReported() {
        RequestToken token = c.request();
        assertEquals(List.of(), c.codes());

        BrokenRequests.dropResolver(c.colony.requests(), token);

        assertEquals(List.of(Violation.Code.REQUEST_UNRESOLVED), c.codes());
    }

    @Test
    void requestAssignedWithoutResolverIsReported() {
        RequestToken token = c.request();

        BrokenRequests.dropResolver(c.colony.requests(), token);
        BrokenRequests.setState(c.colony.requests(), token, RequestState.ASSIGNED);

        assertEquals(List.of(Violation.Code.REQUEST_UNRESOLVED), c.codes());
    }
}
