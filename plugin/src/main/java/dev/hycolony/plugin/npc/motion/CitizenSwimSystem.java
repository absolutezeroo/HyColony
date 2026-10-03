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
 * Lets a citizen swim at the surface, as MC citizens float and swim (MinecoloniesAdvancedPathNavigate setCanFloat,
 * canSwim). Its role has Hytale's Walk and Dive controllers, and nothing in Hytale switches them in water
 * (plugin-b-api.md 52): Walk becomes Dive once the water reaches the body's eyes; Dive becomes Walk once the body has
 * a floor within a block under its feet, or a bank ahead it can climb, at most {@link #BANK_HEIGHT} blocks up (the
 * Walk controller's MaxClimbHeight). At least {@link #SWITCH_GAP_TICKS} pass between two switches, so a body at the
 * edge does not flip back and forth.
 */
public final class CitizenSwimSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    static final String WALK = "Walk";
    static final String DIVE = "Dive";
    /** Least ticks between two switches of a body's controller. */
    static final int SWITCH_GAP_TICKS = 10;
    /** The bank's highest step the Walk controller climbs, in blocks (the role's MaxClimbHeight). */
    static final int BANK_HEIGHT = 3;
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

    /** The controller the body should switch to; null to keep the active one. */
    private static @Nullable String next(String active, Store<EntityStore> store, TransformComponent t, float eyes) {
        Vector3d p = t.getPosition();
        int x = (int) Math.floor(p.x);
        int z = (int) Math.floor(p.z);
        if (WALK.equals(active)) {
            return MotionCells.fluid(store, x, (int) Math.floor(p.y + eyes), z) ? DIVE : null;
        }
        if (DIVE.equals(active)) {
            int below = (int) Math.floor(p.y - 1.0);
            boolean floor = MotionCells.solid(store, x, below, z) && headOut(store, x, below + 1, z, eyes);
            return floor || bankAhead(store, t, eyes) ? WALK : null;
        }
        return null;
    }

    /**
     * Whether a solid block ahead of the body, at most {@link #BANK_HEIGHT} above its feet, has room on top and
     * leaves the head out of the water of a body standing there.
     */
    private static boolean bankAhead(Store<EntityStore> store, TransformComponent t, float eyes) {
        Vector3d p = t.getPosition();
        float heading = t.getRotation().yaw();
        int ax = (int) Math.floor(p.x + PhysicsMath.headingX(heading) * AHEAD_BLOCKS);
        int az = (int) Math.floor(p.z + PhysicsMath.headingZ(heading) * AHEAD_BLOCKS);
        int feet = (int) Math.floor(p.y);
        for (int dy = 0; dy < BANK_HEIGHT; dy++) {
            int stand = feet + dy + 1;
            if (MotionCells.solid(store, ax, stand - 1, az)
                    && !MotionCells.solid(store, ax, stand, az)
                    && !MotionCells.solid(store, ax, stand + 1, az)
                    && headOut(store, ax, stand, az, eyes)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a body standing with its feet at {@code stand} has its eyes out of any fluid: no swim to switch to. */
    private static boolean headOut(Store<EntityStore> store, int x, int stand, int z, float eyes) {
        return !MotionCells.fluid(store, x, (int) Math.floor(stand + eyes), z);
    }

    /** The body's eye height above its feet, from its model; {@link #DEFAULT_EYE_HEIGHT} without one. */
    private static float eyeHeight(ArchetypeChunk<EntityStore> chunk, int index, Store<EntityStore> store) {
        @Nullable ModelComponent model = chunk.getComponent(index, ModelComponent.getComponentType());
        return model == null || model.getModel() == null
                ? DEFAULT_EYE_HEIGHT
                : model.getModel().getEyeHeight(chunk.getReferenceTo(index), store);
    }
}
