package dev.hycolony.core.citizen.vitals;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.nav.StuckHandler;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A citizen's walks as its vital signs keep them: the last one started, the last one ended, the last stuck action. */
public final class WalkVitals {
    private @Nullable BlockPos target;
    private long startTick;
    private @Nullable EndedWalk lastEnd;
    private StuckHandler.@Nullable Action lastStuck;
    private long lastStuckTick;

    /** What its last started walk goes to; empty before any. */
    public Optional<BlockPos> target() {
        return Optional.ofNullable(target);
    }

    /** The tick its last walk started. */
    public long startTick() {
        return startTick;
    }

    /** How its last ended walk ended, with that walk's own target; empty before any. */
    public Optional<EndedWalk> lastEnd() {
        return Optional.ofNullable(lastEnd);
    }

    /** The stuck handler's last action on its walks; empty before any. */
    public Optional<StuckHandler.Action> lastStuck() {
        return Optional.ofNullable(lastStuck);
    }

    /** The tick of the stuck handler's last action. */
    public long lastStuckTick() {
        return lastStuckTick;
    }

    void started(BlockPos walkTarget, long tick) {
        target = walkTarget;
        startTick = tick;
    }

    void ended(EndedWalk end) {
        lastEnd = end;
    }

    void stuck(StuckHandler.Action action, long tick) {
        lastStuck = action;
        lastStuckTick = tick;
    }
}
