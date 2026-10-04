package dev.hycolony.plugin.npc.hurt;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.citizen.hurt.CitizenHurt;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.npc.CitizenTag;
import dev.hycolony.plugin.npc.HyColonyComponents;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * A citizen suffocating in a block takes no damage and is moved out of it ({@link CitizenHurt#outOfWall}, MC
 * EntityCitizen.handleInWallDamage): Hytale's {@code Suffocation} (DamageSystems.CanBreathe out of a fluid) is MC's
 * IN_WALL. Drowning stays Hytale's. Deviation from MC (Hytale world): MC's first IN_WALL hit, at once → Hytale's
 * first Suffocation hit, once its Oxygen stat ran out (about 17 s, Server/Entity/Stats/Oxygen.json).
 */
public final class CitizenWallFilter extends DamageEventSystem {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final Set<Dependency<EntityStore>> DEPENDENCIES =
            Set.of(new SystemDependency<>(Order.AFTER, DamageSystems.FilterUnkillable.class));

    private final WorldRuntimes runtimes;
    private final CauseIndex suffocation = new CauseIndex("Suffocation");

    public CitizenWallFilter(WorldRuntimes runtimes) {
        this.runtimes = runtimes;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return HyColonyComponents.citizenTag();
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    /** After FilterUnkillable: a corpse suffocating during its death animation is no longer moved. */
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull Damage event) {
        try {
            if (event.getDamageCauseIndex() != suffocation.get()) {
                return;
            }
            event.setCancelled(true);
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            CitizenTag tag = store.getComponent(ref, HyColonyComponents.citizenTag());
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            if (tag != null && rt != null && rt.enabled()) {
                BodyId body = rt.bodies().refs().track(ref);
                int colonyId = tag.colonyId();
                // Waking a sleeper takes it out of its bed, a write the processing store refuses (CanBreathe invokes
                // the damage under its lock): from the world's task queue.
                store.getExternalData().getWorld().execute(() -> outOfWall(rt, colonyId, body));
            }
        } catch (RuntimeException e) {
            event.setCancelled(true); // CLAUDE.md § 4: a failed check cancels
            LOG.at(Level.SEVERE).withCause(e).log("HyColony citizen wall filter failed");
        }
    }

    private static void outOfWall(WorldRuntime rt, int colonyId, BodyId body) {
        try {
            if (rt.enabled()) {
                rt.manager().byId(colonyId).ifPresent(c -> CitizenHurt.outOfWall(c, body));
            }
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony citizen wall filter failed");
        }
    }
}
