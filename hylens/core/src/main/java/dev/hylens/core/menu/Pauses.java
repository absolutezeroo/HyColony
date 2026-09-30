package dev.hylens.core.menu;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Which operator paused each world's colonies through HyLens (spec 2026-09-30, § 6.1): HyColony holds the pause for
 * the plugin, HyLens remembers for whom, so the colonies resume when that operator leaves, if they still hold it.
 * Worlds' threads and the thread removing a player share it, so the map is concurrent. Worlds are named.
 */
public final class Pauses {
    private final Map<String, UUID> byWorld = new ConcurrentHashMap<>();

    /** {@code operator} paused {@code world}'s colonies. */
    public void paused(String world, UUID operator) {
        byWorld.put(world, operator);
    }

    /** {@code world}'s colonies resumed, whoever asked. */
    public void resumed(String world) {
        byWorld.remove(world);
    }

    /** Who paused {@code world}'s colonies through HyLens; empty while they run, or if another plugin paused them. */
    public Optional<UUID> by(String world) {
        return Optional.ofNullable(byWorld.get(world));
    }

    /** The worlds whose colonies {@code operator} paused through HyLens, as they leave. */
    public Set<String> pausedBy(UUID operator) {
        return byWorld.entrySet().stream()
                .filter(e -> e.getValue().equals(operator))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Forgets {@code operator}'s pause of {@code world}; true if they still held it, false once another operator
     * paused it since or it resumed. Run on the world's thread, where no click can come in between.
     */
    public boolean forget(String world, UUID operator) {
        return byWorld.remove(world, operator);
    }
}
