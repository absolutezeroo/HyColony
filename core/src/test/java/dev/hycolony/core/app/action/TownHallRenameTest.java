package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC TownHallRenameMessage: MANAGE_HUTS, a name over 25 characters is cut to its first 24. */
class TownHallRenameTest {
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID();

    private Colony found() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        return manager.foundation().confirm(alice, "A").orElseThrow();
    }

    @Test
    void renameTruncatesANameOverTwentyFiveCharactersToTwentyFour() {
        Colony c = found();
        assertTrue(manager.administration().rename(alice, c.id(), "abcdefghijklmnopqrstuvwxyz"));
        assertEquals("abcdefghijklmnopqrstuvwx", c.name());
    }

    @Test
    void renameKeepsANameOfTwentyFiveCharacters() {
        Colony c = found();
        assertTrue(manager.administration().rename(alice, c.id(), "abcdefghijklmnopqrstuvwxy"));
        assertEquals("abcdefghijklmnopqrstuvwxy", c.name());
    }

    @Test
    void renameRefusesABlankName() {
        Colony c = found();
        assertFalse(manager.administration().rename(alice, c.id(), "   "));
        assertEquals("A", c.name());
    }

    @Test
    void renameNeedsManageHuts() {
        Colony c = found();
        assertFalse(manager.administration().rename(UUID.randomUUID(), c.id(), "Hacked"));
        assertEquals("A", c.name());
    }
}
