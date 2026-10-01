package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.WorldKey;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public interface PlayerDirectory {
    /** A player known to the game: its id and name. */
    record Profile(UUID id, String name) {}

    /** An online player's name; empty for an offline or unknown player (never throws). */
    Optional<String> name(UUID player);

    /**
     * The player named {@code name}, online or known to the game's profiles (MC's profile cache): {@code then} gets
     * it, or empty, on the world thread, now or later. Never throws.
     */
    void findByName(String name, Consumer<Optional<Profile>> then);

    boolean isOnline(UUID player);

    Optional<BlockPos> position(UUID player);

    Collection<UUID> onlineIn(WorldKey world);

    /** A server operator; false otherwise (never throws). */
    boolean isOperator(UUID player);

    /** Online in this world and in creative mode right now; false otherwise (never throws). */
    boolean isCreative(UUID player);

    /** A server operator, online and in creative mode right now. */
    default boolean isCreativeOperator(UUID player) {
        return isOperator(player) && isCreative(player);
    }

    /**
     * The quarter-turn the player is facing, rounded to the nearest cardinal direction: 0 = north (-Z), 1 = east
     * (+X), 2 = south (+Z), 3 = west (-X), clockwise from north, as {@code Building}, {@code WorkOrder} and
     * {@code BlueprintSource.load} use for a placement rotation. The offline or unknown player gives 0 (never
     * throws).
     */
    int facing(UUID player);

    /**
     * The player's health in percent of its maximum (Hytale has no hunger: a dining hall's waiter feeds a hurt player,
     * where MC feeds a hungry one). The offline or unknown player gives 100 (never throws).
     */
    int healthPercent(UUID player);
}
