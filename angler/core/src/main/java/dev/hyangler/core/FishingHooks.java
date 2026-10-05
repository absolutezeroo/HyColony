package dev.hyangler.core;

import dev.hyangler.api.Angler;
import dev.hyangler.api.Catch;
import dev.hyangler.api.FishingContext;
import dev.hyangler.api.Subscription;
import dev.hyangler.api.condition.CatchHook;
import dev.hyangler.api.event.FishingEvent;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Other mods' say in fishing (spec § 8.1): the catch hooks, run in order on every catch, and the event listeners, each
 * hearing its own event type; one that fails is reported and the others carry on.
 */
final class FishingHooks {
    private record Typed(Class<?> type, Consumer<Object> listener) {}

    private record Hook(String owner, CatchHook hook) {}

    private final Consumer<RuntimeException> onFailure;
    private final Listeners<Hook> hooks = new Listeners<>();
    private final Listeners<Typed> listeners = new Listeners<>();

    /** onFailure: where a hook's or a listener's failure is reported. */
    FishingHooks(Consumer<RuntimeException> onFailure) {
        this.onFailure = onFailure;
    }

    /** Adds a catch hook, run after those added before it; closing the subscription removes it. Throws on null. */
    Subscription addCatchHook(String owner, CatchHook hook) {
        return hooks.add(new Hook(Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(hook, "hook")));
    }

    /** Adds a listener of the events of type E; closing the subscription removes it. Throws on null. */
    <E extends FishingEvent> Subscription subscribe(Class<E> type, Consumer<? super E> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        return listeners.add(new Typed(type, e -> listener.accept(type.cast(e))));
    }

    /**
     * Runs the catch hooks in order on a rolled catch: each may change it or cancel it (empty), which ends the run. A
     * hook that fails or returns null is reported and skipped, the catch kept as it was.
     */
    Optional<Catch> apply(Angler angler, FishingContext ctx, Optional<Catch> rolled) {
        Optional<Catch> landed = rolled;
        for (Hook h : hooks.snapshot()) {
            if (landed.isEmpty()) {
                break;
            }
            try {
                Optional<Catch> next = h.hook().apply(angler, ctx, landed.get());
                if (next == null) {
                    onFailure.accept(new IllegalStateException(h.owner() + "'s catch hook returned null"));
                } else {
                    landed = next;
                }
            } catch (RuntimeException e) {
                onFailure.accept(e);
            }
        }
        return landed;
    }

    /** Hands e to the listeners of its type, each isolated: one that fails is reported and the others still hear. */
    void publish(FishingEvent e) {
        for (Typed t : listeners.snapshot()) {
            if (t.type().isInstance(e)) {
                try {
                    t.listener().accept(e);
                } catch (RuntimeException failure) {
                    onFailure.accept(failure);
                }
            }
        }
    }
}
