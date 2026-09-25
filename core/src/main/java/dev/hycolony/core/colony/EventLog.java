package dev.hycolony.core.colony;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Colony history shown to players, bounded like MineColonies' EventDescriptionManager. */
public final class EventLog {
    public static final int MAX_ENTRIES = 100;

    public record Entry(String type, int day, List<String> params) {
        public Entry {
            params = List.copyOf(params);
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();

    public void add(String type, int day, String... params) {
        restore(new Entry(type, day, List.of(params)));
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
