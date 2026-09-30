package dev.hylens.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.debug.Violation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Which confirmed violations an operator has not been told of yet (spec 2026-09-30, § 6.5). Fixtures are HyColony's
 * own: a stale step, a request without resolver (never a position), a walk ended away (a position), a queue.
 */
class NewAlertsTest {
    private static final UUID OPERATOR = UUID.randomUUID();
    private static final ColonyRef COLONY = new ColonyRef("default", 1);
    private static final CitizenRef ANN = new CitizenRef(COLONY, 4);

    private static Violation stale(String seconds) {
        return new Violation(
                "JOB_STEP_STALE",
                ApiText.of("hycolony.debug.violation.jobStepStale", "PICKUP", seconds),
                Optional.of(ANN),
                Optional.empty());
    }

    private static Violation unresolved(Optional<CitizenRef> requester, String token) {
        return new Violation(
                "REQUEST_UNRESOLVED",
                ApiText.of("hycolony.debug.violation.requestUnresolved", token, "ASSIGNED"),
                requester,
                Optional.empty());
    }

    private static Violation endedAway(Pos at) {
        return new Violation(
                "WALK_ENDED_AWAY",
                ApiText.of("hycolony.debug.violation.walkEndedAway", "8 64 0", "5.0", "ARRIVED"),
                Optional.of(ANN),
                Optional.of(at));
    }

    private static Violation queue(String key) {
        return new Violation(
                "QUEUE_INCOHERENT",
                ApiText.of("hycolony.debug.violation." + key, "PICKUP"),
                Optional.of(ANN),
                Optional.empty());
    }

    private final NewAlerts alerts = new NewAlerts();

    @Test
    void firstSightIsNew() {
        assertEquals(List.of(stale("300")), alerts.fresh(OPERATOR, COLONY, List.of(stale("300"))));
    }

    @Test
    void violationStillThereIsNotToldAgainThoughItsTextChanged() {
        alerts.fresh(OPERATOR, COLONY, List.of(stale("300")));

        assertEquals(List.of(), alerts.fresh(OPERATOR, COLONY, List.of(stale("302"))));
    }

    @Test
    void violationGoneThenBackIsNewAgain() {
        alerts.fresh(OPERATOR, COLONY, List.of(stale("300")));
        alerts.fresh(OPERATOR, COLONY, List.of());

        assertEquals(List.of(stale("400")), alerts.fresh(OPERATOR, COLONY, List.of(stale("400"))));
    }

    @Test
    void secondRequestWithoutResolverOfTheSameRequesterIsNew() {
        Violation a = unresolved(Optional.of(ANN), "aaaaaaaa");
        Violation b = unresolved(Optional.of(ANN), "bbbbbbbb");
        alerts.fresh(OPERATOR, COLONY, List.of(a));

        assertEquals(List.of(b), alerts.fresh(OPERATOR, COLONY, List.of(a, b)));
        assertEquals(List.of(), alerts.fresh(OPERATOR, COLONY, List.of(a, b)), "told once");
    }

    @Test
    void secondColonyRequestWithoutResolverIsNew() {
        Violation a = unresolved(Optional.empty(), "aaaaaaaa");
        Violation b = unresolved(Optional.empty(), "bbbbbbbb");
        alerts.fresh(OPERATOR, COLONY, List.of(a));

        assertEquals(List.of(b), alerts.fresh(OPERATOR, COLONY, List.of(a, b)));
    }

    @Test
    void queueTurningToAnotherIncoherenceIsNew() {
        alerts.fresh(OPERATOR, COLONY, List.of(queue("queueHeadState")));

        assertEquals(List.of(queue("queueHeadGone")), alerts.fresh(OPERATOR, COLONY, List.of(queue("queueHeadGone"))));
    }

    @Test
    void sameCodeElsewhereIsAnotherViolation() {
        alerts.fresh(OPERATOR, COLONY, List.of(endedAway(new Pos(1, 64, 1))));

        assertEquals(
                List.of(endedAway(new Pos(9, 64, 9))),
                alerts.fresh(OPERATOR, COLONY, List.of(endedAway(new Pos(9, 64, 9)))),
                "one gone, another come");
    }

    @Test
    void sameCodeForAnotherCitizenIsAnotherViolation() {
        Violation ofBob = new Violation(
                "JOB_STEP_STALE",
                ApiText.of("hycolony.debug.violation.jobStepStale", "PICKUP", "300"),
                Optional.of(new CitizenRef(COLONY, 7)),
                Optional.empty());
        alerts.fresh(OPERATOR, COLONY, List.of(stale("300")));

        assertEquals(List.of(ofBob), alerts.fresh(OPERATOR, COLONY, List.of(ofBob)), "one gone, another come");
    }

    @Test
    void operatorsAndColoniesAreToldApart() {
        alerts.fresh(OPERATOR, COLONY, List.of(stale("300")));

        assertEquals(List.of(stale("300")), alerts.fresh(UUID.randomUUID(), COLONY, List.of(stale("300"))));
        assertEquals(List.of(stale("300")), alerts.fresh(OPERATOR, new ColonyRef("default", 2), List.of(stale("300"))));
    }

    @Test
    void forgottenOperatorIsToldAfreshOthersAreNot() {
        UUID other = UUID.randomUUID();
        alerts.fresh(OPERATOR, COLONY, List.of(stale("300")));
        alerts.fresh(other, COLONY, List.of(stale("300")));

        alerts.forget(OPERATOR);

        assertEquals(List.of(stale("300")), alerts.fresh(OPERATOR, COLONY, List.of(stale("300"))));
        assertEquals(List.of(), alerts.fresh(other, COLONY, List.of(stale("300"))));
    }
}
