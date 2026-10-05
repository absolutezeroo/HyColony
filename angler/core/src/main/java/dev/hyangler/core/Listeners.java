package dev.hyangler.core;

import dev.hyangler.api.Subscription;
import java.util.ArrayList;
import java.util.List;

/**
 * A copy-on-write list of registrations: added or closed from any thread or inside a callback, read without
 * allocating (HyColony api spec § 4.1, Distribution).
 */
final class Listeners<T> {
    /** One registration: its identity tells two registrations of equal values apart. */
    private static final class Registration<T> {
        private final T value;

        Registration(T value) {
            this.value = value;
        }
    }

    private List<Registration<T>> registrations = List.of(); // guarded by this
    private volatile List<T> items = List.of();

    /** Adds t; closing the subscription removes this registration, and only it, however often it is closed. */
    synchronized Subscription add(T t) {
        Registration<T> r = new Registration<>(t);
        List<Registration<T>> next = new ArrayList<>(registrations);
        next.add(r);
        publish(next);
        return () -> remove(r);
    }

    /** The registrations now, in order. */
    List<T> snapshot() {
        return items;
    }

    private synchronized void remove(Registration<T> r) {
        List<Registration<T>> next = new ArrayList<>(registrations);
        if (next.remove(r)) {
            publish(next);
        }
    }

    private void publish(List<Registration<T>> next) {
        registrations = List.copyOf(next);
        items = next.stream().map(r -> r.value).toList();
    }
}
