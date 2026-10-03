package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * Transient walk state of a citizen: where the core wants it to walk (read by SensorHyColonyTarget), how its current
 * ascent was judged (CitizenMantleSystem), the climb under way (CitizenClimbSystem) and its last switch between
 * walking and swimming (CitizenSwimSystem).
 */
public final class MoveTarget implements Component<EntityStore> {
    public final Vector3d target = new Vector3d();
    public boolean active;
    /** World tick of the last moveTo. */
    public long sinceTick;
    /** Whether the ascent under way was judged, on its first tick (CitizenMantleSystem). */
    public boolean ascentJudged;
    /** Whether the ascent under way climbs a 3-block ledge, as judged on its first tick. */
    public boolean ledgeClimb;
    /** Whether the body climbs straight up or down to {@link #climbTo}. */
    public boolean climbing;
    /** Where the climb under way ends: the feet's position. */
    public final Vector3d climbTo = new Vector3d();
    /** How high the climb under way has brought the feet: it advances on its own, whatever the gravity did. */
    public double climbY;
    /** Seconds the climb under way may still last before the body is set at its end. */
    public float climbSecondsLeft;
    /** Whether the body is held at {@link #climbTo} after its climb, as on a ladder, until its next order. */
    public boolean climbHold;
    /** Whether a climb has arrived since the walk status was last read. */
    public boolean climbArrived;
    /** Whether a climb ran out of time since the walk status was last read: the body was set at its end. */
    public boolean climbTimedOut;
    /** World tick of the last switch between walking and swimming. */
    public long motionSwitchTick;

    @Override
    public Component<EntityStore> clone() {
        MoveTarget copy = new MoveTarget();
        copy.target.set(target);
        copy.active = active;
        copy.sinceTick = sinceTick;
        copy.ascentJudged = ascentJudged;
        copy.ledgeClimb = ledgeClimb;
        copy.climbing = climbing;
        copy.climbTo.set(climbTo);
        copy.climbY = climbY;
        copy.climbSecondsLeft = climbSecondsLeft;
        copy.climbHold = climbHold;
        copy.climbArrived = climbArrived;
        copy.climbTimedOut = climbTimedOut;
        copy.motionSwitchTick = motionSwitchTick;
        return copy;
    }
}
