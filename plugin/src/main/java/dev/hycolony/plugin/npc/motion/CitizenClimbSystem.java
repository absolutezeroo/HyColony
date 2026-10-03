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
 * 2026-10-03-hycolony-nage-echelles § 5). Each tick the climb advances {@link #CLIMB_BLOCKS_PER_SECOND} times the
 * tick's length on its own height ({@code MoveTarget.climbY}), at the end's x and z, and the body is set there by an
 * exact teleport after its own movement, which overrides the Walk controller's gravity; Hytale's climbing state is set
 * then (ClimbUp, ClimbDown), as Hytale clears it each tick. At the end the body is held there until its next order (our
 * choice: the gravity would drop it before a next walk or climb starts); past its allowed time it is set at the end, a
 * timed-out climb.
 *
 * <p>Deviation from MC (Hytale world): Minecraft's ladder physics, and the crouched way down (setShiftKeyDown,
 * setYya -0.5) → a straight move at a Hytale player's ladder pace (Server/Entity/MovementConfig/Default.json ClimbSpeed
 * 0.035, whose unit is unverified: {@link #CLIMB_BLOCKS_PER_SECOND} is a placeholder tuned in game) with the
 * ClimbUp and ClimbDown animations.
 *
 * <p>Deviation from MC: citizens do not take ladders yet (MC's path job always may, PathfindingUtils.isLadder): no
 * path of ours goes through one until the pathfinding is ported; until then only the selftest climbs.
 */
public final class CitizenClimbSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Blocks a citizen climbs per second (of the world's time, whatever its tick rate). */
    static final double CLIMB_BLOCKS_PER_SECOND = 2.0;
    /** Seconds a climb may last beyond its planned time before the body is set at its end. */
    static final float MARGIN_SECONDS = 2f;

    private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.AFTER, MovementStatesSystem.class));
    /** Where the body is set this tick; reused, as the system runs on the world thread alone. */
    private final Vector3d step = new Vector3d();

    private boolean failed;

    /** Seconds a climb from {@code fromY} to {@code toY} may last: its planned time plus {@link #MARGIN_SECONDS}. */
    public static float allowedSeconds(double fromY, double toY) {
        return (float) (Math.abs(toY - fromY) / CLIMB_BLOCKS_PER_SECOND) + MARGIN_SECONDS;
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

    /**
     * One step of the climb under way, or the hold at its end; the climb's end reads ARRIVED in the walk status, or
     * BLOCKED when its time ran out.
     */
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
            if (walk == null || states == null || transform == null || !(walk.climbing || walk.climbHold)) {
                return;
            }
            if (walk.climbing) {
                advance(walk, dt);
                states.getMovementStates().climbing = walk.climbing;
            }
            step.set(walk.climbTo.x, walk.climbY, walk.climbTo.z);
            buffer.putComponent(
                    chunk.getReferenceTo(index),
                    Teleport.getComponentType(),
                    Teleport.createExact(step, transform.getRotation()));
        } catch (RuntimeException e) { // out of a TickingSystem, an exception would stop the world's thread
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyColony: citizen climb failed");
            failed = true;
        }
    }

    /**
     * Moves the climb's height {@link #CLIMB_BLOCKS_PER_SECOND} times {@code dt} towards its end; at the end, or once
     * its time is up, the climb ends there and the body is held (arrived, or timed out).
     */
    private static void advance(MoveTarget walk, float dt) {
        double left = walk.climbTo.y - walk.climbY;
        double reach = CLIMB_BLOCKS_PER_SECOND * dt;
        walk.climbSecondsLeft -= dt;
        boolean arrived = Math.abs(left) <= reach;
        if (arrived || walk.climbSecondsLeft <= 0) {
            walk.climbY = walk.climbTo.y;
            walk.climbing = false;
            walk.climbHold = true;
            walk.climbArrived = arrived;
            walk.climbTimedOut = !arrived;
            return;
        }
        walk.climbY += Math.signum(left) * reach;
    }
}
