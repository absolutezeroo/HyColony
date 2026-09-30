package dev.hycolony.core.citizen.vitals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * A tracked citizen's last {@value #SIZE} {@link HistoryEntry}s, oldest first, kept while one of its trackings is open.
 * Closing a tracking only sets a flag, safe from any thread; the world thread drops the closed ones when it next records
 * or reads.
 *
 * <p>Deviation from MC: {@code BasicStateMachine} keeps a ring of 20 per state machine (the citizen's and its job's),
 * stamped in real time, enabled outside production, for a player in debug mode who interacts with the citizen
 * ({@code EntityCitizen}) and at the AI's first exception. Here one ring of 20 in ticks holds the AI and job transitions,
 * walk ends and stuck actions, and only a tracked citizen allocates one (spec 2026-09-30, § 5).
 */
public final class CitizenHistory {
    /** Entries kept per tracked citizen. */
    static final int SIZE = 20;

    private final ArrayDeque<HistoryEntry> entries = new ArrayDeque<>(SIZE);
    private final List<Tracking> trackings = new ArrayList<>(1);

    /** One tracker's hold on a citizen's history. */
    public static final class Tracking {
        private volatile boolean open = true;

        private Tracking() {}

        /** Stops this tracking: idempotent, safe from any thread, never throws. */
        public void close() {
            open = false;
        }
    }

    /** Opens one more tracking of this history. */
    Tracking track() {
        Tracking tracking = new Tracking();
        trackings.add(tracking);
        return tracking;
    }

    /** Forgets the closed trackings; returns whether one is still open. */
    boolean tracked() {
        trackings.removeIf(tracking -> !tracking.open);
        return !trackings.isEmpty();
    }

    /** Notes {@code entry}, dropping the oldest one when full. */
    void add(HistoryEntry entry) {
        if (entries.size() == SIZE) {
            entries.removeFirst();
        }
        entries.addLast(entry);
    }

    /** Its entries, oldest first, copied. */
    List<HistoryEntry> entries() {
        return List.copyOf(entries);
    }
}
