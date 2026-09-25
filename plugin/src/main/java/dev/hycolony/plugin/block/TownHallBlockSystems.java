package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Action;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.HutPlacement;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Player-caused town hall place / break / use. Queries PlayerRef so only players trigger these. */
public final class TownHallBlockSystems {
    private TownHallBlockSystems() {}

    static BlockPos pos(Vector3i v) {
        return new BlockPos(v.x, v.y, v.z);
    }

    static PlayerRef player(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        return store.getComponent(ref, PlayerRef.getComponentType());
    }

    public static final class Place extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final WorldRuntimes runtimes;
        private final String hutItemId;

        public Place(WorldRuntimes runtimes, IdMap ids) {
            super(PlaceBlockEvent.class);
            this.runtimes = runtimes;
            this.hutItemId = ids.itemId("hut.townhall");
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull PlaceBlockEvent event) {
            if (event.getItemInHand() == null || !hutItemId.equals(event.getItemInHand().getItemId())) {
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
            HutPlacement result = m.checkHutPlacement(player.getUuid(), pos, BuildingTypes.TOWN_HALL.id());
            switch (result) {
                case HutPlacement.Denied denied -> {
                    event.setCancelled(true);
                    player.sendMessage(HytaleNotifier.toMessage(denied.reason()));
                }
                case HutPlacement.FoundNewColony f -> m.beginFoundation(player.getUuid(), player.getUsername(), pos, rotation);
                case HutPlacement.Allowed allowed -> m.placeHut(allowed.colony(), BuildingTypes.TOWN_HALL.id(), pos, rotation);
            }
        }
    }

    public static final class Break extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final WorldRuntimes runtimes;
        private final String hutBlockId;

        public Break(WorldRuntimes runtimes, IdMap ids) {
            super(BreakBlockEvent.class);
            this.runtimes = runtimes;
            this.hutBlockId = ids.blockId("hut.townhall");
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull BreakBlockEvent event) {
            if (!hutBlockId.equals(event.getBlockType().getId())) {
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
                player.sendMessage(HytaleNotifier.toMessage(Msg.of("hycolony.permission.denied", m.colonyAt(pos).get().name())));
                return;
            }
            m.onHutRemoved(pos);
        }
    }

    public static final class Use extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
        private final WorldRuntimes runtimes;
        private final String hutBlockId;

        public Use(WorldRuntimes runtimes, IdMap ids) {
            super(UseBlockEvent.Pre.class);
            this.runtimes = runtimes;
            this.hutBlockId = ids.blockId("hut.townhall");
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull UseBlockEvent.Pre event) {
            if (!hutBlockId.equals(event.getBlockType().getId())) {
                return;
            }
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            PlayerRef player = player(index, chunk, store);
            if (rt == null || !rt.enabled() || player == null) {
                return;
            }
            event.setCancelled(true);
            rt.manager().openTownHall(player.getUuid(), pos(event.getTargetBlock()));
        }
    }
}
