package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonySettings.Toggle;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC WindowSettings and TriggerSettingMessage: the town hall's switches, MANAGE_HUTS. */
class TownHallSettingsTest {
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID();
    private final Colony colony;

    TownHallSettingsTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
    }

    @Test
    void toggleFlipsTheSettingAndShowsTheTownHallAgain() {
        assertTrue(manager.administration().toggle(alice, colony.id(), Toggle.MOVE_IN));
        assertFalse(colony.settings().moveIn());
        TownHallView.Settings shown = ((TownHallView) t.ui.shown.get(alice)).settings();
        assertFalse(shown.moveIn());
        assertTrue(shown.autoHiring());
        assertTrue(shown.autoHousing());
        assertTrue(manager.administration().toggle(alice, colony.id(), Toggle.AUTO_HIRING));
        TownHallView.Settings hiringOff = ((TownHallView) t.ui.shown.get(alice)).settings();
        assertFalse(hiringOff.autoHiring());
        assertTrue(hiringOff.autoHousing(), "only the switch clicked turns over");
        assertTrue(manager.administration().toggle(alice, colony.id(), Toggle.AUTO_HOUSING));
        assertFalse(colony.settings().autoHiring());
        assertFalse(colony.settings().autoHousing());
    }

    @Test
    void toggleNeedsManageHutsAndSaysSo() {
        UUID stranger = UUID.randomUUID();
        assertFalse(manager.administration().toggle(stranger, colony.id(), Toggle.MOVE_IN));
        assertTrue(colony.settings().moveIn());
        assertEquals(
                "hycolony.permission.toolDenied",
                t.notifier.sent.getLast().msg().key());
    }

    @Test
    void noInitialCitizenMovesInWhileMoveInIsOff() {
        colony.settings().setMoveIn(false);
        for (int i = 0; i < 20; i++) {
            colony.citizens().onColonyTick();
        }
        assertTrue(colony.citizens().all().isEmpty());
        colony.settings().setMoveIn(true);
        for (int i = 0; i < 20; i++) {
            colony.citizens().onColonyTick();
        }
        assertFalse(colony.citizens().all().isEmpty());
    }
}
