package dev.hycolony.core.kernel.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Synchronous, exact-type event bus. One per world. A failing listener never blocks the others. */
public final class EventBus {
    private static final System.Logger LOG = System.getLogger(EventBus.class.getName());

    private final Map<Class<?>, List<Consumer<Object>>> listeners = new HashMap<>();

    public <E> void subscribe(Class<E> type, Consumer<? super E> listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(e -> listener.accept(type.cast(e)));
    }

    public void post(Object event) {
        for (Consumer<Object> listener : listeners.getOrDefault(event.getClass(), List.of())) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Event listener failed for " + event, e);
            }
        }
    }
}
