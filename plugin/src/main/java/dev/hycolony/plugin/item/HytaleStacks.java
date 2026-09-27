package dev.hycolony.plugin.item;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.hycolony.core.kernel.item.DurabilityScale;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.function.ToIntFunction;

/**
 * Converts core stacks to Hytale stacks and back, a tool's damage (uses, MC) becoming its Hytale durability through
 * {@link DurabilityScale}: one use costs {@code maxDurability / durability(id)} points of the item's own max. Read
 * back, a partly spent use counts as a whole one, and the max a Hytale repair took off the stack counts as damage, so
 * no transfer repairs a tool; a broken Hytale tool reads as worn out.
 */
public final class HytaleStacks {
    private final ToIntFunction<ItemKey> durability;

    /** {@code durability}: the uses before an item breaks (ItemCatalog.durability), 0 when unbreakable. */
    public HytaleStacks(ToIntFunction<ItemKey> durability) {
        this.durability = durability;
    }

    /** A Hytale stack of {@code a}, at the durability its damage leaves. Throws on an unknown item id, like Hytale. */
    public ItemStack toStack(ItemAmount a) {
        ItemStack s = new ItemStack(a.item().id(), a.count());
        if (a.damage() == 0 || s.isUnbreakable()) {
            return s;
        }
        return s.withDurability(
                DurabilityScale.durability(a.damage(), durability.applyAsInt(a.item()), s.getMaxDurability()));
    }

    /** {@code count} of {@code s}, with the damage its durability shows. */
    public ItemAmount toAmount(ItemStack s, int count) {
        ItemKey item = new ItemKey(s.getItemId());
        // Against the item's max, not the stack's: a repair lowers the stack's (RepairItemInteraction).
        int damage = DurabilityScale.damage(
                s.getDurability(), s.getMaxDurability(), s.getItem().getMaxDurability(), durability.applyAsInt(item));
        return new ItemAmount(item, count, damage);
    }

    /** The whole of {@code s}, with the damage its durability shows. */
    public ItemAmount toAmount(ItemStack s) {
        return toAmount(s, s.getQuantity());
    }
}
