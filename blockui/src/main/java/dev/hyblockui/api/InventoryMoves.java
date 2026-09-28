package dev.hyblockui.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.PreventInventoryAccess;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Carries out a drop on a custom page's inventory grid, as Hytale's MoveItemStack packet handler does
 * (InventoryPacketHandler.handle): InventoryUtils.moveItem between two sections (negative: the player's inventory
 * parts, others: open windows), which honours the target container's slot filters. The native handler's extra step
 * for the utility section (making the dropped slot active) is left out: no page shows that section. World thread.
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
            }
        } catch (RuntimeException e) {
            LOG.at(WARNED.getAndSet(true) ? Level.FINE : Level.WARNING).withCause(e).log(
                    "hyblockui: inventory drop on %s failed", drop.grid());
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
