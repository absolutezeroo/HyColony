package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.WorldKey;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PlayerDirectory {
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
}
