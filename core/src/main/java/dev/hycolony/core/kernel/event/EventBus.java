package dev.hycolony.core.kernel.event;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Synchronous, exact-type event bus, one per world, used on the world's thread. A failing listener never blocks the
 * others. Listeners are kept in arrays replaced on each change (copy on write): a listener may subscribe or close while
 * hearing, and delivering allocates nothing.
 */
public final class EventBus {
    private static final System.Logger LOG = System.getLogger(EventBus.class.getName());
    private static final Registration[] NONE = new Registration[0];

    private final Map<Class<?>, Registration[]> listeners = new HashMap<>();

    /**
     * One listener's subscription. Closing it forgets the listener at once, so nothing of an unloaded addon is kept;
     * the bus drops the registration itself on the world's thread.
     */
    public static final class Registration {
        /** Null once closed. */
        private volatile @Nullable Consumer<Object> listener;
        /** Whether its first failure was logged at WARNING; the next ones are logged at DEBUG. */
        private boolean warned;

        private Registration(Consumer<Object> listener) {
            this.listener = listener;
        }

        /**
         * Stops the delivery to this listener. Idempotent, callable from any thread (a plugin shutting down), never
         * throws. On the world's thread it takes effect at once; from another thread, a delivery already under way
         * may still reach the listener once.
         */
        public void close() {
            listener = null;
        }

        private boolean isOpen() {
            return listener != null;
        }
    }

    /**
     * Delivers every later event of exactly {@code type} to {@code listener}, after the listeners already there;
     * returns its registration, to close. Called on the world's thread; closed registrations are dropped meanwhile.
     */
    public <E> Registration subscribe(Class<E> type, Consumer<? super E> listener) {
        Registration r = new Registration(e -> listener.accept(type.cast(e)));
        Registration[] open = open(type);
        Registration[] next = Arrays.copyOf(open, open.length + 1);
        next[open.length] = r;
        listeners.put(type, next);
        return r;
    }

    /**
     * Whether an open listener hears {@code type}, so a publisher skips building an event nobody hears. When none is
     * left, the type's closed registrations are dropped.
     */
    public boolean hasListeners(Class<?> type) {
        Registration[] all = listeners.getOrDefault(type, NONE);
        for (Registration r : all) {
            if (r.isOpen()) {
                return true;
            }
        }
        if (all.length > 0) {
            listeners.remove(type);
        }
        return false;
    }

    /**
     * Delivers {@code event} to the open listeners of its exact type, in subscription order, then drops closed ones.
     */
    public void post(Object event) {
        boolean anyClosed = false;
        for (Registration r : listeners.getOrDefault(event.getClass(), NONE)) {
            Consumer<Object> listener = r.listener;
            if (listener == null) {
                anyClosed = true;
            } else {
                deliver(r, listener, event);
            }
        }
        if (anyClosed) {
            Registration[] open = open(event.getClass()); // read afresh: a listener may have subscribed while hearing
            if (open.length == 0) {
                listeners.remove(event.getClass());
            } else {
                listeners.put(event.getClass(), open);
            }
        }
    }

    /** Test hook: how many registrations the bus keeps for {@code type}, closed ones included. */
    int registrations(Class<?> type) {
        return listeners.getOrDefault(type, NONE).length;
    }

    /**
     * A listener's exception, or a linkage error (its addon was unloaded), is logged and swallowed: at WARNING the
     * first time for that listener, at DEBUG after. The message is only built when it is logged.
     */
    private static void deliver(Registration r, Consumer<Object> listener, Object event) {
        try {
            listener.accept(event);
        } catch (RuntimeException | LinkageError e) {
            System.Logger.Level level = r.warned ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING;
            if (LOG.isLoggable(level)) {
                LOG.log(level, "Event listener failed: " + event, e);
            }
            r.warned = true;
        }
    }

    /** The open registrations of {@code type}, in order; the same array when none is closed. */
    private Registration[] open(Class<?> type) {
        Registration[] all = listeners.getOrDefault(type, NONE);
        for (Registration r : all) {
            if (!r.isOpen()) {
                return Arrays.stream(all).filter(Registration::isOpen).toArray(Registration[]::new);
            }
        }
        return all;
    }
}
