package dev.hycolony.core.colony;

import dev.hycolony.core.kernel.BlockPos;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Colony history shown to players, bounded like MineColonies' EventDescriptionManager. */
public final class EventLog {
    public static final int MAX_ENTRIES = 100;

    /** {@code pos}: where it happened (MC EventDescription.getEventPos); empty for an entry saved without one. */
    public record Entry(String type, int day, List<String> params, Optional<BlockPos> pos) {
        public Entry {
            params = List.copyOf(params);
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();

    /** Logs an event with no position. */
    public void add(String type, int day, String... params) {
        restore(new Entry(type, day, List.of(params), Optional.empty()));
    }

    /** Logs an event that happened at {@code pos}. */
    public void addAt(BlockPos pos, String type, int day, String... params) {
        restore(new Entry(type, day, List.of(params), Optional.of(pos)));
    }

    public void restore(Entry entry) {
        if (entries.size() == MAX_ENTRIES) {
            entries.removeFirst();
        }
        entries.addLast(entry);
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }
}
