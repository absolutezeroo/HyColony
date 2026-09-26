package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** PLACE_BLOCKS / BREAK_BLOCKS / OPEN_CONTAINER inside colonies (spec § 3.2). Hut blocks are handled by HutBlockSystems. */
public final class ProtectionSystems {
    private ProtectionSystems() {}

    /** True (and the player is told) when the action must be refused. */
    private static boolean deny(
            WorldRuntimes runtimes, Store<EntityStore> store, PlayerRef player, BlockPos pos, Action action) {
        WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
        if (rt == null || !rt.enabled() || player == null) {
            return false;
        }
        ColonyManager m = rt.manager();
        if (!m.protectionEnabled() || m.isAllowed(player.getUuid(), pos, action)) {
            return false;
        }
        player.sendMessage(HytaleNotifier.toMessage(
                Msg.of("hycolony.permission.denied", m.colonyAt(pos).get().name())));
        return true;
    }

    public static final class Place extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final WorldRuntimes runtimes;
        private final Set<String> hutItemIds;

        public Place(WorldRuntimes runtimes, IdMap ids) {
            super(PlaceBlockEvent.class);
            this.runtimes = runtimes;
            this.hutItemIds = HutBlockSystems.byItemId(ids).keySet();
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
            if (event.getItemInHand() != null
                    && hutItemIds.contains(event.getItemInHand().getItemId())) {
                return;
            }
            if (deny(
                    runtimes,
                    store,
                    HutBlockSystems.player(index, chunk, store),
                    HutBlockSystems.pos(event.getTargetBlock()),
                    Action.PLACE_BLOCKS)) {
                event.setCancelled(true);
            }
        }
    }

    /** OPEN_CONTAINER: only for blocks that actually hold an item container (chests, barrels...). */
    public static final class Use extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
        private final WorldRuntimes runtimes;
        private final Set<String> hutBlockIds;

        public Use(WorldRuntimes runtimes, IdMap ids) {
            super(UseBlockEvent.Pre.class);
            this.runtimes = runtimes;
            this.hutBlockIds = HutBlockSystems.byBlockId(ids).keySet();
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
            if (hutBlockIds.contains(event.getBlockType().getId())) {
                return;
            }
            Vector3i target = event.getTargetBlock();
            World world = store.getExternalData().getWorld();
            if (BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, target.x, target.y, target.z)
                    == null) {
                return;
            }
            if (deny(
                    runtimes,
                    store,
                    HutBlockSystems.player(index, chunk, store),
                    HutBlockSystems.pos(target),
                    Action.OPEN_CONTAINER)) {
                event.setCancelled(true);
            }
        }
    }

    public static final class Break extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final WorldRuntimes runtimes;
        private final Set<String> hutBlockIds;

        public Break(WorldRuntimes runtimes, IdMap ids) {
            super(BreakBlockEvent.class);
            this.runtimes = runtimes;
            this.hutBlockIds = HutBlockSystems.byBlockId(ids).keySet();
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
            if (hutBlockIds.contains(event.getBlockType().getId())) {
                return;
            }
            if (deny(
                    runtimes,
                    store,
                    HutBlockSystems.player(index, chunk, store),
                    HutBlockSystems.pos(event.getTargetBlock()),
                    Action.BREAK_BLOCKS)) {
                event.setCancelled(true);
            }
        }
    }
}
