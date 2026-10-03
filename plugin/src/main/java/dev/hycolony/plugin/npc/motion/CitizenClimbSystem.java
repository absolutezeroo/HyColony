package dev.hycolony.plugin.npc.motion;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.systems.MovementStatesSystem;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.npc.MoveTarget;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Moves a citizen's body straight up or down a column, as a Hytale player climbs a ladder: the mechanics MC's ladders
 * (MinecoloniesAdvancedPathNavigate.handleLadders, doLadderMovement) will use once the pathfinding is ported (spec
 * 2026-10-03-hycolony-nage-echelles § 5). Each tick the body moves {@link #STEP_BLOCKS} closer to the climb's end,
 * centred in the column, by an exact teleport, with Hytale's climbing state (ClimbUp, ClimbDown); at the end, or once
 * its time is up, it is set there. Runs after the NPC's own movement states, which never set {@code climbing} for it.
 *
 * <p>Deviation from MC (Hytale world): Minecraft's ladder physics → a Hytale player's ladder pace
 * ({@code MovementConfig} ClimbSpeed, whose unit is unverified: {@link #CLIMB_BLOCKS_PER_SECOND} is tuned in game).
 */
public final class CitizenClimbSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Blocks a citizen climbs per second. */
    static final double CLIMB_BLOCKS_PER_SECOND = 2.0;
    /** Blocks a citizen climbs per game tick (20 per second). */
    static final double STEP_BLOCKS = CLIMB_BLOCKS_PER_SECOND / 20;
    /** Ticks a climb may last beyond its planned time before the body is set at its end. */
    static final int MARGIN_TICKS = 40;

    private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.AFTER, MovementStatesSystem.class));
    private boolean failed;

    /** The ticks a climb from {@code fromY} to {@code toY} may last: its planned time plus {@link #MARGIN_TICKS}. */
    public static int allowedTicks(double fromY, double toY) {
        return (int) Math.ceil(Math.abs(toY - fromY) / STEP_BLOCKS) + MARGIN_TICKS;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                HyColonyComponents.citizenTag(),
                HyColonyComponents.moveTarget(),
                MovementStatesComponent.getComponentType(),
                TransformComponent.getComponentType());
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    /** One step of the climb under way, if any; at its end the walk status reads ARRIVED. */
    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            MoveTarget walk = chunk.getComponent(index, HyColonyComponents.moveTarget());
            MovementStatesComponent states = chunk.getComponent(index, MovementStatesComponent.getComponentType());
            TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
            if (walk == null || states == null || transform == null || !walk.climbing) {
                return;
            }
            Vector3d p = transform.getPosition();
            double dy = walk.climbTo.y - p.y;
            boolean last = Math.abs(dy) <= STEP_BLOCKS || --walk.climbTicksLeft <= 0;
            Vector3d next = last
                    ? new Vector3d(walk.climbTo)
                    : new Vector3d(walk.climbTo.x, p.y + Math.signum(dy) * STEP_BLOCKS, walk.climbTo.z);
            buffer.putComponent(
                    chunk.getReferenceTo(index),
                    Teleport.getComponentType(),
                    Teleport.createExact(next, transform.getRotation()));
            states.getMovementStates().climbing = !last;
            if (last) {
                walk.climbing = false;
                walk.climbArrived = true;
            }
        } catch (RuntimeException e) { // out of a TickingSystem, an exception would stop the world's thread
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyColony: citizen climb failed");
            failed = true;
        }
    }
}
