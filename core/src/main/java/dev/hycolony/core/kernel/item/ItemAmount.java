package dev.hycolony.core.kernel.item;

/** A stack of {@code count} (> 0) of one item. */
public record ItemAmount(ItemKey item, int count) {
    public ItemAmount {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0: " + count);
        }
    }

    public ItemAmount withCount(int newCount) {
        return new ItemAmount(item, newCount);
    }
}
