package dev.hycolony.core.colony.permission;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * When a player whose action a colony refused is told so: at most once every 10 seconds, like MC
 * ColonyPermissionEventHandler.cancelEvent (lastPlayerNotificationTick). One per colony, as MC has one handler per
 * colony; world thread only, timed by that world's clock.
 *
 * <p>Deviation from MC: no levitation after more than 10 refusals within those 10 seconds (Hytale has no levitation
 * effect we could verify), and no permission event in the town hall's log (HyColony has no such log yet).
 */
public final class DenialNotices {
    /** MC: TICKS_SECOND * 10. */
    public static final long INTERVAL_TICKS = 20L * 10;
    /** Beyond this many players remembered, the expired ones are forgotten. */
    public static final int MAX_REMEMBERED = 64;

    private final Map<UUID, Long> lastNotice = new HashMap<>();

    /** True, and remembered, when {@code player} must be told at {@code tick}; false during the 10 s after that. */
    public boolean shouldTell(UUID player, long tick) {
        Long last = lastNotice.get(player);
        if (last != null && last + INTERVAL_TICKS >= tick) {
            return false;
        }
        lastNotice.put(player, tick);
        if (lastNotice.size() > MAX_REMEMBERED) {
            lastNotice.values().removeIf(t -> t + INTERVAL_TICKS < tick);
        }
        return true;
    }

    /** How many players are remembered. */
    public int remembered() {
        return lastNotice.size();
    }
}
