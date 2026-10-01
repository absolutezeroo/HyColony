package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.MotionKind;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerBase;
import com.hypixel.hytale.server.npc.systems.MovementStatesSystem;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shows a citizen climbing a 3-block ledge as a player does, with Hytale's ledge climb ("mantling", the MantleUp
 * animation of the Player model). A player jumps a step of 1 or 2 blocks, and so does a citizen: Hytale's NPC Walk
 * controller jumps any step of 0.6 block or more it has room to jump (state {@code jumping}), and never sets the
 * mantling state, which only a player's client sets (builtin/mantling/MantlingPlugin). Runs after the NPC's own
 * movement states, which never touch {@code mantling}.
 *
 * <p>Deviation from MC: MC citizens step up at most 1.3 blocks without a ladder
 * ({@code PathingConstants.MAX_JUMP_HEIGHT}, {@code api/util/constant/PathingConstants.java:44}); steps of 2 and 3
 * blocks, and the ledge climb, are HyColony's, as a player climbs them (see the citizen role).
 */
public final class CitizenMantleSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** How far ahead of the feet the step is looked for, in blocks: past the body's half-width of the citizen. */
    private static final double AHEAD_BLOCKS = 0.8;

    private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.AFTER, MovementStatesSystem.class));
    private boolean failed;

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
     * Sets {@code mantling} while the citizen ascends a ledge of 3 blocks, and clears it otherwise. The ledge is judged
     * once, on the first tick of the ascent: 3 blocks high when the blocks ahead, one and two above the feet, are both
     * solid. Judged later, the body risen, a higher block further on would pass for this ledge.
     */
    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            @Nullable ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
            @Nullable NPCEntity npc = npcType == null ? null : chunk.getComponent(index, npcType);
            MovementStatesComponent states = chunk.getComponent(index, MovementStatesComponent.getComponentType());
            TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
            MoveTarget walk = chunk.getComponent(index, HyColonyComponents.moveTarget());
            if (states == null || transform == null || walk == null) {
                return;
            }
            update(states.getMovementStates(), ascending(npc) ? judge(walk, store, transform) : clear(walk));
        } catch (RuntimeException e) { // out of a TickingSystem, an exception would stop the world's thread
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyColony: citizen ledge climb failed");
            failed = true;
        }
    }

    /** Whether {@code npc}'s Walk controller climbs a step now. */
    private static boolean ascending(@Nullable NPCEntity npc) {
        return npc != null
                && npc.getRole() != null
                && npc.getRole().getActiveMotionController() instanceof MotionControllerBase m
                && m.getMotionKind() == MotionKind.ASCENDING;
    }

    /** On an ascent's first tick, judges its step and remembers it; the judgement of this ascent afterwards. */
    private static boolean judge(MoveTarget walk, Store<EntityStore> store, TransformComponent transform) {
        if (!walk.ascentJudged) {
            walk.ascentJudged = true;
            walk.ledgeClimb = threeBlockLedgeAhead(store, transform);
        }
        return walk.ledgeClimb;
    }

    /** Out of an ascent: the next one will be judged afresh. False: no ledge climb. */
    private static boolean clear(MoveTarget walk) {
        walk.ascentJudged = false;
        walk.ledgeClimb = false;
        return false;
    }

    /** Sets or clears {@code s.mantling}; a ledge climb is not a jump. */
    private static void update(MovementStates s, boolean mantling) {
        s.mantling = mantling;
        if (mantling) {
            // Each tick: Hytale may set it again mid-ascent (MotionControllerBase.updateMovementState).
            s.jumping = false;
        }
    }

    /** Whether the blocks ahead of the body, one and two above its feet, are solid: the ledge is 3 blocks high. */
    private static boolean threeBlockLedgeAhead(Store<EntityStore> store, TransformComponent transform) {
        double x = transform.getPosition().x;
        double y = transform.getPosition().y;
        double z = transform.getPosition().z;
        // The body's facing, not the climb's own direction (protected in MotionControllerWalk): a body still turning
        // as it starts the climb may judge the column beside the step.
        float heading = transform.getRotation().yaw();
        int ax = (int) Math.floor(x + PhysicsMath.headingX(heading) * AHEAD_BLOCKS);
        int az = (int) Math.floor(z + PhysicsMath.headingZ(heading) * AHEAD_BLOCKS);
        int feet = (int) Math.floor(y);
        return solid(store, ax, feet + 1, az) && solid(store, ax, feet + 2, az);
    }

    /** Whether the block at {@code x y z} is solid; false where its section is not loaded. */
    private static boolean solid(Store<EntityStore> store, int x, int y, int z) {
        ChunkStore chunks = store.getExternalData().getWorld().getChunkStore();
        @Nullable Ref<ChunkStore> sec = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        if (sec == null || !sec.isValid()) {
            return false;
        }
        @Nullable BlockSection blocks = chunks.getStore().getComponent(sec, BlockSection.getComponentType());
        if (blocks == null) {
            return false;
        }
        @Nullable BlockType type = BlockType.getAssetMap().getAsset(blocks.get(x, y, z));
        return type != null && type.getMaterial() == BlockMaterial.Solid;
    }
}
