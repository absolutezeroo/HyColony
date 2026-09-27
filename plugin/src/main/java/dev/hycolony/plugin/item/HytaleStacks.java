package dev.hycolony.plugin.item;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.hycolony.core.kernel.item.DurabilityScale;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.function.ToIntFunction;

/**
 * Converts core stacks to Hytale stacks and back, a tool's damage (uses, MC) becoming its Hytale durability through
 * {@link DurabilityScale}: one use costs {@code maxDurability / durability(id)} points. Read back, a partly spent use
 * counts as a whole one, so no transfer repairs a tool; a broken Hytale tool reads as worn out. A breakable item the
 * core never wears (a weapon, armour) counts one use per durability point, so its wear travels too.
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
                DurabilityScale.durability(a.damage(), uses(a.item(), s.getMaxDurability()), s.getMaxDurability()));
    }

    /** {@code count} of {@code s}, with the damage its durability shows. */
    public ItemAmount toAmount(ItemStack s, int count) {
        ItemKey item = new ItemKey(s.getItemId());
        int damage = s.isUnbreakable()
                ? 0
                : DurabilityScale.damage(s.getDurability(), uses(item, s.getMaxDurability()), s.getMaxDurability());
        return new ItemAmount(item, count, damage);
    }

    /** The core uses of {@code item}; one per point for an item the core does not wear. */
    private int uses(ItemKey item, double max) {
        int uses = durability.applyAsInt(item);
        return uses > 0 ? uses : (int) Math.ceil(max);
    }

    /** The whole of {@code s}, with the damage its durability shows. */
    public ItemAmount toAmount(ItemStack s) {
        return toAmount(s, s.getQuantity());
    }
}
