package dev.hycolony.core.colony.view;

import dev.hycolony.core.colony.ui.UiPort;
import dev.hycolony.core.colony.ui.WindowKey;
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

    private static final System.Logger LOG = System.getLogger(OpenWindows.class.getName());
    private final UiPort ui;
    private final Map<UUID, Watch<?>> open = new HashMap<>();
    private int ticks;

    OpenWindows(UiPort ui) {
        this.ui = ui;
    }

    /**
     * Watches the window just shown to {@code player}, replacing the one watched before. {@code view} is empty once
     * the window can no longer be shown; {@code redraw} is false once the player no longer has it open.
     */
    <V> void watch(UUID player, Shown<V> shown, Supplier<Optional<V>> view, BiPredicate<UUID, V> redraw) {
        open.put(player, new Watch<>(shown.key(), shown.view(), view, redraw));
    }

    /** The window just shown: which one, and the view drawn in it. */
    record Shown<V>(WindowKey key, V view) {}

    /** Every {@link #UPDATE_SUBSCRIBERS_INTERVAL_TICKS}: redraws changed windows, forgets gone or closed ones. */
    void tick() {
        if (++ticks < UPDATE_SUBSCRIBERS_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        open.entrySet().removeIf(e -> !refresh(e.getKey(), e.getValue()));
    }

    /**
     * False once the window is gone: closed or replaced by the player, its subject removed, access lost. A failure
     * drops that window only, so the others are still refreshed.
     */
    private boolean refresh(UUID player, Watch<?> w) {
        try {
            return ui.isShowing(player, w.key) && w.refresh(player);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Live refresh of " + w.key + " for " + player + " failed; dropped", e);
            return false;
        }
    }

    private static final class Watch<V> {
        private final WindowKey key;
        private V last;
        private final Supplier<Optional<V>> view;
        private final BiPredicate<UUID, V> redraw;

        Watch(WindowKey key, V last, Supplier<Optional<V>> view, BiPredicate<UUID, V> redraw) {
            this.key = key;
            this.last = last;
            this.view = view;
            this.redraw = redraw;
        }

        /** Redraws if the view changed; false once its subject is removed or access lost. */
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
