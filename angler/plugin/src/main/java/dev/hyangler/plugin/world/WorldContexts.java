package dev.hyangler.plugin.world;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hyangler.core.context.ContextFactory;
import dev.hyangler.plugin.AnglerIds;

/** The core's context factory for one world: its four ports and the id-map's salt environments. */
public final class WorldContexts {
    private WorldContexts() {}

    /** A factory reading world; cheap, built per cast or per command, used on world's thread. */
    public static ContextFactory of(World world, AnglerIds ids) {
        return new ContextFactory(
                new HytaleBlocks(world, ids.waterSurface()),
                new HytaleEnvironments(world, ids.zones()),
                new HytaleClock(world),
                new HytaleWeather(world, ids.precipitation()),
                ids.saltEnvironments());
    }
}
