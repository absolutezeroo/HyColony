package dev.hycolony.core.kernel.item;

/**
 * A stack of {@code count} (> 0) of one item. {@code damage} is the uses worn off a tool (MC ItemStack.getDamageValue,
 * 1 per block mined); 0 for anything else. Tools stack to 1, so a tool's damage is its own.
 */
public record ItemAmount(ItemKey item, int count, int damage) {
    public ItemAmount {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0: " + count);
        }
        if (damage < 0) {
            throw new IllegalArgumentException("damage must be >= 0: " + damage);
        }
    }

    /** An undamaged stack. */
    public ItemAmount(ItemKey item, int count) {
        this(item, count, 0);
    }

    /** The same item and damage, {@code newCount} of it. */
    public ItemAmount withCount(int newCount) {
        return new ItemAmount(item, newCount, damage);
    }
}
