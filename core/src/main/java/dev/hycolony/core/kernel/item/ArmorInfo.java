package dev.hycolony.core.kernel.item;

/**
 * What the catalog knows of an armour piece: the slot it goes in, in Hytale's ItemArmorSlot order, and its ItemLevel,
 * which stands for MC's armour value (spec 2026-10-02 citizen inventory, § 3).
 */
public record ArmorInfo(Slot slot, int itemLevel) {
    public ArmorInfo {
        if (itemLevel < 0) {
            throw new IllegalArgumentException("itemLevel must be >= 0: " + itemLevel);
        }
    }

    /** Hytale's armour slots, in ItemArmorSlot order. Deviation from MC: hands instead of feet. */
    public enum Slot {
        HEAD,
        CHEST,
        HANDS,
        LEGS;

        /** Its slot in a citizen's armour. */
        public int index() {
            return ordinal();
        }
    }
}
