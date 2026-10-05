package dev.hyangler.plugin.cast;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * The casts in progress, one a player (spec § 7.4), never saved. Each world's thread reads and writes its own players'
 * casts; the map is concurrent because worlds tick on different threads.
 */
final class Casts {
    private final Map<UUID, ActiveCast> byPlayer = new ConcurrentHashMap<>();

    /** The player's cast in progress, if any. */
    Optional<ActiveCast> of(UUID player) {
        return Optional.ofNullable(byPlayer.get(player));
    }

    /** The player's cast in progress, or null: the per-tick systems' lookup, without allocating an Optional. */
    @Nullable
    ActiveCast running(UUID player) {
        return byPlayer.get(player);
    }

    /** Records the player's new cast. */
    void start(UUID player, ActiveCast cast) {
        byPlayer.put(player, cast);
    }

    /** Forgets the player's cast if it is this one; a newer cast stays. */
    void end(UUID player, ActiveCast cast) {
        byPlayer.remove(player, cast);
    }

    /** Every cast in progress, a live view. */
    Collection<ActiveCast> all() {
        return byPlayer.values();
    }
}
