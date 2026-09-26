package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Action;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.HutPlacement;
import dev.hycolony.core.construction.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Player-caused place / break / use of every hut block. Queries PlayerRef so only players trigger these. */
public final class HutBlockSystems {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    // ponytail: explicit list (BuildingRegistry has no listing); add a line per new hut type.
    private static final List<BuildingType> HUT_TYPES =
            List.of(BuildingTypes.TOWN_HALL, ConstructionBuildingTypes.BUILDER, ConstructionBuildingTypes.RESIDENCE);

    private HutBlockSystems() {}

    /** Hut block id -> building type, from the id-map. */
    public static Map<String, BuildingType> byBlockId(IdMap ids) {
        Map<String, BuildingType> map = new HashMap<>();
        HUT_TYPES.forEach(t -> map.put(ids.blockId(t.hutBlockKey()), t));
        return Map.copyOf(map);
    }

    /** Hut item id -> building type, from the id-map. */
    static Map<String, BuildingType> byItemId(IdMap ids) {
        Map<String, BuildingType> map = new HashMap<>();
        HUT_TYPES.forEach(t -> map.put(ids.itemId(t.hutBlockKey()), t));
        return Map.copyOf(map);
    }

    static BlockPos pos(Vector3i v) {
        return new BlockPos(v.x, v.y, v.z);
    }

    /** A handler must never let an exception escape: the event is cancelled (the world stays as it was) and logged. */
    static void failed(String what, Vector3i target, RuntimeException e) {
        LOG.at(Level.SEVERE).withCause(e).log("HyColony hut %s failed at %s", what, target);
    }

    static PlayerRef player(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        return store.getComponent(ref, PlayerRef.getComponentType());
    }

    public static final class Place extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final WorldRuntimes runtimes;
        private final Map<String, BuildingType> huts;

        public Place(WorldRuntimes runtimes, IdMap ids) {
            super(PlaceBlockEvent.class);
            this.runtimes = runtimes;
            this.huts = byItemId(ids);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer,
                @Nonnull PlaceBlockEvent event) {
            try {
                BuildingType type = event.getItemInHand() == null
                        ? null
                        : huts.get(event.getItemInHand().getItemId());
                if (type == null) {
                    return;
                }
                WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
                PlayerRef player = player(index, chunk, store);
                if (rt == null || !rt.enabled() || player == null) {
                    event.setCancelled(true);
                    return;
                }
                ColonyManager m = rt.manager();
                BlockPos pos = pos(event.getTargetBlock());
                int rotation = event.getRotation().yaw().ordinal();
                HutPlacement result = m.checkHutPlacement(player.getUuid(), pos, type.id());
                switch (result) {
                    case HutPlacement.Denied denied -> {
                        event.setCancelled(true);
                        player.sendMessage(HytaleNotifier.toMessage(denied.reason()));
                    }
                    // The core only returns this for a town hall.
                    case HutPlacement.FoundNewColony f ->
                        m.beginFoundation(player.getUuid(), player.getUsername(), pos, rotation);
                    case HutPlacement.Allowed allowed -> m.placeHut(allowed.colony(), type.id(), pos, rotation);
                }
            } catch (RuntimeException e) {
                event.setCancelled(true);
                failed("place", event.getTargetBlock(), e);
            }
        }
    }

    public static final class Break extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final WorldRuntimes runtimes;
        private final Map<String, BuildingType> huts;

        public Break(WorldRuntimes runtimes, IdMap ids) {
            super(BreakBlockEvent.class);
            this.runtimes = runtimes;
            this.huts = byBlockId(ids);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer,
                @Nonnull BreakBlockEvent event) {
            try {
                if (!huts.containsKey(event.getBlockType().getId())) {
                    return;
                }
                WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
                PlayerRef player = player(index, chunk, store);
                if (rt == null || !rt.enabled() || player == null) {
                    return;
                }
                ColonyManager m = rt.manager();
                BlockPos pos = pos(event.getTargetBlock());
                if (m.cancelFoundationAt(pos).isPresent()) {
                    return; // an unconfirmed town hall (anyone's): its foundation is cancelled, normal drop
                }
                if (m.colonyAt(pos).isPresent() && !m.isAllowed(player.getUuid(), pos, Action.BREAK_HUTS)) {
                    event.setCancelled(true);
                    player.sendMessage(HytaleNotifier.toMessage(Msg.of(
                            "hycolony.permission.denied", m.colonyAt(pos).get().name())));
                    return;
                }
                m.onHutRemoved(pos);
            } catch (RuntimeException e) {
                event.setCancelled(true);
                failed("break", event.getTargetBlock(), e);
            }
        }
    }

    /** Cancelled so the vanilla container window never opens: storage goes through our building page. */
    public static final class Use extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
        private final WorldRuntimes runtimes;
        private final Map<String, BuildingType> huts;

        public Use(WorldRuntimes runtimes, IdMap ids) {
            super(UseBlockEvent.Pre.class);
            this.runtimes = runtimes;
            this.huts = byBlockId(ids);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer,
                @Nonnull UseBlockEvent.Pre event) {
            try {
                BuildingType type = huts.get(event.getBlockType().getId());
                if (type == null) {
                    return;
                }
                event.setCancelled(true);
                WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
                PlayerRef player = player(index, chunk, store);
                if (rt == null || !rt.enabled() || player == null) {
                    return;
                }
                BlockPos pos = pos(event.getTargetBlock());
                if (type == BuildingTypes.TOWN_HALL) {
                    rt.manager().openTownHall(player.getUuid(), pos);
                } else {
                    rt.manager().openBuilding(player.getUuid(), pos);
                }
            } catch (RuntimeException e) {
                event.setCancelled(true);
                failed("use", event.getTargetBlock(), e);
            }
        }
    }
}
