package dev.hycolony.plugin.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryActiveSlotRequestEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.PreventInventoryAccess;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Carries out a drop on a custom page's inventory grid, as Hytale's MoveItemStack packet handler does
 * (InventoryPacketHandler.handle): InventoryUtils.moveItem between two sections (negative: the player's inventory
 * parts, others: open windows), which honours the target container's slot filters, then, for an item put in a
 * utility slot from elsewhere, that slot made active. World thread.
 */
public final class InventoryMoves {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private InventoryMoves() {}

    /**
     * Moves the dropped stack to slot drop.toSlot of section toSection. The client's positions are checked against
     * the server's containers and its quantity is capped by the stack really there: an unknown section, an empty
     * source or an out-of-range slot moves nothing. Never throws; the first failure is logged, later ones at FINE.
     */
    public static void apply(Ref<EntityStore> player, Store<EntityStore> store, InventoryDrop drop, int toSection) {
        try {
            int quantity = allowed(player, store, drop) ? movable(player, store, drop, toSection) : 0;
            if (quantity > 0) {
                InventoryUtils.moveItem(
                        player, drop.fromSection, drop.fromSlot, quantity, toSection, drop.toSlot, store);
                if (toSection == InventoryComponent.UTILITY_SECTION_ID && drop.fromSection != toSection) {
                    activate(player, store, drop.toSlot.byteValue());
                }
            }
        } catch (RuntimeException e) {
            LOG.at(WARNED.getAndSet(true) ? Level.FINE : Level.WARNING).withCause(e).log(
                    "hycolony: inventory drop on %s failed", drop.grid());
        }
    }

    /**
     * Makes the utility slot an item was just put in the active one, as the native handler does after such a move:
     * a cancellable InventoryActiveSlotRequestEvent, then the new slot set and sent to the client.
     */
    private static void activate(Ref<EntityStore> player, Store<EntityStore> store, byte slot) {
        int section = InventoryComponent.UTILITY_SECTION_ID;
        byte current = InventoryUtils.getActiveSlot(player, section, store);
        if (current == slot) {
            return;
        }
        InventoryActiveSlotRequestEvent event = new InventoryActiveSlotRequestEvent(section, current, slot, true);
        store.invoke(player, event);
        if (event.isCancelled() || event.getNewSlot() == current) {
            return;
        }
        InventoryUtils.setActiveSlot(player, section, event.getNewSlot(), store);
        PlayerRef ref = store.getComponent(player, PlayerRef.getComponentType());
        if (ref != null) {
            ref.getPacketHandler().writeNoCache(new SetActiveSlot(section, event.getNewSlot()));
        }
    }

    /** As the native handler: a player whose inventory is locked (PreventInventoryAccess) moves nothing. */
    private static boolean allowed(Ref<EntityStore> player, Store<EntityStore> store, InventoryDrop drop) {
        return drop.complete()
                && player.isValid()
                && !store.getArchetype(player).contains(PreventInventoryAccess.getComponentType());
    }

    /** How many of the dropped stack can move: capped by the stack really there; 0 when the drop does not fit. */
    private static int movable(Ref<EntityStore> player, Store<EntityStore> store, InventoryDrop drop, int toSection) {
        ItemContainer from = InventoryUtils.getSectionById(player, drop.fromSection, store);
        ItemContainer to = InventoryUtils.getSectionById(player, toSection, store);
        if (from == null || to == null || !inRange(from, drop.fromSlot) || !inRange(to, drop.toSlot)) {
            return 0;
        }
        ItemStack stack = from.getItemStack(drop.fromSlot.shortValue());
        return stack == null || stack.isEmpty() ? 0 : Math.clamp(drop.quantity, 1, stack.getQuantity());
    }

    private static boolean inRange(ItemContainer container, Integer slot) {
        return slot != null && slot >= 0 && slot < container.getCapacity();
    }
}
