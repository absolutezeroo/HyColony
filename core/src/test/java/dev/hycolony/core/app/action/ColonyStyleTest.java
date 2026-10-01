package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC IColony.getStructurePack: the colony's style, set at its founding and by ColonyStructureStyleMessage. */
class ColonyStyleTest {
    private final TestContexts t = withBlueprints();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID();
    private final BlockPos hall = new BlockPos(0, 64, 0);

    private static TestContexts withBlueprints() {
        TestContexts t = new TestContexts();
        t.blueprints = new FakeBlueprints();
        return t;
    }

    private Colony found(String style) {
        manager.foundation().begin(alice, "Alice", hall, 0, style);
        return manager.foundation().confirm(alice, "A").orElseThrow();
    }

    @Test
    void foundedColonyTakesItsTownHallStyle() {
        assertEquals(
                FakeBlueprints.STYLE, found(FakeBlueprints.STYLE).settings().style());
    }

    @Test
    void newHutDefaultsToTheColonyStyle() {
        Colony c = found(FakeBlueprints.STYLE);
        BlockPos hut = new BlockPos(10, 64, 0);
        manager.huts().place(c, "hycolony:builder", hut, 0, alice);
        assertEquals(FakeBlueprints.STYLE, c.buildings().at(hut).orElseThrow().style());
    }

    @Test
    void setStyleChangesTheColonyStyleForAManager() {
        Colony c = found("");
        assertTrue(manager.administration().setStyle(alice, c.id(), FakeBlueprints.STYLE));
        assertEquals(FakeBlueprints.STYLE, c.settings().style());
    }

    @Test
    void setStyleNeedsManageHuts() {
        Colony c = found("");
        assertFalse(manager.administration().setStyle(UUID.randomUUID(), c.id(), FakeBlueprints.STYLE));
        assertEquals("", c.settings().style());
        assertEquals(
                "hycolony.permission.toolDenied",
                t.notifier.sent.getLast().msg().key());
    }

    @Test
    void setStyleRefusesAnUnknownStyle() {
        Colony c = found("");
        assertFalse(manager.administration().setStyle(alice, c.id(), "No Such Pack"));
        assertEquals("", c.settings().style());
        assertTrue(t.ui.shown.get(alice) instanceof TownHallView, "the dropdown shows the colony's style again");
    }
}
