package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.npc.spawn.HostileSpawnSystems;

/**
 * Registers the systems that act on NPCs once they live: citizens' use by a player, fire, damage and ledge climb, and
 * the hostile creatures kept from spawning in colonies.
 */
public final class CitizenSystems {
    private CitizenSystems() {}

    /** Registers the use, fire immunity, hurt and ledge climb systems of citizens, and the hostile spawn checks. */
    public static void register(ComponentRegistryProxy<EntityStore> registry, WorldRuntimes worlds, IdMap ids) {
        registry.registerSystem(new CitizenUseSystem(worlds));
        registry.registerSystem(new CitizenFireImmunitySystems.Grant());
        registry.registerSystem(new CitizenFireImmunitySystems.Guard());
        registry.registerSystem(new CitizenHurtSystem(worlds, ids.hurtIgnoredCauses()));
        registry.registerSystem(new CitizenMantleSystem());
        HostileSpawnSystems.register(registry, worlds, ids);
    }
}
