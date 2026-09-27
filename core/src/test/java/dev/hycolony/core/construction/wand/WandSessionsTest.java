package dev.hycolony.core.construction.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WandSessionsTest {
    private final WandSessions sessions = new WandSessions();
    private final UUID alice = UUID.randomUUID();

    @Test
    void unknownPlayerGetsAnEmptySession() {
        assertEquals(WandSession.empty(), sessions.get(alice));
    }

    @Test
    void clearForgetsTheSession() {
        sessions.put(alice, WandSession.empty().withStyle("classic"));

        sessions.clear(alice);

        assertEquals(WandSession.empty(), sessions.get(alice));
        assertFalse(sessions.get(alice).hasBuilding());
    }
}
