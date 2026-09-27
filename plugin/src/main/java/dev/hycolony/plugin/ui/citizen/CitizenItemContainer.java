package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ClearTransaction;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.action.CitizenInventoryActions;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * A citizen's core inventory seen as a Hytale container: every slot read and write goes to the core, so the player and
 * the citizen's AI share one live inventory (MC ContainerCitizenInventory's SlotItemHandler on the citizen's
 * inventory). {@code items} only caches the stacks built from the core; a tool shows the wear its job counted. World
 * thread only.
 */
final class CitizenItemContainer extends SimpleItemContainer {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final CitizenData citizen;
    private final int colonyId;
    private final BooleanSupplier alive;
    private final Supplier<CitizenInventoryActions> actions;
    /** Nesting of write actions: a move locks both containers and re-enters, only the outermost one reports. */
    private int writeDepth;

    /**
     * Reads {@code citizen}'s inventory directly; {@code alive} says whether it is still in its colony (nothing is
     * accepted once it is not); {@code actions} gets each player move that changed it, and keeps its tools' wear.
     */
    CitizenItemContainer(
            CitizenData citizen, int colonyId, BooleanSupplier alive, Supplier<CitizenInventoryActions> actions) {
        super((short) citizen.inventory().size());
        this.citizen = citizen;
        this.colonyId = colonyId;
        this.alive = alive;
        this.actions = actions;
    }

    @Override
    protected ItemStack internal_getSlot(short slot) {
        Inventory inv = citizen.inventory();
        ItemAmount a = slot < inv.size() ? inv.slot(slot).orElse(null) : null;
        ItemStack cached = items[slot];
        if (a == null) {
            items[slot] = null;
            return null;
        }
        if (cached == null || !cached.getItemId().equals(a.item().id()) || cached.getQuantity() != a.count()) {
            cached = new ItemStack(a.item().id(), a.count());
        }
        if (!cached.isUnbreakable()) {
            OptionalDouble condition = condition(a.item());
            // At least 1 while the core still holds it: Hytale shows 0 as broken, the core removes a broken tool.
            double durability = condition.isPresent()
                    ? Math.max(1, cached.getMaxDurability() * condition.getAsDouble())
                    : cached.getMaxDurability();
            if (cached.getDurability() != durability) {
                cached = cached.withDurability(durability);
            }
        }
        items[slot] = cached;
        return cached;
    }

    private OptionalDouble condition(ItemKey item) {
        return actions.get().toolCondition(colonyId, citizen.id(), item);
    }

    @Override
    protected ItemStack internal_setSlot(short slot, ItemStack itemStack) {
        if (ItemStack.isEmpty(itemStack)) {
            return internal_removeSlot(slot);
        }
        ItemStack previous = internal_getSlot(slot);
        Inventory inv = citizen.inventory();
        if (slot < inv.size()) {
            ItemKey item = new ItemKey(itemStack.getItemId());
            inv.set(slot, Optional.of(new ItemAmount(item, itemStack.getQuantity())));
            if (!itemStack.isUnbreakable()) {
                actions.get()
                        .toolPutIn(
                                colonyId, citizen.id(), item, itemStack.getDurability() / itemStack.getMaxDurability());
            }
            items[slot] = itemStack;
        }
        return previous;
    }

    @Override
    protected ItemStack internal_removeSlot(short slot) {
        ItemStack previous = internal_getSlot(slot);
        Inventory inv = citizen.inventory();
        if (slot < inv.size()) {
            inv.set(slot, Optional.empty());
        }
        items[slot] = null;
        return previous;
    }

    @Nonnull
    @Override
    protected ClearTransaction internal_clear() {
        ItemStack[] previous = new ItemStack[capacity];
        for (short i = 0; i < capacity; i++) {
            previous[i] = internal_removeSlot(i);
        }
        return new ClearTransaction(true, (short) 0, previous);
    }

    /**
     * Refuses anything the core cannot hold as it is, and everything once the citizen is gone.
     *
     * <p>Deviation from MC: the core keeps item keys and counts only, so an item with metadata is refused rather than
     * stripped. A worn tool is kept as its job's use count for that kind of tool (MC keeps the damage on the stack);
     * a citizen whose job does not wear tools refuses it rather than repair it.
     */
    @Override
    protected boolean cantAddToSlot(short slot, ItemStack itemStack, ItemStack slotItemStack) {
        return !alive.getAsBoolean()
                || itemStack.getMetadata() != null
                || worn(itemStack)
                        && condition(new ItemKey(itemStack.getItemId())).isEmpty()
                || super.cantAddToSlot(slot, itemStack, slotItemStack);
    }

    private static boolean worn(ItemStack itemStack) {
        return !itemStack.isUnbreakable() && itemStack.getDurability() < itemStack.getMaxDurability();
    }

    @Override
    public boolean isEmpty() {
        Inventory inv = citizen.inventory();
        return inv.freeSlots() == inv.size();
    }

    /** A detached snapshot of the current slots (Hytale copies containers for previews and transactions). */
    @Nonnull
    @Override
    public SimpleItemContainer clone() {
        lock.readLock().lock();
        try {
            int count = 0;
            for (short i = 0; i < capacity; i++) {
                count += internal_getSlot(i) == null ? 0 : 1;
            }
            itemsCount = count;
            return new SimpleItemContainer(this);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    protected <V> V writeAction(@Nonnull Supplier<V> action) {
        return reporting(() -> super.writeAction(action));
    }

    @Override
    protected <X, V> V writeAction(@Nonnull Function<X, V> action, X x) {
        return reporting(() -> super.writeAction(action, x));
    }

    /** Runs a write; the outermost one then tells the core what the player changed, if anything. */
    private <V> V reporting(Supplier<V> write) {
        if (writeDepth > 0) {
            return write.get();
        }
        Inventory inv = citizen.inventory();
        Inventory before = inv.copy();
        long changes = inv.changes();
        writeDepth++;
        try {
            return write.get();
        } finally {
            writeDepth--;
            if (citizen.inventory().changes() != changes) {
                report(before);
            }
        }
    }

    private void report(Inventory before) {
        try {
            actions.get().onPlayerEdit(colonyId, citizen.id(), before);
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony: citizen inventory change not applied to its requests");
        }
    }
}
