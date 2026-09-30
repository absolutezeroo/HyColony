package dev.hycolony.core.app.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.kernel.port.Msg;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Only the violations that last count: a worker notices a cancelled task at its next step. */
class ViolationWatchTest {
    private final ViolationWatch watch = new ViolationWatch();

    private static Violation queueEmpty(int citizen, String step) {
        return new Violation(
                Violation.Code.QUEUE_INCOHERENT,
                Msg.of("hycolony.debug.violation.queueEmpty", step),
                OptionalInt.of(citizen),
                Optional.empty());
    }

    @Test
    void violationSeenAtEveryCheckForItsDelayIsConfirmed() {
        Violation v = queueEmpty(1, "DELIVERY");

        assertEquals(List.of(), watch.confirmed(List.of(v), 0));
        assertEquals(List.of(), watch.confirmed(List.of(v), ViolationWatch.CONFIRM_TICKS - 1));
        assertEquals(List.of(v), watch.confirmed(List.of(v), ViolationWatch.CONFIRM_TICKS));
    }

    @Test
    void violationGoneForOneCheckStartsOver() {
        Violation v = queueEmpty(1, "DELIVERY");
        watch.confirmed(List.of(v), 0);
        watch.confirmed(List.of(), 50);

        assertEquals(List.of(), watch.confirmed(List.of(v), ViolationWatch.CONFIRM_TICKS));
        assertEquals(List.of(v), watch.confirmed(List.of(v), 50 + 2L * ViolationWatch.CONFIRM_TICKS));
    }

    @Test
    void violationWhoseFiguresChangeIsTheSameViolation() {
        watch.confirmed(List.of(queueEmpty(1, "DELIVERY")), 0);

        Violation later = queueEmpty(1, "PICKUP");

        assertEquals(List.of(later), watch.confirmed(List.of(later), ViolationWatch.CONFIRM_TICKS));
    }

    @Test
    void traceIsConfirmedAtOnce() {
        Violation walk = new Violation(
                Violation.Code.WALK_ENDED_AWAY,
                Msg.of("hycolony.debug.violation.walkEndedAway", "8 64 0", "5.0", "ARRIVED"),
                OptionalInt.of(1),
                Optional.empty());

        assertEquals(List.of(walk), watch.confirmed(List.of(walk), 0), "the next walk may clear it");
    }

    @Test
    void eachCitizensViolationIsTimedApart() {
        watch.confirmed(List.of(queueEmpty(1, "DELIVERY")), 0);

        Violation second = queueEmpty(2, "DELIVERY");
        List<Violation> both = List.of(queueEmpty(1, "DELIVERY"), second);

        assertEquals(List.of(queueEmpty(1, "DELIVERY")), watch.confirmed(both, ViolationWatch.CONFIRM_TICKS));
    }
}
