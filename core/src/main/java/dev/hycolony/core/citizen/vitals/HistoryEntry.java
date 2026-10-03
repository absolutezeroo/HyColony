package dev.hycolony.core.citizen.vitals;

import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.StuckHandler;
import dev.hycolony.core.kernel.port.Msg;
import org.jspecify.annotations.Nullable;

/**
 * One thing a tracked citizen did at {@code tick}: its AI or job went from {@code from} to {@code to} ({@code ""} for
 * none), a walk to {@code from} ended {@code to}, or the stuck handler took the action {@code to} on it; {@code detail}
 * says it in the player's language.
 */
public record HistoryEntry(long tick, Kind kind, String from, String to, Msg detail) {
    /** What the entry notes. */
    public enum Kind {
        AI_STATE,
        JOB_STEP,
        WALK_ENDED,
        STUCK
    }

    /**
     * The citizen AI went from {@code from} (null before its first state) to {@code to}; {@code exit} says why it left
     * WORKING, null for no reason given.
     */
    static HistoryEntry aiState(long tick, @Nullable CitizenState from, CitizenState to, @Nullable WorkExit exit) {
        String before = from == null ? "" : from.name();
        Msg detail = exit == null
                ? Msg.of("hycolony.debug.history.aiState", shown(before), to.name())
                : Msg.of(exit.key(), to.name());
        return new HistoryEntry(tick, Kind.AI_STATE, before, to.name(), detail);
    }

    /** The job's AI went from the step {@code from} to {@code to}, {@code ""} for none. */
    static HistoryEntry jobStep(long tick, String from, String to) {
        return new HistoryEntry(
                tick, Kind.JOB_STEP, from, to, Msg.of("hycolony.debug.history.jobStep", shown(from), shown(to)));
    }

    /** A walk ended: what it went to, how, where the body stood and how far from its goal. */
    static HistoryEntry walkEnded(EndedWalk walk) {
        String target = DebugText.pos(walk.target());
        String how = walk.how().name();
        return new HistoryEntry(
                walk.tick(),
                Kind.WALK_ENDED,
                target,
                how,
                Msg.of(
                        "hycolony.debug.history.walkEnded",
                        target,
                        how,
                        DebugText.pos(walk.at()),
                        DebugText.decimal(walk.distance())));
    }

    /** The stuck handler took {@code action} on the walk to {@code target}, the body at {@code at}. */
    static HistoryEntry stuck(long tick, BlockPos target, Vec3 at, StuckHandler.Action action) {
        String to = DebugText.pos(target);
        return new HistoryEntry(
                tick,
                Kind.STUCK,
                to,
                action.name(),
                Msg.of("hycolony.debug.history.stuck", to, DebugText.pos(at), action.name()));
    }

    private static String shown(String step) {
        return step.isEmpty() ? "-" : step;
    }
}
