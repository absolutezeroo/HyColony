package dev.hydomum.core.cutter;

/** One cutter slot: the item in it (empty id when none) and how many. */
public record SlotContent(String itemId, int quantity) {
    /** A slot holding nothing. */
    public static final SlotContent EMPTY = new SlotContent("", 0);

    /** True when the slot holds nothing. */
    public boolean isEmpty() {
        return itemId.isEmpty() || quantity <= 0;
    }
}
