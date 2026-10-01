package dev.hycolony.core.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.EventLog;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.PermissionEvents;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.testing.TestContexts;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Schema 7's town hall state survives a save and a load; the refused actions do not, as MC. */
class TownHallSaveTest {
    @TempDir
    Path dir;

    private final UUID alice = UUID.randomUUID();

    private ColonyManager manager() {
        ColonyManager m = new TestContexts().manager();
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp4());
        return m;
    }

    @Test
    void townHallStateRoundTrips() {
        ColonyManager m = manager();
        m.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.foundation().confirm(alice, "A").orElseThrow();
        c.settings().setStyle("desert");
        c.settings().setMoveIn(false);
        c.log().addAt(new BlockPos(5, 64, 7), "buildingBuilt", 3, "hycolony:residence", "1");
        c.permissions()
                .events()
                .add(new PermissionEvents.Event(
                        Optional.of(UUID.randomUUID()), "Bob", Action.BREAK_BLOCKS, new BlockPos(1, 2, 3)));
        c.markDirty();
        m.persistence().saveAll();

        ColonyManager reloaded = manager();
        reloaded.persistence().loadAll();
        Colony r = reloaded.byId(c.id()).orElseThrow();

        assertEquals("desert", r.settings().style());
        assertFalse(r.settings().moveIn());
        EventLog.Entry built = r.log().entries().getLast();
        assertEquals(Optional.of(new BlockPos(5, 64, 7)), built.pos());
        assertTrue(r.permissions().events().entries().isEmpty(), "MC never saves the refused actions");
    }
}
