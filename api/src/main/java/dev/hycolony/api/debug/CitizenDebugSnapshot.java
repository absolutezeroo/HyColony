package dev.hycolony.api.debug;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Experimental;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Vec;
import java.util.List;
import java.util.Optional;

/**
 * What a citizen's AI was doing when read, at {@code tick}: its AI state and job step ({@code ""} for none, and while
 * its body is unloaded, as no AI runs then) and the ticks they started, what the job says it does, where its last walk
 * went, the {@code path} its body still plans to walk there (the next point first, its navigation's then HyColony's
 * detour corners; empty when it steers straight at its target, stands, or its body is unloaded), how its last walk
 * ended, the stuck handler's last action ({@code ""} for none) and its tick, the ids of its job's own queue (head
 * first), the leisure ticks it has left (0 or less outside a break), and its {@code history} while tracked (else
 * empty). Its broken invariants are in {@link DebugAccess#check}.
 *
 * @since 1.0
 */
@Experimental
public record CitizenDebugSnapshot(
        CitizenRef citizen,
        long tick,
        String aiState,
        long aiStateSince,
        String jobStep,
        long jobStepSince,
        Optional<ApiText> activity,
        Optional<Pos> walkTarget,
        List<Vec> path,
        Optional<WalkEnded> lastWalkEnd,
        String lastStuck,
        long lastStuckTick,
        List<String> queue,
        int leisureTicks,
        List<HistoryEntry> history) {
    /** Keeps its own copies of the lists. */
    public CitizenDebugSnapshot {
        path = List.copyOf(path);
        queue = List.copyOf(queue);
        history = List.copyOf(history);
    }
}
