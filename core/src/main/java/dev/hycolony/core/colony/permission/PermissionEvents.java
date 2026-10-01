package dev.hycolony.core.colony.permission;

import dev.hycolony.core.kernel.BlockPos;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The colony's refused actions, oldest first, as MC BuildingTownHall.permissionEvents: at most
 * {@value #MAX_EVENTS}, an event already logged is not logged twice, kept in memory only (MC never saves them).
 */
public final class PermissionEvents {
    /** MC ColonyConstants.MAX_COLONY_EVENTS. */
    public static final int MAX_EVENTS = 100;

    /** MC PermissionEvent: who was refused (empty for an unknown player), what, where. */
    public record Event(Optional<UUID> player, String name, Action action, BlockPos pos) {}

    private final Deque<Event> events = new ArrayDeque<>();

    /** MC addPermissionEvent: ignored if already logged; the oldest goes once full. True if logged. */
    public boolean add(Event event) {
        if (events.contains(event)) {
            return false;
        }
        if (events.size() >= MAX_EVENTS) {
            events.removeFirst();
        }
        events.addLast(event);
        return true;
    }

    /** MC removePermissionEvents: drops {@code player}'s events; true if any was dropped. */
    public boolean removeOf(UUID player) {
        return events.removeIf(e -> e.player().filter(player::equals).isPresent());
    }

    public List<Event> entries() {
        return List.copyOf(events);
    }
}
