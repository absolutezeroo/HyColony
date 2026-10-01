package dev.hycolony.plugin.npc;

import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.mounts.BlockMountAPI;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockMountType;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import java.util.logging.Level;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Citizens lying in Hytale beds through the native bed mount (BlockMountAPI.mountOnBlock, docs/research/plugin-b-api.md
 * § 41): it places and turns the body on the bed's sleeping point and takes that point, so no player lies there
 * meanwhile. The lying pose is MovementStates.sleeping, which a player's own client sets and the server only relays
 * (MovementStatesSystems): no server code sets it, so it is set here for the NPC. World thread only, outside a store's
 * processing; nothing here throws past a log line (CLAUDE.md § 4).
 */
public final class CitizenBeds {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final BodyTeleport teleporter;
    private boolean warned;

    public CitizenBeds(World world, BodyTeleport teleporter) {
        this.world = world;
        this.teleporter = teleporter;
    }

    /**
     * Stops the body's walk and mounts it on the bed whose base block is {@code bed}; false when it is already
     * mounted, the chunk is not loaded, the block is no bed or its sleeping point is taken. mountOnBlock needs a
     * CommandBuffer, which only Store.forEachChunk hands out of a system: called once, on the first chunk.
     */
    public boolean sleepIn(Ref<EntityStore> ref, BlockPos bed) {
        if (!ref.isValid()) {
            return false;
        }
        Store<EntityStore> st = world.getEntityStore().getStore();
        if (st.getComponent(ref, MountedComponent.getComponentType()) != null) {
            return false;
        }
        MoveTarget walk = st.getComponent(ref, HyColonyComponents.moveTarget());
        if (walk != null) {
            walk.active = false;
        }
        Vector3i block = new Vector3i(bed.x(), bed.y(), bed.z());
        Vector3d hit = new Vector3d(bed.x() + 0.5, bed.y() + 0.5, bed.z() + 0.5);
        BlockMountAPI.BlockMountResult[] result = {BlockMountAPI.DidNotMount.CHUNK_NOT_FOUND};
        st.forEachChunk((chunk, commandBuffer) -> {
            result[0] = BlockMountAPI.mountOnBlock(ref, commandBuffer, block, hit);
            return true;
        });
        if (result[0] instanceof BlockMountAPI.Mounted) {
            setSleeping(st, ref, true);
            return true;
        }
        LOG.at(warned ? Level.FINE : Level.WARNING).log(
                "HyColony: a citizen could not lie in the bed at %s: %s", bed, result[0]);
        warned = true;
        return false;
    }

    /** Whether the body is still mounted on a bed: false once a teleport, a broken bed or a night skip got it up. */
    public boolean isInBed(Ref<EntityStore> ref) {
        if (!ref.isValid()) {
            return false;
        }
        MountedComponent mount =
                world.getEntityStore().getStore().getComponent(ref, MountedComponent.getComponentType());
        return mount != null && mount.getBlockMountType() == BlockMountType.Bed;
    }

    /**
     * Gets the body off its bed (as DismountCommand), then to a free spot beside it. WakeUpOnDismountSystem gives any
     * entity leaving a bed a PlayerSomnolence, which a players' night skip would then act on: taken off again.
     */
    public void wakeUp(Ref<EntityStore> ref) {
        if (!ref.isValid()) {
            return;
        }
        Store<EntityStore> st = world.getEntityStore().getStore();
        setSleeping(st, ref, false); // also after a dismount without us, so it never walks about lying
        if (!isInBed(ref)) {
            return;
        }
        TransformComponent t = st.getComponent(ref, TransformComponent.getComponentType());
        st.tryRemoveComponent(ref, MountedComponent.getComponentType());
        world.execute(() -> {
            if (ref.isValid()) {
                world.getEntityStore().getStore().tryRemoveComponent(ref, PlayerSomnolence.getComponentType());
            }
        });
        if (t != null) {
            Vector3d at = t.getPosition();
            teleporter.teleport(ref, new Vec3(at.x, at.y, at.z));
        }
    }

    /** The lying pose clients draw (MovementStates.sleeping, sent to viewers when it changes); none without states. */
    private static void setSleeping(Store<EntityStore> st, Ref<EntityStore> ref, boolean sleeping) {
        MovementStatesComponent states = st.getComponent(ref, MovementStatesComponent.getComponentType());
        if (states != null) {
            states.getMovementStates().sleeping = sleeping;
        }
    }
}
