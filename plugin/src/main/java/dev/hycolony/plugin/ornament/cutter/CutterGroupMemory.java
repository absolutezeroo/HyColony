package dev.hycolony.plugin.ornament.cutter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cutter group each player opened last, for every cutter (MC DO ArchitectsCutterScreen.groupIndexCache: the window
 * reopens on it, on its first shape). Read and written from every world's thread.
 *
 * <p>Deviation from MC: forgotten when the player disconnects; DO's client static lives until the client quits, which
 * the server cannot see.
 */
public final class CutterGroupMemory {
    private final Map<UUID, Integer> groups = new ConcurrentHashMap<>();

    /** The group player opened last; 0 (the first) when none. */
    int group(UUID player) {
        return groups.getOrDefault(player, 0);
    }

    void remember(UUID player, int group) {
        groups.put(player, group);
    }

    /** Forgets player (they left). */
    public void forget(UUID player) {
        groups.remove(player);
    }
}
