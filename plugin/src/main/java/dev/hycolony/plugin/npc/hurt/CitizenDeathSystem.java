package dev.hycolony.plugin.npc.hurt;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.death.CitizenDeath;
import dev.hycolony.core.citizen.death.DeathCause;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.npc.CitizenTag;
import dev.hycolony.plugin.npc.HyColonyComponents;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * A citizen body died (MC EntityCitizen.die): Hytale adds DeathComponent when its health reaches 0, never when a dead
 * entity loads (citizen-death.md § 2). The body is untracked at once, so the core never despawns the corpse, which
 * Hytale removes after its death animation; the core's death runs from the world's task queue, as the store is
 * processing here.
 */
public final class CitizenDeathSystem extends DeathSystems.OnDeathSystem {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final WorldRuntimes runtimes;

    public CitizenDeathSystem(WorldRuntimes runtimes) {
        this.runtimes = runtimes;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return HyColonyComponents.citizenTag();
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent death,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            World world = store.getExternalData().getWorld();
            WorldRuntime rt = runtimes.of(world);
            CitizenTag tag = store.getComponent(ref, HyColonyComponents.citizenTag());
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (rt == null || !rt.enabled() || tag == null || t == null) {
                return;
            }
            Vector3d p = t.getPosition();
            Vec3 at = new Vec3(p.x, p.y, p.z);
            DeathCause cause = DeathCauses.of(
                    death.getDeathCause(),
                    death.getDeathInfo(),
                    store,
                    killer -> rt.manager()
                            .byId(killer.colonyId())
                            .flatMap(c -> c.citizens().get(killer.citizenId()))
                            .map(CitizenData::name));
            rt.bodies().refs().untrack(ref);
            int colonyId = tag.colonyId();
            int citizenId = tag.citizenId();
            world.execute(() -> died(rt, colonyId, citizenId, at, cause));
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony citizen death failed");
        }
    }

    private static void died(WorldRuntime rt, int colonyId, int citizenId, Vec3 at, DeathCause cause) {
        try {
            if (rt.enabled()) {
                rt.manager().byId(colonyId).ifPresent(c -> CitizenDeath.die(c, citizenId, at, cause));
            }
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony citizen death failed");
        }
    }
}
