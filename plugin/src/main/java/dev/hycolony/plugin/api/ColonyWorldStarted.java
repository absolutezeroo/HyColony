package dev.hycolony.plugin.api;

import com.hypixel.hytale.server.core.universe.world.World;

/**
 * HyColony runs in {@code world} from now, enabled: {@link HyColonyApi#world} finds it.
 *
 * @since 1.0
 */
public record ColonyWorldStarted(World world) implements ColonyWorldEvent {}
