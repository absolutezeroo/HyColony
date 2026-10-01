package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.role.Role;
import dev.hycolony.core.kernel.Vec3;
import java.util.logging.Level;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * Moves a citizen's body at once to a free spot near a target: MC PathingStuckHandler.completeStuckAction (teleport
 * near the goal), as the motion controller finds an accessible position within {@link #Y_RANGE} blocks up or down
 * (BodyMotionTeleport). World thread only.
 */
public final class BodyTeleport {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Blocks up or down the free spot may be from the target. */
    private static final double Y_RANGE = 10;

    private final World world;
    private boolean warned;

    public BodyTeleport(World world) {
        this.world = world;
    }

    /** The body's NPC role; null when the entity is not (or no longer) an NPC, or the NPC module is absent. */
    public static @Nullable Role role(Store<EntityStore> st, Ref<EntityStore> ref) {
        ComponentType<EntityStore, NPCEntity> type = NPCEntity.getComponentType();
        NPCEntity npc = type == null ? null : st.getComponent(ref, type);
        return npc == null ? null : npc.getRole();
    }

    /**
     * Adds a Teleport to the nearest accessible position to {@code target}, its walk stopped; nothing without one.
     * Deferred to world.execute for a caller in an event system, a RefSystem or an interaction, where the store is
     * processing and structural changes throw; the colony tick itself is not (Store.tickInternal takes no lock).
     */
    public void teleport(Ref<EntityStore> ref, Vec3 target) {
        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }
            Store<EntityStore> st = world.getEntityStore().getStore();
            Role role = role(st, ref);
            TransformComponent t = st.getComponent(ref, TransformComponent.getComponentType());
            BoundingBox box = st.getComponent(ref, BoundingBox.getComponentType());
            if (role == null || t == null) {
                return;
            }
            Vector3d to = new Vector3d(target.x(), target.y(), target.z());
            MotionController mc = role.getActiveMotionController();
            if (!mc.translateToAccessiblePosition(
                            to, box == null ? null : box.getBoundingBox(), to.y - Y_RANGE, to.y + Y_RANGE, st)
                    || !mc.isValidPosition(to, st)) {
                warnNoFreeSpot(to);
                return;
            }
            MoveTarget mt = st.getComponent(ref, HyColonyComponents.moveTarget());
            if (mt != null) {
                mt.active = false;
            }
            st.addComponent(ref, Teleport.getComponentType(), Teleport.createExact(to, t.getRotation()));
        });
    }

    /** CLAUDE.md § 4: the first failed teleport is a WARNING, the following ones FINE. */
    private void warnNoFreeSpot(Vector3d to) {
        LOG.at(warned ? Level.FINE : Level.WARNING).log("HyColony: no free spot to unstick a citizen near %s", to);
        warned = true;
    }
}
