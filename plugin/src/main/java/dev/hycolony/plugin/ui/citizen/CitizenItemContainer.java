package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ClearTransaction;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * A citizen's core inventory seen as a Hytale container: every slot read and write goes to the core, so the player and
 * the citizen's AI share one live inventory (MC ContainerCitizenInventory's SlotItemHandler on the citizen's
 * inventory). {@code items} only caches the stacks built from the core. World thread only.
 */
final class CitizenItemContainer extends SimpleItemContainer {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Supplier<Optional<Inventory>> core;
    private final Consumer<Inventory> onPlayerEdit;
    /** Nesting of write actions: a move locks both containers and re-enters, only the outermost one reports. */
    private int writeDepth;

    /**
     * {@code core} is re-read on every access (the citizen may be gone, its inventory replaced); {@code onPlayerEdit}
     * gets the inventory as it was before each player move that changed it.
     */
    CitizenItemContainer(int capacity, Supplier<Optional<Inventory>> core, Consumer<Inventory> onPlayerEdit) {
        super((short) capacity);
        this.core = core;
        this.onPlayerEdit = onPlayerEdit;
    }

    @Override
    protected ItemStack internal_getSlot(short slot) {
        Optional<ItemAmount> a = core.get().filter(inv -> slot < inv.size()).flatMap(inv -> inv.slot(slot));
        ItemStack cached = items[slot];
        if (a.isEmpty()) {
            items[slot] = null;
            return null;
        }
        if (cached == null
                || !cached.getItemId().equals(a.get().item().id())
                || cached.getQuantity() != a.get().count()) {
            cached = new ItemStack(a.get().item().id(), a.get().count());
            items[slot] = cached;
        }
        return cached;
    }

    @Override
    protected ItemStack internal_setSlot(short slot, ItemStack itemStack) {
        if (ItemStack.isEmpty(itemStack)) {
            return internal_removeSlot(slot);
        }
        ItemStack previous = internal_getSlot(slot);
        Optional<Inventory> inv = core.get().filter(i -> slot < i.size());
        if (inv.isPresent()) {
            inv.get()
                    .set(
                            slot,
                            Optional.of(new ItemAmount(new ItemKey(itemStack.getItemId()), itemStack.getQuantity())));
            items[slot] = itemStack;
        }
        return previous;
    }

    @Override
    protected ItemStack internal_removeSlot(short slot) {
        ItemStack previous = internal_getSlot(slot);
        core.get().filter(i -> slot < i.size()).ifPresent(i -> i.set(slot, Optional.empty()));
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
     * <p>Deviation from MC: the core keeps item keys and counts only, so a worn tool or an item with metadata is
     * refused rather than silently repaired or stripped.
     */
    @Override
    protected boolean cantAddToSlot(short slot, ItemStack itemStack, ItemStack slotItemStack) {
        return core.get().isEmpty()
                || itemStack.getMetadata() != null
                || !itemStack.isUnbreakable() && itemStack.getDurability() < itemStack.getMaxDurability()
                || super.cantAddToSlot(slot, itemStack, slotItemStack);
    }

    @Override
    public boolean isEmpty() {
        return core.get().map(i -> i.contents().isEmpty()).orElse(true);
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
        Inventory before = writeDepth == 0 ? core.get().map(Inventory::copy).orElse(null) : null;
        long changes = before == null ? 0 : core.get().map(Inventory::changes).orElse(0L);
        writeDepth++;
        try {
            return write.get();
        } finally {
            writeDepth--;
            if (before != null && core.get().map(Inventory::changes).orElse(changes) != changes) {
                report(before);
            }
        }
    }

    private void report(Inventory before) {
        try {
            onPlayerEdit.accept(before);
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony: citizen inventory change not applied to its requests");
        }
    }
}
