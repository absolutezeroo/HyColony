package dev.hyvanilla.plugin.block;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

/**
 * The world and inventory changes of one flower pot use, done as the vanilla interactions do them: the pot's block is
 * swapped like ChangeStateInteraction (same rotation), the hand and inventory changed like ModifyInventoryInteraction.
 * World thread only.
 */
record FlowerPotUse(
        World world,
        Vector3i target,
        InteractionContext context,
        Ref<EntityStore> player,
        CommandBuffer<EntityStore> buffer) {
    /** PERFORM_BLOCK_UPDATE | NO_SEND_PARTICLES, as vanilla ChangeStateInteraction swaps a state. */
    private static final int SWAP_SETTINGS = 260;

    /** Replaces the pot at the target by the block {@code key}, keeping its rotation; false if key or chunk is gone. */
    boolean swap(String key) {
        int id = BlockType.getAssetMap().getIndex(key);
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(target.x, target.y, target.z);
        BlockType type =
                id == Integer.MIN_VALUE ? null : BlockType.getAssetMap().getAsset(id);
        if (section == null || !section.isValid() || type == null) {
            return false;
        }
        BlockSection blocks = chunks.getStore().getComponent(section, BlockSection.getComponentType());
        int rotation = blocks == null ? 0 : blocks.getRotationIndex(target.x, target.y, target.z);
        return BlockOperations.setBlock(
                chunks, section, target.x, target.y, target.z, id, type, rotation, 0, SWAP_SETTINGS);
    }

    /** Takes one item from the held stack; false if the hand is empty or its slot changed meanwhile. */
    boolean takeOneHeld() {
        ItemStack held = context.getHeldItem();
        ItemContainer container = context.getHeldItemContainer();
        if (held == null || container == null) {
            return false;
        }
        ItemStackSlotTransaction taken = container.removeItemStackFromSlot(context.getHeldItemSlot(), held, 1);
        if (!taken.succeeded()) {
            return false;
        }
        context.setHeldItem(taken.getSlotAfter());
        return true;
    }

    /** Gives one {@code item} to the player, dropped at his feet if his inventory is full (Minecraft addItem). */
    void give(String item) {
        ItemContainer inventory =
                InventoryComponent.getCombined(buffer, player, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        SimpleItemContainer.addOrDropItemStack(buffer, player, inventory, new ItemStack(item, 1));
    }
}
