package dev.hycolony.plugin.npc.body;

import com.hypixel.hytale.builtin.mounts.BlockMountAPI;
import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.BlockMountType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.npc.CitizenBeds;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.npc.MoveTarget;
import java.util.logging.Level;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.jspecify.annotations.Nullable;

/**
 * Citizens sitting on Hytale seats (chairs, stools, benches) through the native seat mount, as {@link CitizenBeds}
 * lies them in beds (BlockMountAPI.mountOnBlock takes a block with {@code Seats} as a seat first,
 * sp4b-hytale-food § 6). The pose is MovementStates.sitting and the Player model's "Sit" animation on the Status slot
 * [in-game]. World thread only, outside a store's processing.
 */
public final class CitizenSeats {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The Player model's sitting animation (Server/Models/Human/Player.json AnimationSets). */
    private static final String SIT_ANIMATION = "Sit";

    private final World world;
    private boolean warned;

    public CitizenSeats(World world) {
        this.world = world;
    }

    /**
     * Stops the body's walk and mounts it on the seat block at {@code seat}; true once seated, also when it already
     * sits there; false when it sits elsewhere or lies, the chunk is not loaded, the block is no seat or all its
     * points are taken. mountOnBlock needs a CommandBuffer: through Store.forEachChunk, as CitizenBeds.
     */
    public boolean sitOn(Ref<EntityStore> ref, BlockPos seat) {
        if (!ref.isValid()) {
            return false;
        }
        Store<EntityStore> st = world.getEntityStore().getStore();
        MountedComponent mounted = st.getComponent(ref, MountedComponent.getComponentType());
        if (mounted != null) {
            return mounted.getBlockMountType() == BlockMountType.Seat && sitsOn(ref, seat);
        }
        MoveTarget walk = st.getComponent(ref, HyColonyComponents.moveTarget());
        if (walk != null) {
            walk.active = false;
        }
        Vector3i block = new Vector3i(seat.x(), seat.y(), seat.z());
        Vector3d hit = new Vector3d(seat.x() + 0.5, seat.y() + 0.5, seat.z() + 0.5);
        BlockMountAPI.BlockMountResult[] result = {BlockMountAPI.DidNotMount.CHUNK_NOT_FOUND};
        st.forEachChunk((chunk, commandBuffer) -> {
            result[0] = BlockMountAPI.mountOnBlock(ref, commandBuffer, block, hit);
            return true;
        });
        if (result[0] instanceof BlockMountAPI.Mounted m && m.component().getBlockMountType() == BlockMountType.Seat) {
            setSitting(st, ref, true);
            playStatus(st, ref, SIT_ANIMATION);
            return true;
        }
        LOG.at(warned ? Level.FINE : Level.WARNING).log("HyColony: a citizen could not sit at %s: %s", seat, result[0]);
        warned = true;
        return false;
    }

    /**
     * Whether every seat point of the block at {@code seat} holds someone (a bench seats two); a block that is no
     * loaded seat counts as taken, so no one is sent there.
     */
    public boolean isTaken(BlockPos seat) {
        BlockType type = blockType(seat);
        if (type == null || type.getSeats() == null) {
            return true;
        }
        BlockMountComponent mount = mountAt(seat);
        if (mount == null) {
            return false;
        }
        long seated = mount.getSeatedEntities().stream().filter(Ref::isValid).count();
        return seated >= type.getSeats().size();
    }

    /** The block type at {@code pos}; null when its chunk is not loaded. */
    private @Nullable BlockType blockType(BlockPos pos) {
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> section = cs.getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
        BlockSection blocks =
                section == null ? null : cs.getStore().getComponent(section, BlockSection.getComponentType());
        return blocks == null ? null : BlockType.getAssetMap().getAsset(blocks.get(pos.x(), pos.y(), pos.z()));
    }

    /** Gets the body off its seat (as DismountCommand) and ends its sitting pose; nothing when it does not sit. */
    public void standUp(Ref<EntityStore> ref) {
        if (!ref.isValid()) {
            return;
        }
        Store<EntityStore> st = world.getEntityStore().getStore();
        MountedComponent mounted = st.getComponent(ref, MountedComponent.getComponentType());
        if (mounted == null || mounted.getBlockMountType() != BlockMountType.Seat) {
            return;
        }
        st.tryRemoveComponent(ref, MountedComponent.getComponentType());
        setSitting(st, ref, false);
        playStatus(st, ref, null);
    }

    private boolean sitsOn(Ref<EntityStore> ref, BlockPos seat) {
        BlockMountComponent mount = mountAt(seat);
        return mount != null && mount.getSeatBlockBySeatedEntity(ref) != null;
    }

    private @Nullable BlockMountComponent mountAt(BlockPos seat) {
        Ref<ChunkStore> block = BlockModule.getBlockEntity(world, seat.x(), seat.y(), seat.z());
        return block == null
                ? null
                : world.getChunkStore().getStore().getComponent(block, BlockMountComponent.getComponentType());
    }

    private static void setSitting(Store<EntityStore> st, Ref<EntityStore> ref, boolean sitting) {
        MovementStatesComponent states = st.getComponent(ref, MovementStatesComponent.getComponentType());
        if (states != null) {
            states.getMovementStates().sitting = sitting;
        }
    }

    /** The NPC's Status animation, as {@link CitizenBeds} plays its lying one; null stops it. */
    private static void playStatus(Store<EntityStore> st, Ref<EntityStore> ref, @Nullable String animation) {
        ComponentType<EntityStore, NPCEntity> type = NPCEntity.getComponentType();
        NPCEntity npc = type == null ? null : st.getComponent(ref, type);
        if (npc != null) {
            npc.playAnimation(ref, AnimationSlot.Status, animation, st);
        }
    }
}
