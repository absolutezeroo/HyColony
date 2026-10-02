package dev.hycolony.core.kernel.item;

import java.util.Arrays;
import java.util.Optional;

/**
 * What the catalog knows of an armour piece: the slot it goes in, in Hytale's ItemArmorSlot order, and its ItemLevel,
 * which stands for MC's armour value (spec 2026-10-02 citizen inventory, § 3). Its wear is counted as a tool's, in the
 * hits it takes before it breaks ({@code ItemCatalog.durability}).
 */
public record ArmorInfo(Slot slot, int itemLevel) {
    public ArmorInfo {
        if (itemLevel < 0) {
            throw new IllegalArgumentException("itemLevel must be >= 0: " + itemLevel);
        }
    }

    /**
     * Hytale's armour slots, with the index each has in a citizen's saved armour (ItemArmorSlot's values). Deviation
     * from MC (Hytale world): MC's helmet, chestplate, leggings, boots → Hytale's head, chest, hands, legs (protocol
     * ItemArmorSlot).
     */
    public enum Slot {
        HEAD(0),
        CHEST(1),
        HANDS(2),
        LEGS(3);

        private final int index;

        Slot(int index) {
            this.index = index;
        }

        /** Its slot in a citizen's armour, as saved. */
        public int index() {
            return index;
        }

        /** The slot of index {@code index}; empty for none. */
        public static Optional<Slot> at(int index) {
            return Arrays.stream(values()).filter(s -> s.index == index).findFirst();
        }
    }
}
