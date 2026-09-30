package dev.hylens.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which operator paused each world's colonies through HyLens (spec 2026-09-30, § 6.1, § 6.4). */
class PausesTest {
    private static final UUID ANN = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private final Pauses pauses = new Pauses();

    @Test
    void pauseIsTheOperatorsWhoAskedIt() {
        pauses.paused("default", ANN);

        assertEquals(Set.of("default"), pauses.pausedBy(ANN));
    }

    @Test
    void resumingForgetsWhoPaused() {
        pauses.paused("default", ANN);

        pauses.resumed("default");

        assertEquals(Set.of(), pauses.pausedBy(ANN));
    }

    @Test
    void leavingOperatorsWorldsAreTheOnesTheyPaused() {
        pauses.paused("default", ANN);
        pauses.paused("nether", ANN);
        pauses.paused("other", BOB);

        assertEquals(Set.of("default", "nether"), pauses.pausedBy(ANN));
        assertEquals(Set.of(), pauses.pausedBy(UUID.randomUUID()));
    }

    @Test
    void forgettingHandsBackAPauseTheOperatorStillHolds() {
        pauses.paused("default", ANN);

        assertTrue(pauses.forget("default", ANN));
        assertEquals(Set.of(), pauses.pausedBy(ANN));
    }

    @Test
    void forgettingFailsOnceAnotherOperatorPausedSince() {
        pauses.paused("default", ANN);
        pauses.paused("default", BOB);

        assertFalse(pauses.forget("default", ANN));
        assertEquals(Set.of("default"), pauses.pausedBy(BOB));
    }
}
