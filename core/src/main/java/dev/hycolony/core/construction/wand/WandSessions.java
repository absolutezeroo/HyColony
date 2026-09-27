package dev.hycolony.core.construction.wand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The build tool session of every player who has one open, server-side (ST AbstractBlueprintManipulationWindow,
 * l.525-590, keeps this client-side; the core keeps it here instead).
 */
final class WandSessions {
    private final Map<UUID, WandSession> sessions = new HashMap<>();

    /** The player's current session, or {@link WandSession#empty()} if they have none. */
    WandSession get(UUID player) {
        return sessions.getOrDefault(player, WandSession.empty());
    }

    /** Replaces the player's session. */
    void put(UUID player, WandSession session) {
        sessions.put(player, session);
    }

    /** Forgets the player's session, as if they never opened the build tool. */
    void clear(UUID player) {
        sessions.remove(player);
    }
}
