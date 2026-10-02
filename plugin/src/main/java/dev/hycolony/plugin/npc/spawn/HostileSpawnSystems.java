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
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntimes;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * The systems that keep hostile creatures from appearing naturally in a colony's territory ({@link HostileSpawns}):
 * an NPC is checked when a world spawn adds it (its spawn configuration is set before), when a spawn beacon or marker
 * tags it (those marks come after the add), and when it is re-added (chunk reload, marker restore and the like). A
 * plugin's NPC (our citizens) or one a command spawns has none of them; a copy of a naturally spawned NPC (/entity
 * clone) keeps its spawn configuration and is checked too. A colony's territory is also not wilderness, where world
 * events such as goblin breaches open ({@link WildernessTrackerSystem}).
 */
public final class HostileSpawnSystems {
    private HostileSpawnSystems() {}

    /** Registers the three checks and the colony-aware wilderness; the hostile group's id comes from the id map. */
    public static void register(ComponentRegistryProxy<EntityStore> registry, WorldRuntimes runtimes, IdMap ids) {
        HostileSpawns rule = new HostileSpawns(runtimes, ids.npcs().group("npc.group.hostile"));
        registry.registerSystem(new NaturalNpcAdded(rule));
        registry.registerSystem(new BeaconSpawn(rule));
        registry.registerSystem(new MarkerSpawn(rule));
        registry.registerSystem(new WildernessTrackerSystem(runtimes));
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

    /**
     * An NPC added to the world: born of a world spawn (WorldSpawnJobSystems sets its spawn configuration before the
     * add), or a naturally spawned one re-added (AddReason.LOAD: chunk reload, a marker restoring its stored NPCs,
     * world change, role change, prefab paste), which spawned before the colony claimed its cell or walked in.
     * Deviation from MC (asked for): MC checks only at spawn (EventHandler.on(PositionCheck)) and never removes a
     * monster already there; a naturally born hostile re-added in the territory is despawned too.
     */
    private static final class NaturalNpcAdded extends RefSystem<EntityStore> {
        private final HostileSpawns rule;

        NaturalNpcAdded(HostileSpawns rule) {
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
            rule.check(ref, store, npc -> natural(npc, ref, store));
        }

        /**
         * Whether {@code npc} was born of a world spawn, a spawn beacon or a spawn marker. A reloaded NPC already
         * carries its beacon or marker mark; a spawning one gets it only after the add ({@link Marked}).
         */
        private static boolean natural(NPCEntity npc, Ref<EntityStore> ref, Store<EntityStore> store) {
            return npc.getSpawnConfiguration() != Integer.MIN_VALUE
                    || store.getComponent(ref, SpawnMarkerReference.getComponentType()) != null
                    || store.getComponent(ref, SpawnBeaconReference.getComponentType()) != null;
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
