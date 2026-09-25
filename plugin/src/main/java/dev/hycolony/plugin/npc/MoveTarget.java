package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/** Transient: where the core wants the citizen to walk. Read by SensorHyColonyTarget. */
public final class MoveTarget implements Component<EntityStore> {
    public final Vector3d target = new Vector3d();
    public boolean active;

    @Override
    public Component<EntityStore> clone() {
        MoveTarget copy = new MoveTarget();
        copy.target.set(target);
        copy.active = active;
        return copy;
    }
}
