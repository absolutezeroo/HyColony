package dev.hycolony.core.citizen;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * The citizens their colony's respawn check found a loaded spot for, yet spawned no body at; for diagnostics. A citizen
 * whose spots are all unloaded is not one: MC waits for them too.
 */
public final class FailedRespawns {
    private final Map<Integer, Long> since = new HashMap<>();

    /** Notes citizen {@code id}'s respawn check at {@code tick}: a failure keeps the tick of the first in a row. */
    void checked(int id, boolean failed, long tick) {
        if (failed) {
            since.putIfAbsent(id, tick);
        } else {
            since.remove(id);
        }
    }

    /** Citizen {@code id} got a body. */
    void bodied(int id) {
        since.remove(id);
    }

    /** Since which respawn check citizen {@code id}'s respawn has kept failing; empty while it has not. */
    public OptionalLong since(int id) {
        Long tick = since.get(id);
        return tick == null ? OptionalLong.empty() : OptionalLong.of(tick);
    }
}
