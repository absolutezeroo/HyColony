package dev.hycolony.plugin.api;

import com.hypixel.hytale.server.core.universe.world.World;

/**
 * HyColony started or stopped in a world. Its two cases are fixed: a third would break an addon's exhaustive
 * {@code switch}.
 *
 * @since 1.0
 */
public sealed interface ColonyWorldEvent permits ColonyWorldStarted, ColonyWorldStopped {
    /** The world. */
    World world();
}
