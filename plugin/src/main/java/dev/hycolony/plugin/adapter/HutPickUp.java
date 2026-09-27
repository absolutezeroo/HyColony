package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.IdMap;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.joml.Vector3i;

/** The hut window's "Pick up" button: the hut item to the player, then the block removed without a drop. */
final class HutPickUp {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final Supplier<ColonyManager> manager;
    private final IdMap ids;

    HutPickUp(Supplier<ColonyManager> manager, IdMap ids) {
        this.manager = manager;
        this.ids = ids;
    }

    /** "Pick up": the hut item goes to the player's inventory; once the core agrees, the block goes without a drop. */
    void run(UUID player, BuildingView view) {
        ColonyManager m = manager.get();
        BuildingType type = m.context().buildingTypes().byId(view.typeId()).orElse(null);
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        if (type == null || ref == null || !ref.isValid()) {
            return;
        }
        String itemId = ids.itemId(type.hutBlockKey());
        if (m.huts().pickUp(player, view.pos(), () -> give(ref, itemId))) {
            removeWithoutDrop(ref.getStore().getExternalData().getWorld(), view.pos(), ids.blockId(type.hutBlockKey()));
        }
    }

    /**
     * Removes the hut block if it is still {@code blockId}, with no item drop (the player already got it). Its
     * container's contents still spill on the ground (ItemContainerSystems drops them on any removal).
     */
    private static void removeWithoutDrop(World world, BlockPos pos, String blockId) {
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> section = cs.getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
        if (section == null) {
            return;
        }
        BlockSection blocks = cs.getStore().getComponent(section, BlockSection.getComponentType());
        if (blocks == null) {
            return;
        }
        BlockType type = BlockType.getAssetMap().getAsset(blocks.get(pos.x(), pos.y(), pos.z()));
        if (type == null || !(blockId.equals(type.getId()) || blockId.equals(type.getDefaultStateKey()))) {
            // The core already dropped the building and the player has the item: the block is left alone.
            LOG.at(Level.WARNING).log(
                    "HyColony pick-up: expected %s at %s, found %s; block left in place",
                    blockId, pos, type == null ? "nothing" : type.getId());
            return;
        }
        BlockHarvestUtils.naturallyRemoveBlock(
                new Vector3i(pos.x(), pos.y(), pos.z()),
                type,
                blocks.getFiller(pos.x(), pos.y(), pos.z()),
                0,
                null,
                null,
                SetBlockSettings.NO_DROP_ITEMS,
                section,
                world.getEntityStore().getStore(),
                cs.getStore());
    }

    /** One {@code itemId} into hotbar then storage; true only if it fit. */
    private static boolean give(Ref<EntityStore> ref, String itemId) {
        ItemContainer inv = InventoryComponent.getCombined(ref.getStore(), ref, InventoryComponent.HOTBAR_FIRST);
        return ItemStack.isEmpty(inv.addItemStack(new ItemStack(itemId, 1)).getRemainder());
    }
}
