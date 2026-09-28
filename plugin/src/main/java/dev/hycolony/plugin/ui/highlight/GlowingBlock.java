package dev.hycolony.plugin.ui.highlight;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.block.HytaleSections;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * A glowing copy of a block: a block entity of the same block, a little larger, intangible and still, carrying the
 * glow effect of the id-map ({@code highlightEffect}, a vanilla ModelVFX effect such as Drop_Legendary). Built as the
 * prefab editor's anchor (PrefabEditingMetadata.createAnchorEntityAt), placed as a dropped carried block
 * (CarriedBlock: mid-height of its hitbox), the effect added as CarriedBlockSystems.ApplyDroppedBlockEntityEffect does.
 * It despawns by itself. World thread; never throws.
 */
public final class GlowingBlock {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The copy's size against the block's, so it wraps the block instead of fighting its faces. */
    private static final float SCALE = 1.05f;

    private static volatile Optional<String> effect = Optional.empty();

    private GlowingBlock() {}

    /** The glow effect to use, from the id-map; without one the copy is spawned without glow. */
    public static void useEffect(Optional<String> effectId) {
        effect = effectId;
    }

    /** Spawns the glowing copy of the block at {@code pos} for {@code millis}; its uuid, empty on air or a failure. */
    static Optional<UUID> spawn(World world, BlockPos pos, long millis) {
        try {
            BlockType type = typeAt(world, pos);
            if (type == null) {
                return Optional.empty();
            }
            Store<EntityStore> store = world.getEntityStore().getStore();
            TimeResource time = store.getResource(TimeResource.getResourceType());
            Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(time, type.getId(), centre(pos, type));
            holder.putComponent(
                    DespawnComponent.getComponentType(), DespawnComponent.despawnInMilliseconds(time, millis));
            holder.removeComponent(Velocity.getComponentType()); // no physics pushing it out of the real block
            holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
            holder.addComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(SCALE));
            holder.ensureComponent(EffectControllerComponent.getComponentType());
            UUID id = holder.ensureAndGetComponent(UUIDComponent.getComponentType())
                    .getUuid();
            world.execute(() -> add(store, holder));
            return Optional.of(id);
        } catch (RuntimeException e) {
            LOG.at(Level.WARNING).withCause(e).log("HyColony highlight: cannot spawn the glow at %s", pos);
            return Optional.empty();
        }
    }

    /** Removes the copy {@code id} if it is still there. */
    static void remove(World world, UUID id) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        world.execute(() -> {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(id);
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        });
    }

    private static void add(Store<EntityStore> store, Holder<EntityStore> holder) {
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        EntityEffect glow = effect.map(EntityEffect.getAssetMap()::getAsset).orElse(null);
        if (ref == null || glow == null) {
            return;
        }
        EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effects != null) {
            effects.addEffect(ref, glow, store);
        }
    }

    /** The block's centre on x and z, mid-height of its hitbox on y (CarriedBlock places a dropped block so). */
    private static Vector3d centre(BlockPos pos, BlockType type) {
        BlockBoundingBoxes boxes = BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
        double height = boxes == null ? 1 : boxes.get(0).getBoundingBox().height();
        return new Vector3d(pos.x() + 0.5, pos.y() + height / 2, pos.z() + 0.5);
    }

    private static @Nullable BlockType typeAt(World world, BlockPos pos) {
        Ref<ChunkStore> section = HytaleSections.section(world, pos);
        BlockSection blocks = section == null
                ? null
                : world.getChunkStore().getStore().getComponent(section, BlockSection.getComponentType());
        int id = blocks == null ? BlockType.EMPTY_ID : blocks.get(pos.x(), pos.y(), pos.z());
        return id == BlockType.EMPTY_ID ? null : BlockType.getAssetMap().getAsset(id);
    }
}
