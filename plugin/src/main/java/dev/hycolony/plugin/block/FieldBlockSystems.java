package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.action.FieldActions;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.Set;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * A player placing, breaking or using a field block (MC BlockScarecrow setPlacedBy, playerWillDestroy, use): the core
 * registers, removes or shows the field. Each runs after the colony protection, and does nothing for an event it
 * cancelled. A failure cancels the event and is logged.
 */
public final class FieldBlockSystems {
    private FieldBlockSystems() {}

    /** The actions of the world's colonies, or null when HyColony is off there. */
    private static @Nullable FieldActions actions(WorldRuntimes runtimes, Store<EntityStore> store) {
        WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
        return rt == null || !rt.enabled() ? null : new FieldActions(rt.manager());
    }

    public static final class Place extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final Set<Dependency<EntityStore>> dependencies =
                Set.of(new SystemDependency<>(Order.AFTER, ProtectionSystems.Place.class));
        private final WorldRuntimes runtimes;
        private final String fieldItem;

        public Place(WorldRuntimes runtimes, String fieldItem) {
            super(PlaceBlockEvent.class);
            this.runtimes = runtimes;
            this.fieldItem = fieldItem;
        }

        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
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
                if (event.isCancelled()
                        || event.getItemInHand() == null
                        || !fieldItem.equals(event.getItemInHand().getItemId())) {
                    return;
                }
                FieldActions fields = actions(runtimes, store);
                PlayerRef player = HutBlockSystems.player(index, chunk, store);
                if (fields != null && player != null) {
                    fields.placed(player.getUuid(), HutBlockSystems.pos(event.getTargetBlock()));
                }
            } catch (RuntimeException e) {
                event.setCancelled(true);
                HutBlockSystems.failed("field place", event.getTargetBlock(), e);
            }
        }
    }

    public static final class Break extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final Set<Dependency<EntityStore>> dependencies =
                Set.of(new SystemDependency<>(Order.AFTER, ProtectionSystems.Break.class));
        private final WorldRuntimes runtimes;
        private final String fieldBlock;

        public Break(WorldRuntimes runtimes, String fieldBlock) {
            super(BreakBlockEvent.class);
            this.runtimes = runtimes;
            this.fieldBlock = fieldBlock;
        }

        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
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
                if (event.isCancelled()
                        || !fieldBlock.equals(event.getBlockType().getId())) {
                    return;
                }
                FieldActions fields = actions(runtimes, store);
                if (fields != null) {
                    fields.broken(HutBlockSystems.pos(event.getTargetBlock()));
                }
            } catch (RuntimeException e) {
                event.setCancelled(true);
                HutBlockSystems.failed("field break", event.getTargetBlock(), e);
            }
        }
    }

    /** Cancelled: the field block's own interaction does nothing; its window is ours. */
    public static final class Use extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
        private final Set<Dependency<EntityStore>> dependencies =
                Set.of(new SystemDependency<>(Order.AFTER, BlockUseProtectionSystem.class));
        private final WorldRuntimes runtimes;
        private final String fieldBlock;

        public Use(WorldRuntimes runtimes, String fieldBlock) {
            super(UseBlockEvent.Pre.class);
            this.runtimes = runtimes;
            this.fieldBlock = fieldBlock;
        }

        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
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
                if (event.isCancelled()
                        || !fieldBlock.equals(event.getBlockType().getId())) {
                    return;
                }
                event.setCancelled(true);
                FieldActions fields = actions(runtimes, store);
                PlayerRef player = HutBlockSystems.player(index, chunk, store);
                if (fields != null && player != null) {
                    fields.open(player.getUuid(), HutBlockSystems.pos(event.getTargetBlock()));
                }
            } catch (RuntimeException e) {
                event.setCancelled(true);
                HutBlockSystems.failed("field use", event.getTargetBlock(), e);
            }
        }
    }
}
