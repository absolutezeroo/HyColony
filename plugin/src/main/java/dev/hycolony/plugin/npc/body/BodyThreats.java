package dev.hycolony.plugin.npc.body;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.plugin.npc.spawn.HostileGroup;
import java.util.Optional;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * The hostile creatures near a citizen body (MC EntityAICitizenAvoidEntity.getClosestToAvoid: a Monster in the body's
 * bounding box inflated by the range, 3 up and down): an NPC of {@link HostileGroup}, not dying. Deviation from MC:
 * no line-of-sight check (MC getSensing().hasLineOfSight). World thread only.
 */
public final class BodyThreats {
    /** MC getClosestToAvoid: the box is inflated 3 blocks up and down. */
    private static final double HALF_HEIGHT = 3;

    private final World world;
    private final HostileGroup hostile;

    public BodyThreats(World world, HostileGroup hostile) {
        this.world = world;
        this.hostile = hostile;
    }

    /** Where the nearest hostile NPC within {@code range} of {@code ref} stands; empty without one. */
    public Optional<Vec3> nearest(Ref<EntityStore> ref, double range) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        if (t == null || npcType == null) {
            return Optional.empty();
        }
        Vector3d p = t.getPosition();
        Vec3 here = new Vec3(p.x, p.y, p.z);
        BoundingBox bounds = store.getComponent(ref, BoundingBox.getComponentType());
        Vector3d low = bounds == null ? new Vector3d() : bounds.getBoundingBox().getMin();
        Vector3d high =
                bounds == null ? new Vector3d() : bounds.getBoundingBox().getMax();
        Vec3 best = null;
        for (Ref<EntityStore> other : TargetUtil.getAllEntitiesInBox(
                new Vector3d(p.x + low.x - range, p.y + low.y - HALF_HEIGHT, p.z + low.z - range),
                new Vector3d(p.x + high.x + range, p.y + high.y + HALF_HEIGHT, p.z + high.z + range),
                store)) {
            Vec3 at = hostileAt(store, npcType, other);
            if (at != null && (best == null || here.distance(at) < here.distance(best))) {
                best = at;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Where {@code other} stands if it is a living hostile NPC; null else. */
    private @Nullable Vec3 hostileAt(
            Store<EntityStore> store, ComponentType<EntityStore, NPCEntity> npcType, Ref<EntityStore> other) {
        NPCEntity npc = other.isValid() ? store.getComponent(other, npcType) : null;
        if (npc == null
                || store.getComponent(other, DeathComponent.getComponentType()) != null
                || !hostile.contains(npc.getRoleIndex())) {
            return null;
        }
        TransformComponent t = store.getComponent(other, TransformComponent.getComponentType());
        return t == null ? null : new Vec3(t.getPosition().x, t.getPosition().y, t.getPosition().z);
    }
}
