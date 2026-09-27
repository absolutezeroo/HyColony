package dev.hycolony.core.colony.view;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * The window each player last opened, redrawn when its view changes (MC {@code ColonyPackageManager.updateSubscribers}
 * sends the dirty colony and building views every {@code UPDATE_SUBSCRIBERS_INTERVAL} ticks, and open windows re-read
 * them). Deviation from MC: HyColony has no view dirty flags; the fresh view is compared with the one last shown,
 * which sends the same updates.
 */
final class OpenWindows {
    /** MC {@code ColonyConstants.UPDATE_SUBSCRIBERS_INTERVAL}, in ticks. */
    static final int UPDATE_SUBSCRIBERS_INTERVAL_TICKS = 20;

    private final Map<UUID, Watch<?>> open = new HashMap<>();
    private int ticks;

    /**
     * Watches the window just shown to {@code player}, replacing the one watched before. {@code view} is empty once
     * the window can no longer be shown; {@code redraw} is false once the player no longer has it open.
     */
    <V> void watch(UUID player, V shown, Supplier<Optional<V>> view, BiPredicate<UUID, V> redraw) {
        open.put(player, new Watch<>(shown, view, redraw));
    }

    /** Every {@link #UPDATE_SUBSCRIBERS_INTERVAL_TICKS}: redraws changed windows, forgets gone or closed ones. */
    void tick() {
        if (++ticks < UPDATE_SUBSCRIBERS_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        open.entrySet().removeIf(e -> !e.getValue().refresh(e.getKey()));
    }

    private static final class Watch<V> {
        private V last;
        private final Supplier<Optional<V>> view;
        private final BiPredicate<UUID, V> redraw;

        Watch(V last, Supplier<Optional<V>> view, BiPredicate<UUID, V> redraw) {
            this.last = last;
            this.view = view;
            this.redraw = redraw;
        }

        /** False once the window is gone: its subject removed, access lost, or closed by the player. */
        boolean refresh(UUID player) {
            V now = view.get().orElse(null);
            if (now == null) {
                return false;
            }
            if (now.equals(last)) {
                return true;
            }
            last = now;
            return redraw.test(player, now);
        }
    }
}
