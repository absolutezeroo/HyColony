package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * PLACE_BLOCKS / BREAK_BLOCKS inside colonies (spec § 3.2); using a block is {@link BlockUseProtectionSystem}. Hut
 * blocks are handled by HutBlockSystems.
 */
public final class ProtectionSystems {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private ProtectionSystems() {}

    /** True (and the player is told) when the action must be refused. */
    private static boolean deny(
            WorldRuntimes runtimes, Store<EntityStore> store, PlayerRef player, BlockPos pos, Action action) {
        WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
        if (rt == null || !rt.enabled() || player == null) {
            return false;
        }
        return rt.manager().protection().refuses(player.getUuid(), pos, action);
    }

    public static final class Place extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final WorldRuntimes runtimes;
        private final Set<String> hutItemIds;

        public Place(WorldRuntimes runtimes) {
            super(PlaceBlockEvent.class);
            this.runtimes = runtimes;
            this.hutItemIds = HutBlockSystems.byItemId(runtimes.setup()).keySet();
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
                if (event.getItemInHand() != null
                        && hutItemIds.contains(event.getItemInHand().getItemId())) {
                    return;
                }
                BlockPos pos = HutBlockSystems.pos(event.getTargetBlock());
                if (deny(runtimes, store, HutBlockSystems.player(index, chunk, store), pos, Action.PLACE_BLOCKS)) {
                    event.setCancelled(true);
                } else {
                    tellHuts(store, pos, event.getItemInHand());
                }
            } catch (RuntimeException e) {
                // A failing check must not let the placement through.
                event.setCancelled(true);
                LOG.at(Level.SEVERE).withCause(e).log("HyColony place check failed at %s", event.getTargetBlock());
            }
        }

        /**
         * The huts whose footprint holds {@code pos} hear of the block placed there (a dining hall takes a campfire or
         * a seat, HutActions.placedByPlayer); an item that places no block tells nothing.
         */
        private void tellHuts(Store<EntityStore> store, BlockPos pos, @Nullable ItemStack inHand) {
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            Item item = inHand == null ? null : Item.getAssetMap().getAsset(inHand.getItemId());
            if (rt != null && rt.enabled() && item != null && item.getBlockId() != null) {
                rt.manager().huts().placedByPlayer(pos, new BlockKey(item.getBlockId()));
            }
        }
    }

    public static final class Break extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final WorldRuntimes runtimes;
        private final Set<String> hutBlockIds;

        public Break(WorldRuntimes runtimes) {
            super(BreakBlockEvent.class);
            this.runtimes = runtimes;
            this.hutBlockIds = HutBlockSystems.byBlockId(runtimes.setup()).keySet();
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
            } catch (RuntimeException e) {
                // A failing check must not let the break through.
                event.setCancelled(true);
                LOG.at(Level.SEVERE).withCause(e).log("HyColony break check failed at %s", event.getTargetBlock());
            }
        }
    }
}
