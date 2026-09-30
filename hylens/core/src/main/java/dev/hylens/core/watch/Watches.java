package dev.hylens.core.watch;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Subscription;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which citizen each operator watches, with the tracking that keeps its history (spec 2026-09-30, § 6.1). Watches
 * start on the world's thread; an operator leaving stops theirs from whatever thread removes them, so the map is
 * concurrent and a tracking is closed with {@link Subscription#close}, safe from any thread.
 */
public final class Watches {
    private record Watch(CitizenRef citizen, Subscription tracking) {}

    private final Map<UUID, Watch> byOperator = new ConcurrentHashMap<>();

    /** {@code operator} now watches {@code citizen}, held by {@code tracking}; closes the tracking it replaces. */
    public void start(UUID operator, CitizenRef citizen, Subscription tracking) {
        Watch previous = byOperator.put(operator, new Watch(citizen, tracking));
        if (previous != null) {
            previous.tracking().close();
        }
    }

    /** Stops what {@code operator} watches and closes its tracking; the citizen it watched, empty if none. */
    public Optional<CitizenRef> stop(UUID operator) {
        Watch watch = byOperator.remove(operator);
        if (watch == null) {
            return Optional.empty();
        }
        watch.tracking().close();
        return Optional.of(watch.citizen());
    }

    /** The citizen {@code operator} watches; empty if none. */
    public Optional<CitizenRef> watched(UUID operator) {
        return Optional.ofNullable(byOperator.get(operator)).map(Watch::citizen);
    }
}
