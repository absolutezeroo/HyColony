package dev.hyangler.plugin.spike;

import com.hypixel.hytale.builtin.beam.BeamSystems;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
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
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.modules.projectile.system.StandardPhysicsTickSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Throwaway (plan task 2): the spike's bobbers, pinned where they first touch water and bobbed gently, the plan's way
 * (Cozy Tales' idea, fishing-hytale.md § 1.8; left to Hytale's physics they sink, § 6). A floating one plays its
 * scripted catch (SpikeCatchDemo); each one's line follows the angler's rod tip and, sagging, has its carriers laid
 * every tick (SpikeLine); any is removed after a minute.
 */
public final class SpikeBobberSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** A minute at Hytale's 30 ticks a second (TickingThread.java:17). */
    private static final int LIFETIME_TICKS = 30 * 60;

    private static @Nullable ComponentType<EntityStore, SpikeBobber> bobberType;

    private final Set<Dependency<EntityStore>> dependencies = Set.of(
            new SystemDependency<>(Order.AFTER, StandardPhysicsTickSystem.class),
            new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class),
            // the line's beam changes go out the same tick as its carriers' moves
            new SystemDependency<>(Order.BEFORE, BeamSystems.Tracker.class),
            // a snapped line's beam removal reaches viewers that still see it (queueRemove throws otherwise)
            new SystemDependency<>(Order.BEFORE, EntityTrackerSystems.ClearEntityViewers.class));
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

    /**
     * Pins a bobber once it is in water, steps its scripted catch, moves its line onto the rod tip and lays its
     * carriers (other entities' transforms), removes it past its minute if Hytale's own despawn has not (its line and
     * camera lock go with it, SpikeBobberRemoval); never throws.
     */
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
            pinOnWater(bobber, physics, transform);
            Ref<EntityStore> self = chunk.getReferenceTo(index);
            if (bobber.ticks > LIFETIME_TICKS) {
                // its line and its angler's camera go with it (SpikeBobberRemoval)
                buffer.tryRemoveEntity(self, RemoveReason.REMOVE);
                return;
            }
            if (bobber.floating) {
                SpikeCatchDemo.step(bobber, self, buffer);
            }
            layLine(bobber, self, transform, buffer);
        } catch (RuntimeException e) { // out of a TickingSystem, an exception would stop the world's thread
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyAngler spike: bobber tick failed");
            failed = true;
        }
    }

    /**
     * Samples the rod tip of the angler's pose, moves the line's end onto it, and lays a sagging line's carriers
     * between the bobber and it, taut while a fish pulls (SpikeCatchDemo).
     */
    private static void layLine(
            SpikeBobber bobber, Ref<EntityStore> self, TransformComponent at, CommandBuffer<EntityStore> buffer) {
        Ref<EntityStore> angler = bobber.angler;
        if (angler == null || !angler.isValid()) {
            return;
        }
        SpikeTipFollow.sample(bobber);
        SpikeTipFollow.follow(bobber, bobber.carriers.isEmpty() ? self : bobber.carriers.getLast(), buffer);
        TransformComponent anglerAt = buffer.getComponent(angler, TransformComponent.getComponentType());
        if (!bobber.carriers.isEmpty() && anglerAt != null) {
            SpikeLine.lay(bobber, at.getPosition(), anglerAt, buffer);
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
            // the live position, no allocation per tick: the tracker sends it when it differs from the sent one
            transform.getPosition().y = bobber.surfaceY + 0.04 * Math.sin(bobber.ticks * 0.15);
        }
    }
}
