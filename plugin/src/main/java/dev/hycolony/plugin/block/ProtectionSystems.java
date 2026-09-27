package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * PLACE_BLOCKS / BREAK_BLOCKS inside colonies (spec § 3.2); using a block is {@link BlockUseProtectionSystem}. Hut
 * blocks are handled by HutBlockSystems.
 */
public final class ProtectionSystems {
    private ProtectionSystems() {}

    /** True (and the player is told) when the action must be refused. */
    private static boolean deny(Check check, Store<EntityStore> store, PlayerRef player, BlockPos pos, Action action) {
        WorldRuntime rt = check.runtimes().of(store.getExternalData().getWorld());
        if (rt == null || !rt.enabled() || player == null) {
            return false;
        }
        ColonyManager m = rt.manager();
        if (!m.protectionEnabled() || m.isAllowed(player.getUuid(), pos, action)) {
            return false;
        }
        check.refusals().tell(rt, player, m.colonyAt(pos).get().name());
        return true;
    }

    /** What a protection system checks with: the world runtimes and the shared denial messages. */
    public record Check(WorldRuntimes runtimes, ColonyRefusals refusals) {}

    public static final class Place extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final Check check;
        private final Set<String> hutItemIds;

        public Place(Check check) {
            super(PlaceBlockEvent.class);
            this.check = check;
            this.hutItemIds = HutBlockSystems.byItemId(check.runtimes().setup()).keySet();
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
                    check,
                    store,
                    HutBlockSystems.player(index, chunk, store),
                    HutBlockSystems.pos(event.getTargetBlock()),
                    Action.PLACE_BLOCKS)) {
                event.setCancelled(true);
            }
        }
    }

    public static final class Break extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final Check check;
        private final Set<String> hutBlockIds;

        public Break(Check check) {
            super(BreakBlockEvent.class);
            this.check = check;
            this.hutBlockIds =
                    HutBlockSystems.byBlockId(check.runtimes().setup()).keySet();
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
                    check,
                    store,
                    HutBlockSystems.player(index, chunk, store),
                    HutBlockSystems.pos(event.getTargetBlock()),
                    Action.BREAK_BLOCKS)) {
                event.setCancelled(true);
            }
        }
    }
}
