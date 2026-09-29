package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ClearTransaction;
import dev.hycolony.core.app.action.CitizenInventoryActions;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.plugin.item.HytaleStacks;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * A citizen's core inventory seen as a Hytale container: every slot read and write goes to the core, so the player and
 * the citizen's AI share one live inventory (MC ContainerCitizenInventory's SlotItemHandler on the citizen's
 * inventory). {@code items} only caches the stacks built from the core, each tool at the durability its damage leaves
 * ({@link HytaleStacks}); a tool worn by a mined block changes the inventory, so an open window shows it. World thread
 * only.
 */
final class CitizenItemContainer extends SimpleItemContainer {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final CitizenData citizen;
    private final int colonyId;
    private final BooleanSupplier alive;
    private final Supplier<CitizenInventoryActions> actions;
    private final HytaleStacks stacks;
    /** Nesting of write actions: a move locks both containers and re-enters, only the outermost one reports. */
    private int writeDepth;

    /**
     * Reads {@code citizen}'s inventory directly; {@code alive} says whether it is still in its colony (nothing is
     * accepted once it is not); {@code actions} gets each player move that changed it; {@code stacks} converts a tool's
     * damage to and from Hytale durability.
     */
    CitizenItemContainer(
            CitizenData citizen,
            int colonyId,
            BooleanSupplier alive,
            Supplier<CitizenInventoryActions> actions,
            HytaleStacks stacks) {
        super((short) citizen.inventory().size());
        this.citizen = citizen;
        this.colonyId = colonyId;
        this.alive = alive;
        this.actions = actions;
        this.stacks = stacks;
    }

    @Override
    protected @Nullable ItemStack internal_getSlot(short slot) {
        Inventory inv = citizen.inventory();
        ItemAmount a = slot < inv.size() ? inv.slot(slot).orElse(null) : null;
        ItemStack cached = items[slot];
        if (a == null) {
            forget(slot);
            return null;
        }
        // The stack a player put keeps its exact durability while the core reads it as the same damage.
        if (cached == null || !stacks.toAmount(cached).equals(a)) {
            cached = stacks.toStack(a);
        }
        items[slot] = cached;
        return cached;
    }

    @Override
    protected @Nullable ItemStack internal_setSlot(short slot, ItemStack itemStack) {
        if (ItemStack.isEmpty(itemStack)) {
            return internal_removeSlot(slot);
        }
        ItemStack previous = internal_getSlot(slot);
        Inventory inv = citizen.inventory();
        if (slot < inv.size()) {
            inv.set(slot, Optional.of(stacks.toAmount(itemStack)));
            items[slot] = itemStack;
        }
        return previous;
    }

    @Override
    protected @Nullable ItemStack internal_removeSlot(short slot) {
        ItemStack previous = internal_getSlot(slot);
        Inventory inv = citizen.inventory();
        if (slot < inv.size()) {
            inv.set(slot, Optional.empty());
        }
        forget(slot);
        return previous;
    }

    /** Empties the cached stack of {@code slot}. */
    // Hytale's SimpleItemContainer.items (unannotated) holds null for an empty slot: its own code writes null there.
    @SuppressWarnings("NullAway")
    private void forget(short slot) {
        items[slot] = null;
    }

    @Nonnull
    @Override
    protected ClearTransaction internal_clear() {
        @Nullable ItemStack[] previous = new @Nullable ItemStack[capacity];
        for (short i = 0; i < capacity; i++) {
            previous[i] = internal_removeSlot(i);
        }
        return new ClearTransaction(true, (short) 0, previous);
    }

    /**
     * Refuses anything the core cannot hold as it is, and everything once the citizen is gone.
     *
     * <p>Deviation from MC: the core keeps an item's key, count and damage only, so an item with other metadata is
     * refused rather than stripped. A worn tool is accepted: its damage goes with it.
     */
    @Override
    protected boolean cantAddToSlot(short slot, ItemStack itemStack, ItemStack slotItemStack) {
        return !alive.getAsBoolean()
                || itemStack.getMetadata() != null
                || super.cantAddToSlot(slot, itemStack, slotItemStack);
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
