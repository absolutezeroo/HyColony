package dev.hycolony.plugin.npc.motion;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerDive;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerWalk;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.MovementStatesSystem;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.npc.MoveTarget;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Lets a citizen swim at the surface of water, as MC citizens float and swim (MinecoloniesAdvancedPathNavigate.java
 * l. 156-157 setCanFloat, canSwim; AbstractEntityCitizen.java l. 348-349). Its role has Hytale's Walk and Dive
 * controllers, and nothing in Hytale switches them in water (plugin-b-api.md 52): Walk becomes Dive once the water
 * reaches the body's eyes and it cannot walk out; Dive becomes Walk once it can: the body has a floor within a block
 * under its feet, or a bank ahead
 * at most {@link #BANK_HEIGHT} blocks above the floor of the water (Walk sinks, then climbs from there: the role's
 * MaxClimbHeight), its head out of the water once standing there; without a floor within {@link #FLOOR_SCAN} blocks it
 * keeps swimming. At least {@link #SWITCH_GAP_TICKS} pass between two switches, so a body at the edge does not flip
 * back and forth.
 *
 * <p>Deviation from MC: MC's path weighs water (PathingOptions swimCostEnter 24, swimCost 4, divingCost 4) and never
 * jumps from a swimming node (AbstractPathJob, canJump), so a citizen leaves water only by a bank level with it;
 * Hytale's A* weighs distance only (plugin-b-api.md 52), and a swimmer here climbs a bank up to {@link #BANK_HEIGHT}:
 * to take up with the ported pathfinding. MC's EntityAIFloat (head under water with no air above: escape path, nav
 * paused 300 ticks) is not ported: Dive keeps the head out, and a body held under a ceiling (a pier, ice) is left to
 * the stuck handler.
 *
 * <p>Deviation from MC (Hytale world): MC citizens never take a swim pose (AbstractFastMinecoloniesEntity
 * updateSwimming is a no-op; they float upright) → Hytale's Dive sets the swimming state, and the client plays the
 * Player model's Swim animations (Server/Models/Human/Player.json).
 *
 * <p>Deviation from MC (Hytale world): MC citizens move twice as fast in water, wading or swimming
 * (CITIZEN_SWIM_BONUS 2.0, AbstractEntityCitizen) → Hytale's water slows a walker to 0.6, lifts a swimmer at 2.5 and
 * sinks it at 1.35 blocks per second (Server/Item/Block/FluidFX/Water.json HorizontalSpeedMultiplier, SwimUpSpeed,
 * SinkSpeed): the role's Dive has MaxSwimSpeed 1.8 (0.6 times the Walk's 3), MaxDiveSpeed 2.5 and MaxSinkSpeed 1.35,
 * to tune in game. Its HyColonySeek relaxes Wade and Breathe, so a path may cross water.
 */
public final class CitizenSwimSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Least world ticks (30 per second by default) between two switches of a body's controller. */
    static final int SWITCH_GAP_TICKS = 10;
    /** The bank's highest step the Walk controller climbs from the floor, in blocks (the role's MaxClimbHeight). */
    static final int BANK_HEIGHT = 3;
    /** Blocks below the feet the floor of the water is looked for. */
    static final int FLOOR_SCAN = 8;
    /** {@link #floorUnder}'s answer when no floor is found. */
    private static final int NO_FLOOR = Integer.MIN_VALUE;
    /** How far ahead of the feet the bank is looked for, in blocks: past the body's half-width. */
    private static final double AHEAD_BLOCKS = 0.8;
    /** Eye height, in blocks, when the body has no model (the Player model's is about 1.6). */
    private static final float DEFAULT_EYE_HEIGHT = 1.6f;

    private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.AFTER, MovementStatesSystem.class));
    private boolean failed;

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                HyColonyComponents.citizenTag(),
                HyColonyComponents.moveTarget(),
                TransformComponent.getComponentType());
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    /** Switches the body between walking and swimming when it enters deep water or reaches a floor or a bank. */
    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            switchIfDue(chunk, index, store, buffer);
        } catch (RuntimeException e) { // out of a TickingSystem, an exception would stop the world's thread
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyColony: citizen swim switch failed");
            failed = true;
        }
    }

    /**
     * Switches the body's controller when {@link #next} says so and its last switch is at least
     * {@link #SWITCH_GAP_TICKS} old, and notes when in its walk state.
     */
    private static void switchIfDue(
            ArchetypeChunk<EntityStore> chunk, int index, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        @Nullable ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        @Nullable NPCEntity npc = npcType == null ? null : chunk.getComponent(index, npcType);
        @Nullable MoveTarget walk = chunk.getComponent(index, HyColonyComponents.moveTarget());
        @Nullable TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        long now = store.getExternalData().getWorld().getTick();
        if (npc == null || walk == null || transform == null || now - walk.motionSwitchTick < SWITCH_GAP_TICKS) {
            return;
        }
        @Nullable Role role = npc.getRole();
        if (role == null) {
            return;
        }
        float eyes = eyeHeight(chunk, index, store);
        @Nullable String next = next(role.getActiveMotionController().getType(), store, transform, eyes);
        if (next != null && role.setActiveMotionController(chunk.getReferenceTo(index), npc, next, buffer)) {
            walk.motionSwitchTick = now;
        }
    }

    /**
     * The controller the body should switch to; null to keep the active one. Both switches read the same way out
     * ({@link #walkable}): a body that walks out of the water, its eyes still under while it climbs the bank, is not
     * sent back to swimming.
     */
    private static @Nullable String next(String active, Store<EntityStore> store, TransformComponent t, float eyes) {
        Vector3d p = t.getPosition();
        if (MotionControllerWalk.TYPE.equals(active)) {
            boolean eyesUnder = MotionCells.water(
                    store, (int) Math.floor(p.x), (int) Math.floor(p.y + eyes), (int) Math.floor(p.z));
            return eyesUnder && !walkable(store, t, eyes) ? MotionControllerDive.TYPE : null;
        }
        return MotionControllerDive.TYPE.equals(active) && walkable(store, t, eyes) ? MotionControllerWalk.TYPE : null;
    }

    /**
     * Whether the body can walk out from here: it has a floor within a block under its feet with its head out of the
     * water once standing on it, or a bank ahead it can climb from the floor of the water. Walk sinks and climbs from
     * the bottom, so the bank is measured from the floor it will stand on; without a floor within
     * {@link #FLOOR_SCAN} blocks, it cannot.
     */
    private static boolean walkable(Store<EntityStore> store, TransformComponent t, float eyes) {
        Vector3d p = t.getPosition();
        int x = (int) Math.floor(p.x);
        int z = (int) Math.floor(p.z);
        int floor = floorUnder(store, x, (int) Math.floor(p.y), z);
        if (floor == NO_FLOOR) {
            return false;
        }
        boolean standing = floor >= p.y - 1 && headOut(store, x, floor, z, eyes);
        return standing || bankAhead(store, t, floor, eyes);
    }

    /**
     * The feet height of a body standing on the first solid block at or below {@code y}, within {@link #FLOOR_SCAN}
     * blocks; {@link #NO_FLOOR} without one.
     */
    private static int floorUnder(Store<EntityStore> store, int x, int y, int z) {
        for (int below = y; below >= y - FLOOR_SCAN; below--) {
            if (MotionCells.solid(store, x, below, z)) {
                return below + 1;
            }
        }
        return NO_FLOOR;
    }

    /**
     * Whether a solid block ahead of the body, at most {@link #BANK_HEIGHT} above the floor it will sink to, has room
     * on top and leaves the head out of the water of a body standing there.
     */
    private static boolean bankAhead(Store<EntityStore> store, TransformComponent t, int floor, float eyes) {
        Vector3d p = t.getPosition();
        float heading = t.getRotation().yaw();
        int ax = (int) Math.floor(p.x + PhysicsMath.headingX(heading) * AHEAD_BLOCKS);
        int az = (int) Math.floor(p.z + PhysicsMath.headingZ(heading) * AHEAD_BLOCKS);
        for (int stand = floor + 1; stand <= floor + BANK_HEIGHT; stand++) {
            if (MotionCells.solid(store, ax, stand - 1, az)
                    && !MotionCells.solid(store, ax, stand, az)
                    && !MotionCells.solid(store, ax, stand + 1, az)
                    && headOut(store, ax, stand, az, eyes)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a body standing with its feet at {@code stand} has its eyes out of the water: no swim to switch to. */
    private static boolean headOut(Store<EntityStore> store, int x, int stand, int z, float eyes) {
        return !MotionCells.water(store, x, (int) Math.floor(stand + eyes), z);
    }

    /** The body's eye height above its feet, from its model; {@link #DEFAULT_EYE_HEIGHT} without one. */
    private static float eyeHeight(ArchetypeChunk<EntityStore> chunk, int index, Store<EntityStore> store) {
        @Nullable ModelComponent model = chunk.getComponent(index, ModelComponent.getComponentType());
        return model == null || model.getModel() == null
                ? DEFAULT_EYE_HEIGHT
                : model.getModel().getEyeHeight(chunk.getReferenceTo(index), store);
    }
}
