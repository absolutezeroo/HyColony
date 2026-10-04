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
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.DisplayNameSupport;
import com.hypixel.hytale.server.spawning.SpawnTestResult;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.plugin.item.HytaleStacks;
import dev.hycolony.plugin.npc.BodyTeleport;
import dev.hycolony.plugin.npc.CitizenBeds;
import dev.hycolony.plugin.npc.CitizenTag;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.npc.HyColonySeek;
import dev.hycolony.plugin.npc.MoveTarget;
import dev.hycolony.plugin.npc.body.BodyDefense;
import dev.hycolony.plugin.npc.body.BodyGestures;
import dev.hycolony.plugin.npc.body.BodyRefs;
import dev.hycolony.plugin.npc.body.BodySpeeds;
import dev.hycolony.plugin.npc.body.BodyThreats;
import dev.hycolony.plugin.npc.body.BodyVitals;
import dev.hycolony.plugin.npc.body.CitizenSpeed;
import dev.hycolony.plugin.npc.body.HytaleBodyHealth;
import dev.hycolony.plugin.npc.body.HytaleBodySeats;
import dev.hycolony.plugin.npc.motion.BodyWalks;
import dev.hycolony.plugin.npc.spawn.HostileGroup;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.joml.Vector3d;

/** CitizenBodies over Hytale NPCs. World thread only. */
public final class HytaleCitizenBodies implements CitizenBodies {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final String roleName;
    private final BodySpeeds speeds;
    private final BodyTeleport teleporter;
    private final CitizenBeds beds;
    private final BodyVitals vitals;
    private final HytaleBodyHealth health;
    private final HytaleBodySeats seats;
    private final BodyThreats threats;
    private final HytaleStacks stacks;
    private final BodyRefs refs = new BodyRefs();
    private final BodyWalks walks;
    private boolean speedWarned;

    /**
     * {@code stacks}: the core's stacks as Hytale's, for the armour a body wears; {@code hostile}: the creatures a body
     * avoids.
     */
    public HytaleCitizenBodies(
            World world, String roleName, CitizenSpeed speed, HytaleStacks stacks, HostileGroup hostile) {
        this.world = world;
        this.roleName = roleName;
        this.stacks = stacks;
        this.speeds = new BodySpeeds(speed);
        this.teleporter = new BodyTeleport(world);
        this.beds = new CitizenBeds(world, teleporter);
        this.vitals = new BodyVitals(world);
        this.health = new HytaleBodyHealth(world, refs::entity, vitals, speeds);
        this.threats = new BodyThreats(world, hostile);
        this.seats = new HytaleBodySeats(world, refs::entity);
        this.walks = new BodyWalks(world);
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

    /** The bodies' entities, tracked under their ids. */
    public BodyRefs refs() {
        return refs;
    }

    /**
     * Stops every body where it stands: its move target off, as {@link #lookAt} does, not Hytale's Frozen (saved with
     * the NPC); a walker walks again once the nav is no longer moving.
     */
    public void haltAll() {
        for (Ref<EntityStore> ref : refs.all()) {
            if (ref.isValid()) {
                walks.stop(ref);
            }
        }
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
        return Optional.of(refs.track(spawned[0]));
    }

    @Override
    public boolean isAlive(BodyId body) {
        return refs.ref(body) != null;
    }

    /** The NPC's Health stat ({@code DefaultEntityStatTypes.getHealth}) in percent of its range; 0 without one. */
    @Override
    public int healthPercent(BodyId body) {
        Ref<EntityStore> ref = refs.ref(body);
        return ref == null ? 0 : vitals.percent(ref);
    }

    /** Hytale's physical damage reduction of the body ({@link BodyDefense#percent}); 0 without a loaded body. */
    @Override
    public int defensePercent(BodyId body) {
        Ref<EntityStore> ref = refs.ref(body);
        return ref == null ? 0 : BodyDefense.percent(world, store(), ref);
    }

    @Override
    public Optional<Vec3> position(BodyId body) {
        Ref<EntityStore> ref = refs.ref(body);
        if (ref == null) {
            return Optional.empty();
        }
        TransformComponent t = store().getComponent(ref, TransformComponent.getComponentType());
        Vector3d p = t.getPosition();
        return Optional.of(new Vec3(p.x, p.y, p.z));
    }

    /** Through {@link BodyThreats}; empty for an unknown body. */
    @Override
    public Optional<Vec3> nearestThreat(BodyId body, double range) {
        Ref<EntityStore> ref = refs.ref(body);
        return ref == null ? Optional.empty() : threats.nearest(ref, range);
    }

    @Override
    public void moveTo(BodyId body, Vec3 target) {
        Ref<EntityStore> ref = refs.ref(body);
        if (ref != null) {
            walks.walkTo(ref, target);
        }
    }

    /**
     * Climbs the body straight up or down to {@code to} ({@link BodyWalks#climbTo}): the mechanics the ported
     * pathfinding will use for ladders (spec 2026-10-03-hycolony-nage-echelles § 5); until then, the selftest's.
     * Nothing for an unknown body.
     */
    public void climb(BodyId body, Vec3 to) {
        Ref<EntityStore> ref = refs.ref(body);
        if (ref != null) {
            walks.climbTo(ref, to);
        }
    }

    /** The waypoints its HyColonySeek still walks; empty for another body motion, or an unknown body. */
    @Override
    public List<Vec3> path(BodyId body) {
        Ref<EntityStore> ref = refs.ref(body);
        Role role = ref == null ? null : BodyTeleport.role(store(), ref);
        return role != null && role.getLastBodySteeringMotion() instanceof HyColonySeek seek
                ? seek.waypoints()
                : List.of();
    }

    @Override
    public NavStatus navStatus(BodyId body) {
        Ref<EntityStore> ref = refs.ref(body);
        return ref == null ? NavStatus.FAILED : walks.status(ref);
    }

    @Override
    public void setDisplayName(BodyId body, String name) {
        Ref<EntityStore> ref = refs.ref(body);
        if (ref != null) {
            DisplayNameSupport.setDisplayName(ref, name, store());
        }
    }

    /** Through {@link CitizenSpeed}'s speed effects; never throws (first failure WARNING, then FINE). */
    @Override
    public void setMovementSpeed(BodyId body, double factor) {
        Ref<EntityStore> ref = refs.ref(body);
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
        Ref<EntityStore> ref = refs.ref(body);
        if (ref != null) {
            refs.untrack(ref);
            // Deferred: despawn can run inside a store's processing (a system, an event handler), where
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
        Ref<EntityStore> ref = refs.ref(body);
        if (ref != null) {
            BodyGestures.hold(ref, item, store());
        }
    }

    /** See {@link BodyGestures#wear}. */
    @Override
    public void setArmor(BodyId body, List<Optional<ItemAmount>> pieces) {
        Ref<EntityStore> ref = refs.ref(body);
        if (ref != null) {
            BodyGestures.wear(ref, pieces, store(), stacks);
        }
    }

    /** See {@link BodyGestures#animate}. */
    @Override
    public void playAnimation(BodyId body, BodyAnimation animation) {
        Ref<EntityStore> ref = refs.ref(body);
        if (ref != null) {
            BodyGestures.animate(ref, animation, store());
        }
    }

    /** Ends the walk, then {@link BodyGestures#lookAt}. */
    @Override
    public void lookAt(BodyId body, Vec3 target) {
        Ref<EntityStore> ref = refs.ref(body);
        if (ref == null) {
            return;
        }
        walks.stop(ref);
        BodyGestures.lookAt(ref, target, store());
    }

    /** MC PathingStuckHandler.completeStuckAction (teleport near the goal), see {@link BodyTeleport}. */
    @Override
    public void teleport(BodyId body, Vec3 target) {
        Ref<EntityStore> ref = refs.ref(body);
        if (ref != null) {
            walks.stop(ref); // a climb under way or held would set the body back in its column
            teleporter.teleport(ref, target);
        }
    }

    /** Hytale's bed mount, see {@link CitizenBeds}; false for an unknown body. */
    @Override
    public boolean sleepIn(BodyId body, BlockPos bed) {
        Ref<EntityStore> ref = refs.ref(body);
        if (ref == null) {
            return false;
        }
        walks.stop(ref); // a climb under way or held would pull the body out of its bed
        return beds.sleepIn(ref, bed);
    }

    @Override
    public boolean isInBed(BodyId body) {
        Ref<EntityStore> ref = refs.ref(body);
        return ref != null && beds.isInBed(ref);
    }

    @Override
    public void wakeUp(BodyId body) {
        Ref<EntityStore> ref = refs.ref(body);
        if (ref != null) {
            beds.wakeUp(ref);
        }
    }
}
