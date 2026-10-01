package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.NavState;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.DisplayNameSupport;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import com.hypixel.hytale.server.spawning.SpawnTestResult;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.plugin.npc.CitizenSpeed;
import dev.hycolony.plugin.npc.CitizenTag;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.npc.HyColonySeek;
import dev.hycolony.plugin.npc.MoveTarget;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/** CitizenBodies over Hytale NPCs. World thread only. */
public final class HytaleCitizenBodies implements CitizenBodies {
    /** World ticks after moveTo during which a stale AT_GOAL etc. is ignored. */
    private static final long FRESH_MOVE_TICKS = 10;
    /** MC completeStuckAction searches 10 blocks around the goal. */
    private static final double TELEPORT_Y_RANGE = 10;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final String roleName;
    private final CitizenSpeed speed;
    private final Map<Long, Ref<EntityStore>> refs = new HashMap<>();
    private final IdentityHashMap<Ref<EntityStore>, Long> ids = new IdentityHashMap<>();
    private long nextId = 1;
    private boolean teleportWarned;
    private boolean speedWarned;
    private boolean spawnWarned;

    public HytaleCitizenBodies(World world, String roleName, CitizenSpeed speed) {
        this.world = world;
        this.roleName = roleName;
        this.speed = speed;
    }

    private Store<EntityStore> store() {
        return world.getEntityStore().getStore();
    }

    public BodyId track(Ref<EntityStore> ref) {
        Long existing = ids.get(ref);
        if (existing != null) {
            return new BodyId(existing);
        }
        long id = nextId++;
        refs.put(id, ref);
        ids.put(ref, id);
        return new BodyId(id);
    }

    public Optional<BodyId> untrack(Ref<EntityStore> ref) {
        Long id = ids.remove(ref);
        if (id == null) {
            return Optional.empty();
        }
        refs.remove(id);
        return Optional.of(new BodyId(id));
    }

    /** The loaded entity of {@code body}; empty once gone. */
    public Optional<Ref<EntityStore>> entity(BodyId body) {
        return Optional.ofNullable(ref(body));
    }

    /**
     * Stops every body where it stands: its move target off, as {@link #lookAt} does, not Hytale's Frozen (saved with
     * the NPC); a walker walks again once the nav is no longer moving.
     */
    public void haltAll() {
        for (Ref<EntityStore> ref : refs.values()) {
            if (ref.isValid()) {
                MoveTarget mt = store().getComponent(ref, HyColonyComponents.moveTarget());
                if (mt != null) {
                    mt.active = false;
                }
            }
        }
    }

    private @Nullable Ref<EntityStore> ref(BodyId body) {
        Ref<EntityStore> ref = refs.get(body.value());
        return ref != null && ref.isValid() ? ref : null;
    }

    @Override
    public Optional<BodyId> spawn(WorldKey key, BlockPos near, int colonyId, int citizenId, String displayName) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore>[] spawned = new Ref[1];
        SpawnTestResult result = NPCPlugin.get()
                .spawnNPCWithColumnProbe(
                        store(),
                        roleName,
                        null,
                        world,
                        near.x() + 1,
                        near.z(),
                        near.y(),
                        new Rotation3f(),
                        (npc, ref, st) -> {
                            st.addComponent(ref, HyColonyComponents.citizenTag(), new CitizenTag(colonyId, citizenId));
                            st.addComponent(ref, HyColonyComponents.moveTarget(), new MoveTarget());
                            DisplayNameSupport.setDisplayName(ref, displayName, st);
                            spawned[0] = ref;
                        });
        if (result != SpawnTestResult.TEST_OK || spawned[0] == null) {
            // CLAUDE.md § 4: the reason (FAIL_NO_POSITION, FAIL_INVALID_POSITION...) is only known here.
            LOG.at(spawnWarned ? Level.FINE : Level.WARNING).log(
                    "HyColony: cannot spawn a citizen near %s: %s", near, result);
            spawnWarned = true;
            return Optional.empty();
        }
        return Optional.of(track(spawned[0]));
    }

    @Override
    public boolean isAlive(BodyId body) {
        return ref(body) != null;
    }

    @Override
    public Optional<Vec3> position(BodyId body) {
        Ref<EntityStore> ref = ref(body);
        if (ref == null) {
            return Optional.empty();
        }
        TransformComponent t = store().getComponent(ref, TransformComponent.getComponentType());
        Vector3d p = t.getPosition();
        return Optional.of(new Vec3(p.x, p.y, p.z));
    }

    @Override
    public void moveTo(BodyId body, Vec3 target) {
        Ref<EntityStore> ref = ref(body);
        if (ref == null) {
            return;
        }
        MoveTarget mt = store().getComponent(ref, HyColonyComponents.moveTarget());
        if (mt == null) {
            mt = new MoveTarget();
            store().addComponent(ref, HyColonyComponents.moveTarget(), mt);
        }
        mt.target.set(target.x(), target.y(), target.z());
        mt.active = true;
        mt.sinceTick = world.getTick();
    }

    /** The waypoints its HyColonySeek still walks; empty for another body motion, or an unknown body. */
    @Override
    public List<Vec3> path(BodyId body) {
        Ref<EntityStore> ref = ref(body);
        Role role = ref == null ? null : role(store(), ref);
        return role != null && role.getLastBodySteeringMotion() instanceof HyColonySeek seek
                ? seek.waypoints()
                : List.of();
    }

    @Override
    public NavStatus navStatus(BodyId body) {
        Ref<EntityStore> ref = ref(body);
        if (ref == null) {
            return NavStatus.FAILED;
        }
        MoveTarget mt = store().getComponent(ref, HyColonyComponents.moveTarget());
        if (mt == null || !mt.active) {
            return NavStatus.IDLE;
        }
        if (world.getTick() - mt.sinceTick < FRESH_MOVE_TICKS) {
            return NavStatus.MOVING; // the nav state still describes the previous goal
        }
        Role role = role(store(), ref);
        if (role == null) {
            return NavStatus.FAILED; // no longer an NPC: nothing will move it
        }
        NavState state = role.getActiveMotionController().getNavState();
        // Every NavState: INIT ("doing nothing"), PROGRESSING and DEFER may last forever (e.g. a Seek goal more than
        // 1 block above the feet is never AT_GOAL): the core's stuck handler watches the position, not this.
        NavStatus status = switch (state) {
            case AT_GOAL -> NavStatus.ARRIVED;
            case BLOCKED -> NavStatus.BLOCKED;
            case ABORTED -> NavStatus.FAILED;
            case INIT, PROGRESSING, DEFER -> NavStatus.MOVING;
        };
        if (status != NavStatus.MOVING) {
            mt.active = false;
        }
        return status;
    }

    /** The body's NPC role; null when the entity is not (or no longer) an NPC, or the NPC module is absent. */
    private static @Nullable Role role(Store<EntityStore> st, Ref<EntityStore> ref) {
        ComponentType<EntityStore, NPCEntity> type = NPCEntity.getComponentType();
        NPCEntity npc = type == null ? null : st.getComponent(ref, type);
        return npc == null ? null : npc.getRole();
    }

    @Override
    public void setDisplayName(BodyId body, String name) {
        Ref<EntityStore> ref = ref(body);
        if (ref != null) {
            DisplayNameSupport.setDisplayName(ref, name, store());
        }
    }

    /** Through {@link CitizenSpeed}'s speed effects; never throws (first failure WARNING, then FINE). */
    @Override
    public void setMovementSpeed(BodyId body, double factor) {
        Ref<EntityStore> ref = ref(body);
        if (ref == null) {
            return;
        }
        try {
            speed.apply(ref, factor, store());
        } catch (RuntimeException e) {
            LOG.at(speedWarned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony: cannot set a citizen's speed");
            speedWarned = true;
        }
    }

    @Override
    public void despawn(BodyId body) {
        Ref<EntityStore> ref = ref(body);
        if (ref != null) {
            untrack(ref);
            // Deferred: despawn can run inside a store callback (RefSystem.onEntityAdded), where
            // removeEntity throws "Store is currently processing".
            world.execute(() -> {
                if (ref.isValid()) {
                    store().removeEntity(ref, RemoveReason.REMOVE);
                }
            });
        }
    }

    /** Hotbar slot 0 of the NPC (the role's default hotbar has 3 slots). */
    @Override
    public void setHeldItem(BodyId body, Optional<ItemKey> item) {
        Ref<EntityStore> ref = ref(body);
        if (ref == null) {
            return;
        }
        if (item.isEmpty()) {
            InventoryHelper.clearItemInHand(ref, (byte) 0, store());
        } else if (InventoryHelper.setHotbarItem(ref, item.get().id(), (byte) 0, store())) {
            InventoryHelper.setHotbarSlot(ref, (byte) 0, store());
        }
    }

    /**
     * Plays an item animation on the Action slot (the model has no work animations of its own).
     * Fallback if the client shows nothing: {@code "Default", "SwingRight"}.
     */
    @Override
    public void playAnimation(BodyId body, BodyAnimation animation) {
        Ref<EntityStore> ref = ref(body);
        if (ref == null) {
            return;
        }
        switch (animation) {
            case BUILD -> AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Block", "Build", store());
            case MINE -> AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Pickaxe", "Mine", store());
            // The hoe's own animation set (Server/Item/Animations/Hoe.json), as Hoe_Till plays it for a player
            case TILL -> AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Hoe", "Till", store());
            // The seeds' animation set (Template_Seeds PlayerAnimationsId), as Seed_Place plays it for a player
            case PLANT -> AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Item", "Interact", store());
        }
    }

    /**
     * MC WorkerUtil.faceBlock: body yaw toward the target (TransformComponent rotation) and head yaw and pitch from
     * the eyes (HeadRotation), with PhysicsMath's heading/pitch as NPC motions use. The walk is ended first: with no
     * Seek target the role has no body steering, so MotionControllerBase keeps the yaw it reads from the transform
     * each tick, and the head, without head steering, turns toward that body yaw.
     */
    @Override
    public void lookAt(BodyId body, Vec3 target) {
        Ref<EntityStore> ref = ref(body);
        if (ref == null) {
            return;
        }
        MoveTarget mt = store().getComponent(ref, HyColonyComponents.moveTarget());
        if (mt != null) {
            mt.active = false;
        }
        TransformComponent t = store().getComponent(ref, TransformComponent.getComponentType());
        HeadRotation head = store().getComponent(ref, HeadRotation.getComponentType());
        if (t == null) {
            return;
        }
        Vector3d p = t.getPosition();
        double dx = target.x() - p.x, dz = target.z() - p.z;
        double dy = target.y() - (p.y + ModelComponent.getEyeHeight(ref, store()));
        float yaw = PhysicsMath.normalizeTurnAngle(PhysicsMath.headingFromDirection(dx, dz));
        t.getRotation().setYaw(yaw);
        if (head != null) {
            head.getRotation().setYaw(yaw);
            head.getRotation().setPitch(PhysicsMath.pitchFromDirection(dx, dy, dz));
        }
    }

    /**
     * MC PathingStuckHandler.completeStuckAction (teleport near the goal): the motion controller moves the target to
     * an accessible position within 10 blocks up or down (as BodyMotionTeleport does), then a Teleport component is
     * added. Deferred to world.execute for a caller in an event system, a RefSystem or an interaction, where the store
     * is processing and structural changes throw; the colony tick itself is not (Store.tickInternal takes no lock).
     */
    @Override
    public void teleport(BodyId body, Vec3 target) {
        Ref<EntityStore> ref = ref(body);
        if (ref == null) {
            return;
        }
        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }
            Store<EntityStore> st = store();
            Role role = role(st, ref);
            TransformComponent t = st.getComponent(ref, TransformComponent.getComponentType());
            BoundingBox box = st.getComponent(ref, BoundingBox.getComponentType());
            if (role == null || t == null) {
                return;
            }
            Vector3d to = new Vector3d(target.x(), target.y(), target.z());
            MotionController mc = role.getActiveMotionController();
            if (!mc.translateToAccessiblePosition(
                            to,
                            box == null ? null : box.getBoundingBox(),
                            to.y - TELEPORT_Y_RANGE,
                            to.y + TELEPORT_Y_RANGE,
                            st)
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
        LOG.at(teleportWarned ? Level.FINE : Level.WARNING).log(
                "HyColony: no free spot to unstick a citizen near %s", to);
        teleportWarned = true;
    }
}
