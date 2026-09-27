package dev.hycolony.core.colony.permission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DenialNoticesTest {
    private final UUID bob = UUID.randomUUID();
    private final DenialNotices notices = new DenialNotices();

    @Test
    void theFirstRefusalIsTold() {
        assertTrue(notices.shouldTell(bob, 1000));
    }

    @Test
    void refusalsWithinTenSecondsAreSilent() {
        notices.shouldTell(bob, 1000);
        assertFalse(notices.shouldTell(bob, 1000 + DenialNotices.INTERVAL_TICKS));
    }

    @Test
    void aRefusalMoreThanTenSecondsLaterIsToldAgain() {
        notices.shouldTell(bob, 1000);
        assertTrue(notices.shouldTell(bob, 1001 + DenialNotices.INTERVAL_TICKS));
        assertFalse(notices.shouldTell(bob, 1002 + DenialNotices.INTERVAL_TICKS));
    }

    @Test
    void eachPlayerHasHisOwnDelay() {
        notices.shouldTell(bob, 1000);
        assertTrue(notices.shouldTell(UUID.randomUUID(), 1001));
    }
}
