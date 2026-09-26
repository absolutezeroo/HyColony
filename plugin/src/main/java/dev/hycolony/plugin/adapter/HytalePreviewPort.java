package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.packets.interface_.BlockChange;
import com.hypixel.hytale.protocol.packets.interface_.FluidChange;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.PrefabPreview;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.PreviewPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.joml.Vector3d;

/**
 * Previews as in-memory prefab-preview entities ({@code NetworkId + TransformComponent + PrefabPreview +
 * NonSerialized}, like the model preview of vanilla {@code ChangeModelPage}): {@code PrefabPreviewTracker} sends the
 * blocks to every player who starts seeing the entity, and {@link #hideFromOthers} keeps each one to its owner. A
 * preview's blocks cannot change, so {@link #show} removes the entity and spawns a new one.
 *
 * <p>Every change is deferred to {@code world.execute}: the core runs inside store systems, where adding or removing
 * an entity throws. Tasks run in order, so a show followed by a hide stays consistent. World thread only.
 *
 * <p>Fallback if the client ignores a {@code PrefabPreview} that never had a {@code PersistentPrefabPreview}: write
 * the blocks as a temporary prefab in the server prefab folder and spawn {@code PersistentPrefabPreview.spawn(store,
 * pos, rotation, key, Integer.MAX_VALUE)}, then give that entity {@code NonSerialized} and delete the file with the
 * entity. Only {@link #spawn} changes.
 */
public final class HytalePreviewPort implements PreviewPort {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Vanilla PrefabPreviewSystems defaults, used when a preview has no tint of its own. */
    private static final int BIOME_TINT = 6004264;

    private static final int WATER_TINT = 668501;
    private static final FluidChange[] NO_FLUIDS = new FluidChange[0];

    private final World world;
    private final Map<UUID, Map<String, Ref<EntityStore>>> byPlayer = new HashMap<>();
    private final Map<Ref<EntityStore>, UUID> owners = new HashMap<>();
    private boolean warned;

    public HytalePreviewPort(World world) {
        this.world = world;
    }

    @Override
    public void show(UUID player, String id, BlockPos origin, List<Block> blocks) {
        BlockChange[] changes = changes(blocks);
        world.execute(() -> {
            remove(player, id);
            if (changes.length > 0) {
                spawn(player, id, origin, changes);
            }
        });
    }

    @Override
    public void hide(UUID player, String id) {
        world.execute(() -> remove(player, id));
    }

    @Override
    public void hideAll(UUID player) {
        world.execute(() -> {
            for (String id : List.copyOf(byPlayer.getOrDefault(player, Map.of()).keySet())) {
                remove(player, id);
            }
        });
    }

    /** Removes from {@code visible} the previews of other players than {@code viewer}; returns how many. */
    public int hideFromOthers(UUID viewer, Set<Ref<EntityStore>> visible) {
        int hidden = 0;
        for (Map.Entry<Ref<EntityStore>, UUID> e : owners.entrySet()) {
            if (!e.getValue().equals(viewer) && visible.remove(e.getKey())) {
                hidden++;
            }
        }
        return hidden;
    }

    private void spawn(UUID player, String id, BlockPos origin, BlockChange[] changes) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            Holder<EntityStore> holder = store.getRegistry().newHolder();
            holder.addComponent(
                    NetworkId.getComponentType(),
                    new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
            holder.addComponent(
                    TransformComponent.getComponentType(),
                    new TransformComponent(new Vector3d(origin.x(), origin.y(), origin.z()), new Rotation3f()));
            holder.addComponent(
                    PrefabPreview.getComponentType(),
                    new PrefabPreview(changes, NO_FLUIDS, Integer.MAX_VALUE, BIOME_TINT, WATER_TINT));
            Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
            byPlayer.computeIfAbsent(player, p -> new HashMap<>()).put(id, ref);
            owners.put(ref, player);
        } catch (RuntimeException e) {
            warn(e);
        }
    }

    private void remove(UUID player, String id) {
        Map<String, Ref<EntityStore>> mine = byPlayer.get(player);
        Ref<EntityStore> ref = mine == null ? null : mine.remove(id);
        if (ref == null) {
            return;
        }
        owners.remove(ref);
        if (mine.isEmpty()) {
            byPlayer.remove(player);
        }
        try {
            if (ref.isValid()) {
                world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE);
            }
        } catch (RuntimeException e) {
            warn(e);
        }
    }

    /** Block ids from the asset map; fluids and unknown keys are left out (a preview only draws blocks). */
    private static BlockChange[] changes(List<Block> blocks) {
        List<BlockChange> out = new ArrayList<>(blocks.size());
        for (Block b : blocks) {
            int id = BlockType.getAssetMap().getIndex(b.state().key().id());
            if (id != Integer.MIN_VALUE) {
                out.add(new BlockChange(
                        b.offset().x(), b.offset().y(), b.offset().z(), id, (byte)
                                b.state().rotation()));
            }
        }
        return out.toArray(new BlockChange[0]);
    }

    private void warn(RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony preview failed");
        warned = true;
    }
}
