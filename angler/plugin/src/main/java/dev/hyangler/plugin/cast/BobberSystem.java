package dev.hyangler.plugin.cast;

import com.hypixel.hytale.builtin.beam.BeamSystems;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.modules.projectile.system.StandardPhysicsTickSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * HyAngler's bobbers (spec § 7.2, § 7.3): pinned where they first touch water and bobbed gently (Cozy Tales' idea,
 * fishing-hytale.md § 1.8; left to Hytale's physics they sink, § 6); a player's one drives its cast's core session at
 * 20 ticks a second (CastTicks); every one lays its line each world tick; an ended one stays FINISH_TICKS for its end
 * animation, then goes (its line, and the camera's release, with it: BobberRemoval).
 */
final class BobberSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** How deep the bobber dips while a fish bites, in blocks. */
    private static final double BITE_DIP = 0.25;
    /** The gentle bob's height in blocks, and its speed in radians a world tick. */
    private static final double BOB_HEIGHT = 0.04;

    private static final double BOB_SPEED = 0.15;

    private final Set<Dependency<EntityStore>> dependencies = Set.of(
            new SystemDependency<>(Order.AFTER, StandardPhysicsTickSystem.class),
            new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class),
            // the line's beam changes go out the same tick as its carriers' moves
            new SystemDependency<>(Order.BEFORE, BeamSystems.Tracker.class),
            // a snapped line's beam removal reaches viewers that still see it (queueRemove throws otherwise)
            new SystemDependency<>(Order.BEFORE, EntityTrackerSystems.ClearEntityViewers.class));
    private final Casts casts;
    private final CastTicks ticks;
    private final Landing landing;
    private final CastAnimations animations;
    private boolean failed;

    BobberSystem(Casts casts, CastTicks ticks, Landing landing, CastAnimations animations) {
        this.casts = casts;
        this.ticks = ticks;
        this.landing = landing;
        this.animations = animations;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                Bobber.type(), StandardPhysicsProvider.getComponentType(), TransformComponent.getComponentType());
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    /**
     * Snaps a line Landing marked broken; pins a bobber in water; steps an ended one's exit, or advances a running
     * one's cast; moves its line onto the rod tip and lays its carriers; removes a player's bobber whose cast is gone.
     * Never throws.
     */
    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            Bobber bobber = chunk.getComponent(index, Bobber.type());
            StandardPhysicsProvider physics = chunk.getComponent(index, StandardPhysicsProvider.getComponentType());
            TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
            if (bobber == null || physics == null || transform == null) {
                return;
            }
            bobber.ticks++;
            Ref<EntityStore> self = chunk.getReferenceTo(index);
            snapIfDue(bobber, self, buffer);
            pin(bobber, physics, transform);
            bobber.onGround = physics.isOnGround();
            if (!stays(bobber, self, transform.getPosition(), dt, buffer)) {
                return;
            }
            bob(bobber, transform);
            if (bobber.line != null) {
                bobber.line.lay(transform.getPosition(), bobber.taut, buffer);
            }
        } catch (RuntimeException e) { // out of a TickingSystem, an exception would stop the world's thread
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyAngler: bobber tick failed");
            failed = true;
        }
    }

    /** Snaps a broken line Landing marked: with this system's buffer, so its beams go out before the trackers run. */
    private static void snapIfDue(Bobber bobber, Ref<EntityStore> self, CommandBuffer<EntityStore> buffer) {
        if (bobber.snapDue && bobber.line != null) {
            bobber.snapDue = false;
            bobber.line.snap(self, buffer);
        }
    }

    /**
     * Steps an ended bobber's exit, or advances a running one's cast (an NPC's has none); false once the bobber goes:
     * its exit over, or a player's bobber whose cast ended elsewhere (a stale one).
     */
    private boolean stays(
            Bobber bobber, Ref<EntityStore> self, Vector3d at, float dt, CommandBuffer<EntityStore> buffer) {
        if (bobber.finishing > 0) {
            return exit(bobber, self, buffer);
        }
        ActiveCast cast = castOf(bobber, self);
        if (bobber.owner != null && cast == null) {
            buffer.tryRemoveEntity(self, RemoveReason.REMOVE);
            return false;
        }
        if (cast != null && gone(cast, buffer)) {
            cast.session.cancel(); // its player left, died or put the rod away: the bobber goes out
            landing.finish(cast, cast.session.end().orElseThrow(), Optional.empty(), buffer);
        } else if (cast != null) {
            ticks.advance(cast, bobber, at, dt, buffer);
        }
        return true;
    }

    /**
     * Whether the cast cannot go on: its player gone from this world (disconnected, moved to another world, unloaded),
     * dead (a dead player's entity stays, DeathSystems.CorpseRemoval spares players, with a DeathComponent), or no
     * longer holding its rod (another slot, the rod thrown away or moved in the inventory), read each world tick.
     */
    private static boolean gone(ActiveCast cast, CommandBuffer<EntityStore> buffer) {
        if (!cast.angler.isValid() || buffer.getComponent(cast.angler, DeathComponent.getComponentType()) != null) {
            return true;
        }
        ItemStack hand = InventoryComponent.getItemInHand(buffer, cast.angler);
        return hand == null || hand.isEmpty() || !cast.rod.itemId().equals(hand.getItemId());
    }

    /** The player's cast this bobber belongs to; null for an NPC's bobber, or when its cast is gone or newer. */
    private @Nullable ActiveCast castOf(Bobber bobber, Ref<EntityStore> self) {
        UUID owner = bobber.owner;
        ActiveCast cast = owner == null ? null : casts.running(owner);
        return cast != null && Objects.equals(cast.bobber, self) ? cast : null;
    }

    /** Steps an ended bobber's exit: a catch's lift after its strike, then the bobber goes; false once it goes. */
    private boolean exit(Bobber bobber, Ref<EntityStore> self, CommandBuffer<EntityStore> buffer) {
        if (--bobber.finishing <= 0) {
            buffer.tryRemoveEntity(self, RemoveReason.REMOVE);
            return false;
        }
        if (bobber.catchIn > 0 && --bobber.catchIn == 0) {
            animations.play(bobber, CastAnimations.CATCH, buffer);
        }
        return true;
    }

    /** Keeps a floating bobber at its surface with a gentle bob, dipped while a fish bites. */
    private static void bob(Bobber bobber, TransformComponent transform) {
        if (bobber.floating) {
            // the live position, no allocation per tick: the tracker sends it when it differs from the sent one
            Vector3d p = transform.getPosition();
            double dip = bobber.taut && bobber.finishing == 0 ? BITE_DIP : 0;
            p.y = bobber.surfaceY + BOB_HEIGHT * Math.sin(bobber.ticks * BOB_SPEED) - dip;
        }
    }

    /**
     * Stops the bobber's physics on its first tick in water and keeps it at that height from then on: the height where
     * it entered, near the surface (judged in game, 2026-10-04).
     */
    private static void pin(Bobber bobber, StandardPhysicsProvider physics, TransformComponent transform) {
        if (!bobber.floating && physics.isInFluid()) {
            physics.setState(StandardPhysicsProvider.STATE.INACTIVE); // velocity zeroed, position ours
            bobber.floating = true;
            bobber.surfaceY = transform.getPosition().y;
        }
    }
}
