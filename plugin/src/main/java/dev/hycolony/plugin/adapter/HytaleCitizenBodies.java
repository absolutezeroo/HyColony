package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.NavState;
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
import dev.hycolony.plugin.npc.CitizenTag;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.npc.MoveTarget;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import org.joml.Vector3d;

/** CitizenBodies over Hytale NPCs. World thread only. */
public final class HytaleCitizenBodies implements CitizenBodies {
    /** World ticks after moveTo during which a stale AT_GOAL etc. is ignored. */
    private static final long FRESH_MOVE_TICKS = 10;

    private final World world;
    private final String roleName;
    private final Map<Long, Ref<EntityStore>> refs = new HashMap<>();
    private final Map<Ref<EntityStore>, Long> ids = new IdentityHashMap<>();
    private long nextId = 1;

    public HytaleCitizenBodies(World world, String roleName) {
        this.world = world;
        this.roleName = roleName;
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

    private Ref<EntityStore> ref(BodyId body) {
        Ref<EntityStore> ref = refs.get(body.value());
        return ref != null && ref.isValid() ? ref : null;
    }

    @Override
    public Optional<BodyId> spawn(WorldKey key, BlockPos near, int colonyId, int citizenId, String displayName) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore>[] spawned = new Ref[1];
        SpawnTestResult result = NPCPlugin.get().spawnNPCWithColumnProbe(store(), roleName, null, world,
                near.x() + 1, near.z(), near.y(), new Rotation3f(),
                (npc, ref, st) -> {
                    st.addComponent(ref, HyColonyComponents.citizenTag(), new CitizenTag(colonyId, citizenId));
                    st.addComponent(ref, HyColonyComponents.moveTarget(), new MoveTarget());
                    DisplayNameSupport.setDisplayName(ref, displayName, st);
                    spawned[0] = ref;
                });
        if (result != SpawnTestResult.TEST_OK || spawned[0] == null) {
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
        NPCEntity npc = store().getComponent(ref, NPCEntity.getComponentType());
        NavState state = npc.getRole().getActiveMotionController().getNavState();
        NavStatus status = switch (state) {
            case AT_GOAL -> NavStatus.ARRIVED;
            case BLOCKED -> NavStatus.BLOCKED;
            case ABORTED -> NavStatus.FAILED;
            default -> NavStatus.MOVING;
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

    /** No-op for now: held items arrive with the plan B adapters. */
    @Override
    public void setHeldItem(BodyId body, Optional<ItemKey> item) {}

    /** No-op for now: animations arrive with the plan B adapters. */
    @Override
    public void playAnimation(BodyId body, BodyAnimation animation) {}
}
