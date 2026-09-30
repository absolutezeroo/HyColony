package dev.hylens.core.menu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Each operator's menu choices (spec 2026-09-30, § 6.4), read by the menu and by the drawings. Worlds' threads and the
 * thread removing a player share it, so the map is concurrent.
 */
public final class Menus {
    private final Map<UUID, MenuState> byOperator = new ConcurrentHashMap<>();

    /** What {@code operator} chose; {@link MenuState#INITIAL} if nothing yet. */
    public MenuState state(UUID operator) {
        return byOperator.getOrDefault(operator, MenuState.INITIAL);
    }

    /** Applies {@code change} to what {@code operator} chose; returns the new choices. */
    public MenuState update(UUID operator, UnaryOperator<MenuState> change) {
        // One atomic step: a forget never loses to a stale read, so the forgotten choices never come back.
        return byOperator.compute(operator, (k, old) -> change.apply(old == null ? MenuState.INITIAL : old));
    }

    /** Forgets what {@code operator} chose, as they leave. */
    public void forget(UUID operator) {
        byOperator.remove(operator);
    }
}
