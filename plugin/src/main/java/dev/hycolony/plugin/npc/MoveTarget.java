package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * Transient walk state of a citizen: where the core wants it to walk (read by SensorHyColonyTarget), and how its
 * current ascent was judged (CitizenMantleSystem).
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

    @Override
    public Component<EntityStore> clone() {
        MoveTarget copy = new MoveTarget();
        copy.target.set(target);
        copy.active = active;
        copy.sinceTick = sinceTick;
        copy.ascentJudged = ascentJudged;
        copy.ledgeClimb = ledgeClimb;
        return copy;
    }
}
