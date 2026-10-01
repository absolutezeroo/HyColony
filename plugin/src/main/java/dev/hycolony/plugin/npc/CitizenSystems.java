package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntimes;

/**
 * Registers the systems that act on citizens' bodies once they live: a player's use, fire, damage, the ledge climb.
 */
public final class CitizenSystems {
    private CitizenSystems() {}

    /** Registers the use, fire immunity, hurt and ledge climb systems of citizens. */
    public static void register(ComponentRegistryProxy<EntityStore> registry, WorldRuntimes worlds, IdMap ids) {
        registry.registerSystem(new CitizenUseSystem(worlds));
        registry.registerSystem(new CitizenFireImmunitySystems.Grant());
        registry.registerSystem(new CitizenFireImmunitySystems.Guard());
        registry.registerSystem(new CitizenHurtSystem(worlds, ids.hurtIgnoredCauses()));
        registry.registerSystem(new CitizenMantleSystem());
    }
}
