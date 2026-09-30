package dev.hycolony.plugin.bridge;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.api.Subscription;
import dev.hycolony.plugin.api.ColonyWorldEvent;
import dev.hycolony.plugin.api.ColonyWorldStarted;
import dev.hycolony.plugin.api.ColonyWorldStopped;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Tells the api's listeners where HyColony starts and stops. Their list is copied on write: an addon subscribes from
 * its start, on another thread than the worlds' (spec 2026-09-30, § 4.1).
 */
final class WorldAnnouncer {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final List<Entry> listeners = new CopyOnWriteArrayList<>();
    /** The worlds told started: only their stop is told. */
    private final Set<String> started = ConcurrentHashMap.newKeySet();
    /** Whether a listener failed yet: the first failure is a WARNING, the next ones FINE (spec § 4.1). */
    private volatile boolean warned;

    /** One subscription: closed, it hears nothing more, even from a telling already under way. */
    private final class Entry implements Subscription {
        private final Consumer<? super ColonyWorldEvent> listener;
        private volatile boolean closed;

        Entry(Consumer<? super ColonyWorldEvent> listener) {
            this.listener = listener;
        }

        @Override
        public void close() {
            closed = true;
            listeners.remove(this);
        }
    }

    /** Hears every later start and stop; the subscription ends it. Any thread. */
    Subscription add(Consumer<? super ColonyWorldEvent> listener) {
        Entry e = new Entry(listener);
        listeners.add(e);
        return e;
    }

    void started(World world) {
        started.add(world.getName());
        tell(new ColonyWorldStarted(world));
    }

    /** Tells the stop of {@code world}, if its start was told. */
    void stopped(World world) {
        if (started.remove(world.getName())) {
            tell(new ColonyWorldStopped(world));
        }
    }

    /** Tells each listener alone: one that fails or whose addon unloaded is logged, the others still told. */
    private void tell(ColonyWorldEvent event) {
        for (Entry e : listeners) {
            if (e.closed) {
                continue;
            }
            try {
                e.listener.accept(event);
            } catch (RuntimeException | LinkageError ex) {
                LOG.at(warned ? Level.FINE : Level.WARNING).withCause(ex).log("A listener of HyColony's worlds failed");
                warned = true;
            }
        }
    }
}
