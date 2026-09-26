package dev.hycolony.core.kernel.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.StuckHandler.Action;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StuckHandlerTest {
    private static final Vec3 HOME = new Vec3(0, 64, 0);
    private static final Vec3 FAR = new Vec3(20, 64, 0);

    /** Every non-NONE action with its tick, checking each tick for {@code ticks} ticks at a fixed position. */
    private static List<String> run(StuckHandler h, Vec3 pos, int ticks) {
        List<String> out = new ArrayList<>();
        for (long t = 1; t <= ticks; t++) {
            Action a = h.check(pos, t);
            if (a != Action.NONE) {
                out.add(a + "@" + t);
            }
        }
        return out;
    }

    @Test
    void aBodyThatNeverMovesIsRepathedThenTeleportedThenGivenUp() {
        StuckHandler h = new StuckHandler();
        h.start(FAR, HOME, 0);
        assertEquals(List.of("REPATH@100", "TELEPORT@300", "GIVE_UP@500"), run(h, HOME, 600));
    }

    @Test
    void progressResetsTheEscalation() {
        StuckHandler h = new StuckHandler();
        h.start(FAR, HOME, 0);
        for (long t = 1; t <= 1000; t++) {
            assertEquals(Action.NONE, h.check(new Vec3(t / 50.0, 64, 0), t), "moving one block every 50 ticks");
        }
    }

    @Test
    void closeToTheDestinationItGivesUpInsteadOfTeleporting() {
        StuckHandler h = new StuckHandler();
        h.start(new Vec3(2, 64, 0), HOME, 0);
        assertEquals(List.of("GIVE_UP@100"), run(h, HOME, 600));
    }

    @Test
    void globalTimeoutTeleportsEvenWhileMoving() {
        StuckHandler h = new StuckHandler();
        h.start(new Vec3(1000, 64, 0), HOME, 0);
        List<String> actions = new ArrayList<>();
        for (long t = 1; t <= 3000; t++) {
            Action a = h.check(new Vec3((t % 100) / 10.0, 64, 0), t); // circling: always moving, never closer
            if (a != Action.NONE) {
                actions.add(a + "@" + t);
            }
        }
        // max(2400, 200 * max(10, ~1000)) = 200 000 ticks: beyond this run; nothing fires for a far goal.
        assertEquals(List.of(), actions);

        h.start(new Vec3(5, 64, 0), new Vec3(10, 64, 0), 0);
        actions.clear();
        for (long t = 1; t <= 3000; t++) {
            Action a = h.check(new Vec3(10 + (t % 100) / 10.0, 64, 0), t); // 5 to 15 blocks away
            if (a != Action.NONE) {
                actions.add(a + "@" + t);
            }
        }
        assertEquals("TELEPORT@2410", actions.get(0), "MIN_TP_DELAY, checked every 10 ticks");
    }
}
