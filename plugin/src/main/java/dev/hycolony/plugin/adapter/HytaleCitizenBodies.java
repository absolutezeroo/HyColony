package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.movement.NavState;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.DisplayNameSupport;
import com.hypixel.hytale.server.spawning.SpawnTestResult;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.plugin.npc.BodyTeleport;
import dev.hycolony.plugin.npc.CitizenBeds;
import dev.hycolony.plugin.npc.CitizenTag;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.npc.HyColonySeek;
import dev.hycolony.plugin.npc.MoveTarget;
import dev.hycolony.plugin.npc.body.BodyGestures;
import dev.hycolony.plugin.npc.body.BodySpeeds;
import dev.hycolony.plugin.npc.body.BodyVitals;
import dev.hycolony.plugin.npc.body.CitizenSpeed;
import dev.hycolony.plugin.npc.body.HytaleBodyHealth;
import dev.hycolony.plugin.npc.body.HytaleBodySeats;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/** CitizenBodies over Hytale NPCs. World thread only. */
public final class HytaleCitizenBodies implements CitizenBodies {
    /** World ticks after moveTo during which a stale AT_GOAL etc. is ignored. */
    private static final long FRESH_MOVE_TICKS = 10;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final String roleName;
    private final BodySpeeds speeds;
    private final BodyTeleport teleporter;
    private final CitizenBeds beds;
    private final BodyVitals vitals;
    private final HytaleBodyHealth health;
    private final HytaleBodySeats seats;
    private final Map<Long, Ref<EntityStore>> refs = new HashMap<>();
    private final IdentityHashMap<Ref<EntityStore>, Long> ids = new IdentityHashMap<>();
    private long nextId = 1;
    private boolean speedWarned;

    /** {@code coreTicks}: the core's clock, for how long a hurt body is remembered. */
    public HytaleCitizenBodies(World world, String roleName, CitizenSpeed speed, LongSupplier coreTicks) {
        this.world = world;
        this.roleName = roleName;
        this.speeds = new BodySpeeds(speed);
        this.teleporter = new BodyTeleport(world);
        this.beds = new CitizenBeds(world, teleporter);
        this.vitals = new BodyVitals(world, coreTicks);
        this.health = new HytaleBodyHealth(world, this::entity, vitals, speeds);
        this.seats = new HytaleBodySeats(world, this::entity);
    }

    /** The bodies' health port, on the same Health stats and speeds as these bodies. */
    public HytaleBodyHealth health() {
        return health;
    }

    /** The bodies' seats port. */
    public HytaleBodySeats seats() {
        return seats;
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
                        near.x(),
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
            // The reason (FAIL_NO_POSITION, FAIL_INVALID_POSITION...) is only known here. FINE: a refused column is a
            // normal step of the core's search (CitizenArrival); CitizenManager warns the players when a new citizen
            // finds no room at the town hall.
            LOG.at(Level.FINE).log("HyColony: cannot spawn a citizen in the column of %s: %s", near, result);
            return Optional.empty();
        }
        return Optional.of(track(spawned[0]));
    }

    @Override
    public boolean isAlive(BodyId body) {
        return ref(body) != null;
    }

    /** The NPC's Health stat ({@code DefaultEntityStatTypes.getHealth}) in percent of its range; 0 without one. */
    @Override
    public int healthPercent(BodyId body) {
        Ref<EntityStore> ref = ref(body);
        return ref == null ? 0 : vitals.percent(ref);
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
        Role role = ref == null ? null : BodyTeleport.role(store(), ref);
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
        Role role = BodyTeleport.role(store(), ref);
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
            speeds.setJobFactor(ref, factor, store());
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

    /** See {@link BodyGestures#hold}. */
    @Override
    public void setHeldItem(BodyId body, Optional<ItemKey> item) {
        Ref<EntityStore> ref = ref(body);
        if (ref != null) {
            BodyGestures.hold(ref, item, store());
        }
    }

    /** See {@link BodyGestures#animate}. */
    @Override
    public void playAnimation(BodyId body, BodyAnimation animation) {
        Ref<EntityStore> ref = ref(body);
        if (ref != null) {
            BodyGestures.animate(ref, animation, store());
        }
    }

    /** Ends the walk, then {@link BodyGestures#lookAt}. */
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
        BodyGestures.lookAt(ref, target, store());
    }

    /** MC PathingStuckHandler.completeStuckAction (teleport near the goal), see {@link BodyTeleport}. */
    @Override
    public void teleport(BodyId body, Vec3 target) {
        Ref<EntityStore> ref = ref(body);
        if (ref != null) {
            teleporter.teleport(ref, target);
        }
    }

    /** Hytale's bed mount, see {@link CitizenBeds}; false for an unknown body. */
    @Override
    public boolean sleepIn(BodyId body, BlockPos bed) {
        Ref<EntityStore> ref = ref(body);
        return ref != null && beds.sleepIn(ref, bed);
    }

    @Override
    public boolean isInBed(BodyId body) {
        Ref<EntityStore> ref = ref(body);
        return ref != null && beds.isInBed(ref);
    }

    @Override
    public void wakeUp(BodyId body) {
        Ref<EntityStore> ref = ref(body);
        if (ref != null) {
            beds.wakeUp(ref);
        }
    }
}
