package dev.hycolony.plugin.npc.motion;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.movement.NavState;
import com.hypixel.hytale.server.npc.role.Role;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.plugin.npc.BodyTeleport;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.npc.MoveTarget;
import javax.annotation.Nullable;

/**
 * A citizen body's walk and climb, kept in its {@link MoveTarget}: where it walks (read by SensorHyColonyTarget), the
 * climb under way ({@link CitizenClimbSystem}), and how the walk stands. World thread only.
 */
public final class BodyWalks {
    /** World ticks after a walk starts during which a stale AT_GOAL etc. is ignored. */
    private static final long FRESH_MOVE_TICKS = 10;

    private final World world;

    public BodyWalks(World world) {
        this.world = world;
    }

    /** Walks the body to {@code target}, a climb under way or held ended. */
    public void walkTo(Ref<EntityStore> ref, Vec3 target) {
        MoveTarget mt = walk(ref);
        endClimb(mt);
        mt.target.set(target.x(), target.y(), target.z());
        mt.active = true;
        mt.sinceTick = world.getTick();
    }

    /**
     * Climbs the body straight up or down to {@code to} ({@link CitizenClimbSystem}), from where its feet are, its
     * walk stopped; nothing for a body without a position.
     */
    public void climbTo(Ref<EntityStore> ref, Vec3 to) {
        TransformComponent t = store().getComponent(ref, TransformComponent.getComponentType());
        if (t == null) {
            return;
        }
        MoveTarget mt = walk(ref);
        endClimb(mt);
        mt.active = false;
        mt.climbing = true;
        mt.climbTo.set(to.x(), to.y(), to.z());
        mt.climbY = t.getPosition().y;
        mt.climbSecondsLeft = CitizenClimbSystem.allowedSeconds(mt.climbY, to.y());
    }

    /**
     * Stops the body where it stands: its walk, and a climb under way or held (any other order on the body, a
     * teleport, a bed, a seat, takes over from the climb).
     */
    public void stop(Ref<EntityStore> ref) {
        MoveTarget mt = store().getComponent(ref, HyColonyComponents.moveTarget());
        if (mt != null) {
            mt.active = false;
            endClimb(mt);
        }
    }

    /**
     * How the walk or climb stands: MOVING while climbing, then ARRIVED once, or BLOCKED once if its time ran out;
     * else the nav's own state. A walk that has ended is IDLE.
     */
    public NavStatus status(Ref<EntityStore> ref) {
        @Nullable MoveTarget mt = store().getComponent(ref, HyColonyComponents.moveTarget());
        if (mt == null) {
            return NavStatus.IDLE;
        }
        @Nullable NavStatus climb = climbStatus(mt);
        if (climb != null) {
            return climb;
        }
        if (!mt.active) {
            return NavStatus.IDLE;
        }
        // Within its first ticks, the nav state still describes the previous goal.
        return world.getTick() - mt.sinceTick < FRESH_MOVE_TICKS ? NavStatus.MOVING : navStatus(ref, mt);
    }

    /** The climb's status, read once at its end; null when no climb is under way or ending. */
    private static @Nullable NavStatus climbStatus(MoveTarget mt) {
        if (mt.climbing) {
            return NavStatus.MOVING;
        }
        if (mt.climbArrived || mt.climbTimedOut) {
            NavStatus end = mt.climbArrived ? NavStatus.ARRIVED : NavStatus.BLOCKED;
            mt.climbArrived = false;
            mt.climbTimedOut = false;
            return end;
        }
        return null;
    }

    /** No climb under way nor held, and no climb end left to read. */
    private static void endClimb(MoveTarget mt) {
        mt.climbing = false;
        mt.climbHold = false;
        mt.climbArrived = false;
        mt.climbTimedOut = false;
    }

    /** The nav's own state of the walk under way; a walk that ends is no longer active. */
    private NavStatus navStatus(Ref<EntityStore> ref, MoveTarget mt) {
        @Nullable Role role = BodyTeleport.role(store(), ref);
        if (role == null) {
            return NavStatus.FAILED; // no longer an NPC: nothing will move it
        }
        NavState state = role.getActiveMotionController().getNavState();
        // Every NavState: INIT ("doing nothing"), PROGRESSING and DEFER may last forever (e.g. a Seek goal more than
        // 1 block above the feet is never AT_GOAL): the core's stuck handler watches the position, not this.
        NavStatus status = switch (state) {
            case AT_GOAL -> NavStatus.ARRIVED;
            case BLOCKED -> NavStatus.BLOCKED;
            case ABORTED -> NavStatus.FAILED;
            case INIT, PROGRESSING, DEFER -> NavStatus.MOVING;
        };
        if (status != NavStatus.MOVING) {
            mt.active = false;
        }
        return status;
    }

    /** The body's walk state, added when it has none. */
    private MoveTarget walk(Ref<EntityStore> ref) {
        @Nullable MoveTarget mt = store().getComponent(ref, HyColonyComponents.moveTarget());
        if (mt == null) {
            mt = new MoveTarget();
            store().addComponent(ref, HyColonyComponents.moveTarget(), mt);
        }
        return mt;
    }

    private Store<EntityStore> store() {
        return world.getEntityStore().getStore();
    }
}
