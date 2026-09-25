package dev.hycolony.core.citizen;

import dev.hycolony.core.colony.Colony;

/** Citizens of one colony. Minimal version; spawning, respawn and bodies arrive in Task 9. */
public final class CitizenManager {
    private final Colony colony;
    private boolean failNextTick;

    public CitizenManager(Colony colony) {
        this.colony = colony;
    }

    /** Every 60 ticks while ACTIVE. */
    public void tickData() {
        if (failNextTick) {
            failNextTick = false;
            throw new IllegalStateException("test failure");
        }
    }

    /** Slow tick (every 500 ticks) while ACTIVE. */
    public void onColonyTick() {}

    /** Every core tick: citizens' AI. */
    public void tickAi() {}

    /** Test hook: next tickData throws. */
    public void failNextTickForTest() {
        failNextTick = true;
    }
}
