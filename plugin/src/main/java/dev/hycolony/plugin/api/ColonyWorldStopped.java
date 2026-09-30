package dev.hycolony.plugin.api;

import com.hypixel.hytale.server.core.universe.world.World;

/**
 * HyColony stopped in {@code world}, told started before, its colonies saved: what an addon kept of it is stale.
 *
 * @since 1.0
 */
public record ColonyWorldStopped(World world) implements ColonyWorldEvent {}
