package dev.hyangler.plugin.spike;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.modules.projectile.system.StandardPhysicsTickSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Throwaway (plan task 2): the spike's bobbers. A free one is left to Hytale's own physics (does a Standard projectile
 * of density 700 float, drift?); a frozen one is pinned where it first touched water and bobbed gently, the plan's
 * way (Cozy Tales' idea, fishing-hytale.md § 1.8). Either is removed after a minute.
 */
public final class SpikeBobberSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** A minute at Hytale's 30 ticks a second (TickingThread.java:17). */
    private static final int LIFETIME_TICKS = 30 * 60;

    private static @Nullable ComponentType<EntityStore, SpikeBobber> bobberType;

    private final Set<Dependency<EntityStore>> dependencies = Set.of(
            new SystemDependency<>(Order.AFTER, StandardPhysicsTickSystem.class),
            new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class));
    private boolean failed;

    /** Registers the bobber's component; call once, in setup(), before the system. */
    public static void registerComponent(ComponentRegistryProxy<EntityStore> registry) {
        bobberType = registry.registerComponent(SpikeBobber.class, SpikeBobber::new);
    }

    /** The bobber's component type; throws {@link IllegalStateException} before registerComponent. */
    static ComponentType<EntityStore, SpikeBobber> bobberType() {
        ComponentType<EntityStore, SpikeBobber> type = bobberType;
        if (type == null) {
            throw new IllegalStateException("SpikeBobber is not registered");
        }
        return type;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                bobberType(), StandardPhysicsProvider.getComponentType(), TransformComponent.getComponentType());
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    /** Pins a frozen bobber once it is in water, and removes any bobber past its minute; never throws. */
    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            SpikeBobber bobber = chunk.getComponent(index, bobberType());
            StandardPhysicsProvider physics = chunk.getComponent(index, StandardPhysicsProvider.getComponentType());
            TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
            if (bobber == null || physics == null || transform == null) {
                return;
            }
            bobber.ticks++;
            if (bobber.freeze) {
                pinOnWater(bobber, physics, transform);
            }
            if (bobber.ticks > LIFETIME_TICKS) {
                buffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE);
            }
        } catch (RuntimeException e) { // out of a TickingSystem, an exception would stop the world's thread
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyAngler spike: bobber tick failed");
            failed = true;
        }
    }

    /**
     * Stops the bobber's physics on its first tick in water and keeps it at that height, with a gentle bob: the height
     * where it entered is only near the surface (an estimate the in-game test judges).
     */
    private static void pinOnWater(SpikeBobber bobber, StandardPhysicsProvider physics, TransformComponent transform) {
        if (!bobber.floating && physics.isInFluid()) {
            physics.setState(StandardPhysicsProvider.STATE.INACTIVE); // velocity zeroed, position ours
            bobber.floating = true;
            bobber.surfaceY = transform.getPosition().y;
        }
        if (bobber.floating) {
            Vector3d p = transform.getPosition(); // the live position: no allocation per tick
            p.y = bobber.surfaceY + 0.04 * Math.sin(bobber.ticks * 0.15);
            transform.setPosition(p);
        }
    }
}
