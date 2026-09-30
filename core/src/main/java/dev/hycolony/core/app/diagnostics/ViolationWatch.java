package dev.hycolony.core.app.diagnostics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The violations that last. {@link Invariants#check} is a snapshot, and some invariants break for a moment by design:
 * a courier notices a cancelled task at its next step, up to a walk delay later. A state seen at every check for
 * {@link #CONFIRM_TICKS} is confirmed, one missing from a check starts over; a trace ({@link Violation.Code#trace()})
 * is confirmed at once, as the next walk may clear it. Meant for checks every few ticks: a lone check confirms only
 * traces, and checks further apart than {@link #CONFIRM_TICKS} confirm a state at its second sight.
 */
public final class ViolationWatch {
    /** Ticks a violation must last: longer than a worker's step takes to notice a changed queue (5 seconds). */
    public static final int CONFIRM_TICKS = 100;

    private Map<Key, Long> firstSeen = new HashMap<>();

    /**
     * Notes {@code current}, the violations a check found at {@code now}; returns those seen at every check for
     * {@link #CONFIRM_TICKS}, in {@code current}'s order. A violation is the same while its code, citizen and message
     * key are, whatever its figures.
     */
    public List<Violation> confirmed(List<Violation> current, long now) {
        Map<Key, Long> seen = new HashMap<>();
        List<Violation> out = new ArrayList<>();
        for (Violation v : current) {
            Key key = new Key(v.code(), v.citizen(), v.detail().key());
            long first = firstSeen.getOrDefault(key, now);
            seen.put(key, first);
            if (v.code().trace() || now - first >= CONFIRM_TICKS) {
                out.add(v);
            }
        }
        firstSeen = seen;
        return out;
    }

    /** What makes two checks' violations the same one. */
    private record Key(Violation.Code code, OptionalInt citizen, String detail) {}
}
