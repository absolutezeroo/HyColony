package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.npc.hurt.CitizenHitFilter;
import dev.hycolony.plugin.npc.hurt.CitizenHurtSystem;
import dev.hycolony.plugin.npc.hurt.CitizenVulnerabilitySystem;
import dev.hycolony.plugin.npc.hurt.CitizenWallFilter;
import dev.hycolony.plugin.npc.motion.CitizenClimbSystem;
import dev.hycolony.plugin.npc.motion.CitizenMantleSystem;
import dev.hycolony.plugin.npc.motion.CitizenSwimSystem;
import dev.hycolony.plugin.npc.spawn.HostileSpawnSystems;

/**
 * Registers the systems that act on living NPCs: citizens' use by a player, fire, damage, ledge climb, swim and
 * climb, and the hostile creatures kept from spawning in colonies.
 */
public final class NpcSystems {
    private NpcSystems() {}

    /**
     * Registers the use, fire immunity, hurt, ledge climb, swim and climb systems of citizens, and the hostile spawn
     * checks.
     */
    public static void register(ComponentRegistryProxy<EntityStore> registry, WorldRuntimes worlds, IdMap ids) {
        registry.registerSystem(new CitizenUseSystem(worlds));
        registry.registerSystem(new CitizenFireImmunitySystems.Grant());
        registry.registerSystem(new CitizenFireImmunitySystems.Guard());
        registry.registerSystem(new CitizenVulnerabilitySystem());
        registry.registerSystem(new CitizenWallFilter(worlds));
        registry.registerSystem(new CitizenHitFilter(worlds));
        registry.registerSystem(new CitizenHurtSystem(worlds, ids.hurtIgnoredCauses()));
        registry.registerSystem(new CitizenMantleSystem());
        registry.registerSystem(new CitizenSwimSystem());
        registry.registerSystem(new CitizenClimbSystem());
        HostileSpawnSystems.register(registry, worlds, ids);
    }
}
