package dev.hycolony.plugin.npc.spawn;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.SpawnBeaconReference;
import com.hypixel.hytale.server.npc.components.SpawnMarkerReference;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntimes;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * The systems that keep hostile creatures from appearing naturally in a colony's territory ({@link HostileSpawns}):
 * an NPC is checked when a world spawn adds it (its spawn configuration is set before), and when a spawn beacon or
 * marker tags it (those marks come after the add). A plugin's NPC (our citizens) or one a command spawns has none of
 * them; a copy of a naturally spawned NPC (/entity clone) keeps its spawn configuration and is checked too.
 */
public final class HostileSpawnSystems {
    private HostileSpawnSystems() {}

    /** Registers the three checks; the hostile group's id comes from the id map. */
    public static void register(ComponentRegistryProxy<EntityStore> registry, WorldRuntimes runtimes, IdMap ids) {
        HostileSpawns rule = new HostileSpawns(runtimes, ids.npcs().group("npc.group.hostile"));
        registry.registerSystem(new WorldSpawn(rule));
        registry.registerSystem(new BeaconSpawn(rule));
        registry.registerSystem(new MarkerSpawn(rule));
    }

    /** An NPC a spawn beacon tags. Its own class: Hytale registers one system per class. */
    private static final class BeaconSpawn extends Marked<SpawnBeaconReference> {
        BeaconSpawn(HostileSpawns rule) {
            super(rule, SpawnBeaconReference.getComponentType());
        }
    }

    /** An NPC a spawn marker tags. Its own class: Hytale registers one system per class. */
    private static final class MarkerSpawn extends Marked<SpawnMarkerReference> {
        MarkerSpawn(HostileSpawns rule) {
            super(rule, SpawnMarkerReference.getComponentType());
        }
    }

    /** An NPC a world spawn adds: WorldSpawnJobSystems sets its spawn configuration before the add. */
    private static final class WorldSpawn extends RefSystem<EntityStore> {
        private final HostileSpawns rule;

        WorldSpawn(HostileSpawns rule) {
            this.rule = rule;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return HostileSpawns.npcType();
        }

        @Override
        public void onEntityAdded(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull AddReason reason,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer) {
            if (reason == AddReason.SPAWN) {
                rule.check(ref, store, npc -> npc.getSpawnConfiguration() != Integer.MIN_VALUE);
            }
        }

        @Override
        public void onEntityRemove(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull RemoveReason reason,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer) {
            // nothing to undo
        }
    }

    /** An NPC a spawn beacon or marker tags, just after adding it. */
    private abstract static class Marked<T extends Component<EntityStore>> extends RefChangeSystem<EntityStore, T> {
        private final HostileSpawns rule;
        private final ComponentType<EntityStore, T> mark;

        Marked(HostileSpawns rule, ComponentType<EntityStore, T> mark) {
            this.rule = rule;
            this.mark = mark;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return HostileSpawns.npcType();
        }

        @Nonnull
        @Override
        public ComponentType<EntityStore, T> componentType() {
            return mark;
        }

        @Override
        public void onComponentAdded(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull T component,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer) {
            rule.check(ref, store, npc -> true);
        }

        @Override
        public void onComponentSet(
                @Nonnull Ref<EntityStore> ref,
                @Nullable T before,
                @Nonnull T after,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer) {
            // the mark only changes owner: the NPC was checked when first marked
        }

        @Override
        public void onComponentRemoved(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull T component,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer) {
            // nothing to undo
        }
    }
}
