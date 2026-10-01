package dev.hycolony.core.colony.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC BuildingTownHall.addPermissionEvent and removePermissionEvents. */
class PermissionEventsTest {
    private final UUID bob = UUID.randomUUID();

    private PermissionEvents.Event event(int x) {
        return new PermissionEvents.Event(Optional.of(bob), "Bob", Action.BREAK_BLOCKS, new BlockPos(x, 64, 0));
    }

    @Test
    void anEventAlreadyLoggedIsNotLoggedTwice() {
        PermissionEvents events = new PermissionEvents();
        assertTrue(events.add(event(1)));
        assertFalse(events.add(event(1)));
        assertEquals(1, events.entries().size());
    }

    @Test
    void theOldestEventGoesOnceAHundredAreLogged() {
        PermissionEvents events = new PermissionEvents();
        for (int x = 0; x <= PermissionEvents.MAX_EVENTS; x++) {
            events.add(event(x));
        }
        assertEquals(PermissionEvents.MAX_EVENTS, events.entries().size());
        assertEquals(1, events.entries().getFirst().pos().x());
    }

    @Test
    void removingAPlayerDropsOnlyTheirEvents() {
        PermissionEvents events = new PermissionEvents();
        events.add(event(1));
        events.add(new PermissionEvents.Event(Optional.empty(), "Ghost", Action.OPEN_CONTAINER, new BlockPos(0, 0, 0)));
        assertTrue(events.removeOf(bob));
        assertEquals("Ghost", events.entries().getFirst().name());
    }
}
