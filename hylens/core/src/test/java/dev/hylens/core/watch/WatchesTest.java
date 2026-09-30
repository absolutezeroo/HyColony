package dev.hylens.core.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Subscription;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which citizen each operator watches, and the tracking held for it (spec 2026-09-30, § 6.1). */
class WatchesTest {
    private static final UUID OPERATOR = UUID.randomUUID();
    private static final CitizenRef ANN = new CitizenRef(new ColonyRef("default", 1), 1);
    private static final CitizenRef BOB = new CitizenRef(new ColonyRef("default", 1), 2);

    /** A tracking that remembers being closed. */
    private static final class Tracking implements Subscription {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    private final Watches watches = new Watches();

    @Test
    void startedWatchIsKnown() {
        watches.start(OPERATOR, ANN, new Tracking());

        assertEquals(Optional.of(ANN), watches.watched(OPERATOR));
    }

    @Test
    void watchingAnotherCitizenClosesTheFirstTracking() {
        Tracking first = new Tracking();
        watches.start(OPERATOR, ANN, first);
        Tracking second = new Tracking();

        watches.start(OPERATOR, BOB, second);

        assertTrue(first.closed);
        assertFalse(second.closed);
        assertEquals(Optional.of(BOB), watches.watched(OPERATOR));
    }

    @Test
    void stoppingClosesTheTrackingAndTellsWhoWasWatched() {
        Tracking tracking = new Tracking();
        watches.start(OPERATOR, ANN, tracking);

        assertEquals(Optional.of(ANN), watches.stop(OPERATOR));
        assertTrue(tracking.closed);
        assertEquals(Optional.empty(), watches.watched(OPERATOR));
    }

    @Test
    void stoppingWithoutAWatchDoesNothing() {
        assertEquals(Optional.empty(), watches.stop(OPERATOR));
    }

    @Test
    void operatorsWatchApart() {
        UUID other = UUID.randomUUID();
        Tracking mine = new Tracking();
        watches.start(OPERATOR, ANN, mine);
        watches.start(other, ANN, new Tracking());

        watches.stop(other);

        assertFalse(mine.closed);
        assertEquals(Optional.of(ANN), watches.watched(OPERATOR));
    }

    @Test
    void stopFromAnotherThreadIsSeenOnceJoined() throws InterruptedException {
        Tracking tracking = new Tracking();
        watches.start(OPERATOR, ANN, tracking);

        Thread network = new Thread(() -> watches.stop(OPERATOR));
        network.start();
        network.join();

        assertTrue(tracking.closed);
        assertEquals(Optional.empty(), watches.watched(OPERATOR));
    }
}
