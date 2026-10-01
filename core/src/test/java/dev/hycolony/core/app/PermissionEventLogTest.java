package dev.hycolony.core.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.BlockUse;
import dev.hycolony.core.colony.permission.PermissionEvents;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC ColonyPermissionEventHandler.cancelEvent: every refused action is logged for the Permissions tab. */
class PermissionEventLogTest {
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final BlockPos inside = new BlockPos(5, 64, 5);

    @Test
    void aRefusedActionIsLoggedOnceWithThePlayersName() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = manager.foundation().confirm(alice, "A").orElseThrow();
        t.players.names.put(bob, "Bob");
        t.players.online.put(bob, inside);
        c.clearDirty();

        assertTrue(manager.protection().refuses(bob, inside, Action.BREAK_BLOCKS));
        assertTrue(manager.protection().refuses(bob, inside, Action.BREAK_BLOCKS));

        assertEquals(
                List.of(new PermissionEvents.Event(Optional.of(bob), "Bob", Action.BREAK_BLOCKS, inside)),
                c.permissions().events().entries());
        assertTrue(c.isDirty());
    }

    @Test
    void aRefusedBlockUseIsLoggedToo() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = manager.foundation().confirm(alice, "A").orElseThrow();
        BlockUse chest = new BlockUse(false, true, false, BlockUse.Held.NOTHING);
        assertTrue(manager.protection().refuses(bob, inside, chest));
        // MC's first check: a neutral player may not even right-click a block.
        assertEquals(
                Action.RIGHTCLICK_BLOCK,
                c.permissions().events().entries().getFirst().action());
    }
}
