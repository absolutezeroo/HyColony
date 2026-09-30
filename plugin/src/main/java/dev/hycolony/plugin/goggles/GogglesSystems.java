package dev.hycolony.plugin.goggles;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * The plugin side of the build goggles: which players wear them (the head armour slot, read on every armour change
 * and when a player is ready in a world), and a visibility filter that keeps each preview to its owner.
 */
public final class GogglesSystems {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** {@code ItemArmorSlot.Head}: slot 0 of the armour container. */
    private static final short HEAD_SLOT = 0;

    private GogglesSystems() {}

    /** Tells the world's goggles whether {@code player} wears {@code goggles} in the head slot of {@code armor}. */
    static void report(WorldRuntime rt, UUID player, ItemContainer armor, String goggles) {
        ItemStack head = armor.getItemStack(HEAD_SLOT);
        if (head != null && !ItemStack.isEmpty(head) && goggles.equals(head.getItemId())) {
            rt.goggles().equip(player);
        } else {
            rt.goggles().unequip(player);
        }
    }

    /** PlayerReadyEvent: no armour event is sent on joining a world, so the slot is read once the player is in. */
    public static void onPlayerReady(WorldRuntimes runtimes, String goggles, PlayerReadyEvent e) {
        try {
            checkOnJoin(runtimes, goggles, e);
        } catch (RuntimeException ex) { // the world may no longer take tasks (stopping)
            LOG.at(Level.SEVERE).withCause(ex).log("HyColony goggles check failed on join");
        }
    }

    private static void checkOnJoin(WorldRuntimes runtimes, String goggles, PlayerReadyEvent e) {
        Ref<EntityStore> ref = e.getPlayerRef();
        World world = e.getPlayer().getWorld();
        WorldRuntime rt = runtimes.of(world);
        if (world == null || rt == null) {
            return;
        }
        world.execute(() -> {
            try {
                if (!ref.isValid() || !rt.enabled()) {
                    return;
                }
                Store<EntityStore> store = ref.getStore();
                PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
                InventoryComponent.Armor armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
                if (player != null && armor != null) {
                    report(rt, player.getUuid(), armor.getInventory(), goggles);
                }
            } catch (RuntimeException ex) {
                LOG.at(Level.SEVERE).withCause(ex).log("HyColony goggles check failed on join");
            }
        });
    }

    /** Armour changed: the head slot is read again (MC checks the HEAD slot every frame). */
    public static final class ArmorChange extends EntityEventSystem<EntityStore, InventoryChangeEvent> {
        private final WorldRuntimes runtimes;
        private final String goggles;

        public ArmorChange(WorldRuntimes runtimes, String goggles) {
            super(InventoryChangeEvent.class);
            this.runtimes = runtimes;
            this.goggles = goggles;
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
                @Nonnull InventoryChangeEvent event) {
            try {
                if (!InventoryComponent.Armor.getComponentType().equals(event.getComponentType())) {
                    return;
                }
                WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
                PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
                if (rt != null && rt.enabled() && player != null) {
                    report(rt, player.getUuid(), event.getInventory().getInventory(), goggles);
                }
            } catch (RuntimeException e) {
                LOG.at(Level.SEVERE).withCause(e).log("HyColony goggles armour check failed");
            }
        }
    }

    /**
     * Keeps a preview entity visible to its owner only, like vanilla {@code HideEntitySystems.AdventurePlayerSystem}:
     * runs after {@code CollectVisible} and takes the other players' previews out of the viewer's visible set.
     */
    public static final class Visibility extends EntityTickingSystem<EntityStore> {
        private final WorldRuntimes runtimes;
        /** The first failure is logged SEVERE, the next ones FINE: this runs for every player every tick. */
        private boolean failed;

        private final Set<Dependency<EntityStore>> dependencies =
                Set.of(new SystemDependency<>(Order.AFTER, EntityTrackerSystems.CollectVisible.class));

        public Visibility(WorldRuntimes runtimes) {
            this.runtimes = runtimes;
        }

        @Override
        public SystemGroup<EntityStore> getGroup() {
            return EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP;
        }

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(EntityTrackerSystems.EntityViewer.getComponentType(), PlayerRef.getComponentType());
        }

        @Override
        public void tick(
                float dt,
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer) {
            // A throw here would end the world thread (TickingThread catches outside its loop): never let one out.
            try {
                WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
                if (rt == null) {
                    return;
                }
                EntityTrackerSystems.EntityViewer viewer =
                        chunk.getComponent(index, EntityTrackerSystems.EntityViewer.getComponentType());
                PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
                if (viewer != null && player != null) {
                    viewer.hiddenCount += rt.previews().hideFromOthers(player.getUuid(), viewer.visible);
                }
            } catch (RuntimeException e) {
                LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyColony preview visibility failed");
                failed = true;
            }
        }
    }
}
