package dev.hylens.plugin;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.plugin.api.ColonyClock;
import dev.hycolony.plugin.api.HyColonyApi;
import java.util.Optional;

/**
 * HyColony's api as HyLens reaches it: empty, never an exception, once HyColony stopped (its api holder is empty).
 * A call off the world's thread still throws, as the api wants (spec 2026-09-30, § 4.1).
 */
public final class HyColonyAccess {
    private HyColonyAccess() {}

    /** HyColony's api; empty once HyColony stopped. Any thread. */
    public static Optional<HyColonyApi> api() {
        try {
            return Optional.of(HyColonyApi.get());
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    /** HyColony in {@code world}; empty where it does not run, or once it stopped. World thread. */
    public static Optional<ColonyWorld> world(World world) {
        return api().flatMap(a -> a.world(world));
    }

    /** The colony clock of {@code world}; empty where HyColony does not run, or once it stopped. World thread. */
    public static Optional<ColonyClock> clock(World world) {
        return api().flatMap(a -> a.clock(world));
    }
}
