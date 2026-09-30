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
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.HutPlacement;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.RuntimeSetup;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Player-caused place / break / use of every hut block. Queries PlayerRef so only players trigger these. */
public final class HutBlockSystems {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private HutBlockSystems() {}

    /** Hut block id -> building type, for every registered type (core and sub-plugins), from the id-map. */
    public static Map<String, BuildingType> byBlockId(RuntimeSetup setup) {
        Map<String, BuildingType> map = new HashMap<>();
        setup.buildings().all().forEach(t -> map.put(setup.ids().blockId(t.hutBlockKey()), t));
        return Map.copyOf(map);
    }

    /** Hut item id -> building type, for every registered type (core and sub-plugins), from the id-map. */
    static Map<String, BuildingType> byItemId(RuntimeSetup setup) {
        Map<String, BuildingType> map = new HashMap<>();
        setup.buildings().all().forEach(t -> map.put(setup.ids().itemId(t.hutBlockKey()), t));
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

        public Place(WorldRuntimes runtimes) {
            super(PlaceBlockEvent.class);
            this.runtimes = runtimes;
            this.huts = byItemId(runtimes.setup());
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
                int rotation = event.getRotation().yaw().getDegrees() / 90; // declared degrees, not the enum position
                HutPlacement result = m.huts().checkPlacement(player.getUuid(), pos, type.id());
                switch (result) {
                    case HutPlacement.Denied denied -> {
                        event.setCancelled(true);
                        player.sendMessage(HytaleNotifier.toMessage(denied.reason()));
                    }
                    // The core only returns this for a town hall.
                    case HutPlacement.FoundNewColony _ ->
                        m.foundation().begin(player.getUuid(), player.getUsername(), pos, rotation);
                    case HutPlacement.Allowed allowed ->
                        m.huts().place(allowed.colony(), type.id(), pos, rotation, player.getUuid());
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

        public Break(WorldRuntimes runtimes) {
            super(BreakBlockEvent.class);
            this.runtimes = runtimes;
            this.huts = byBlockId(runtimes.setup());
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
                if (!rt.manager().huts().breakBy(player.getUuid(), pos(event.getTargetBlock()))) {
                    event.setCancelled(true);
                }
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

        public Use(WorldRuntimes runtimes) {
            super(UseBlockEvent.Pre.class);
            this.runtimes = runtimes;
            this.huts = byBlockId(runtimes.setup());
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
                if (type.equals(BuildingTypes.TOWN_HALL)) {
                    rt.manager().windows().openTownHall(player.getUuid(), pos);
                } else {
                    rt.manager().windows().openBuilding(player.getUuid(), pos);
                }
            } catch (RuntimeException e) {
                event.setCancelled(true);
                failed("use", event.getTargetBlock(), e);
            }
        }
    }
}
