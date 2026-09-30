package dev.hylens.core.send;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The operators whose next map "Teleport" sends a citizen instead of them (spec 2026-09-30, § 6.6). The map's packet
 * is read on the network thread, the arming comes from a world's thread: the set is concurrent, and a map serves once.
 */
public final class ArmedMaps {
    private final Set<UUID> armed = ConcurrentHashMap.newKeySet();

    /** {@code operator}'s next map "Teleport" sends a citizen. */
    public void arm(UUID operator) {
        armed.add(operator);
    }

    /** {@code operator}'s map teleports them again, as they leave; armed again, it still serves once. */
    public void disarm(UUID operator) {
        armed.remove(operator);
    }

    /** Whether {@code operator}'s map was armed, disarming it: true serves this click only. Any thread. */
    public boolean use(UUID operator) {
        return armed.remove(operator);
    }
}
