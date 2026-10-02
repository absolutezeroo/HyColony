package dev.hycolony.plugin.npc.spawn;

import com.hypixel.hytale.builtin.tagset.TagSetPlugin;
import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * Despawns a naturally spawned hostile NPC standing where the core refuses hostile spawns (ColonyProtection
 * .allowsHostileSpawn): hostile means in HyColony's NPC group (vanilla Aggressive, Outlander, Scarak and a few named
 * roles). The NPC goes at its next tick, as vanilla despawns it (setDespawning, NPCPreTickSystem): removed during its
 * add, its spawner would log an error and a spawn marker could be deleted.
 */
final class HostileSpawns {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final WorldRuntimes runtimes;
    private final String groupId;
    /** The first failure is logged SEVERE, the next ones FINE (one per spawn otherwise). */
    private volatile boolean failed;

    HostileSpawns(WorldRuntimes runtimes, String groupId) {
        this.runtimes = runtimes;
        this.groupId = groupId;
    }

    /** The NPC component's type: null only before the NPC module is set up, which HyColony depends on. */
    static ComponentType<EntityStore, NPCEntity> npcType() {
        return Objects.requireNonNull(NPCEntity.getComponentType(), "the NPC module is not set up");
    }

    /**
     * On the world thread: despawns {@code ref} if {@code natural} holds for it and it is hostile in a colony's
     * territory; never throws.
     */
    void check(Ref<EntityStore> ref, Store<EntityStore> store, Predicate<NPCEntity> natural) {
        try {
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            NPCEntity npc = store.getComponent(ref, npcType());
            if (rt != null && rt.enabled() && npc != null && natural.test(npc) && hostile(npc.getRoleIndex())) {
                despawnInColony(rt, npc, store.getComponent(ref, TransformComponent.getComponentType()));
            }
        } catch (RuntimeException e) {
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyColony hostile spawn check failed");
            failed = true;
        }
    }

    /** Despawns {@code npc} at its next tick if it stands where the core refuses hostile spawns. */
    private static void despawnInColony(WorldRuntime rt, NPCEntity npc, @Nullable TransformComponent transform) {
        if (transform == null) {
            return;
        }
        Vector3d at = transform.getPosition();
        BlockPos pos = new BlockPos((int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z));
        if (!rt.manager().protection().allowsHostileSpawn(pos)) {
            npc.setDespawning(true);
            npc.setDespawnRemainingSeconds(0);
        }
    }

    /** Whether role {@code role} is in the hostile group; throws (logged by {@link #check}) if it is not loaded. */
    private boolean hostile(int role) {
        int group = NPCGroup.getAssetMap().getIndex(groupId);
        if (group == Integer.MIN_VALUE) {
            throw new IllegalStateException("NPC group " + groupId + " is not loaded");
        }
        return TagSetPlugin.get(NPCGroup.class).tagInSet(group, role);
    }
}
