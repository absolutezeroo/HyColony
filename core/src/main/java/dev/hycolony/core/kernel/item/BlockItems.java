package dev.hycolony.core.kernel.item;

import java.util.Optional;

/**
 * What the world says about the items of one block, from which the builder picks the item that places it: its own
 * item, whether a survival player can get that item (a recipe, a block's break or a drop list gives it), the item
 * whose placement makes this block (a wall or ceiling variant), and the single stack breaking it gives. Each is empty
 * when there is none.
 */
public record BlockItems(
        Optional<ItemKey> own, boolean ownHasSource, Optional<ItemKey> placedBy, Optional<ItemAmount> breakDrop) {
    /** A block with no item at all (air, a fluid): free to place. */
    public static final BlockItems NONE = new BlockItems(Optional.empty(), false, Optional.empty(), Optional.empty());

    /** A block placed by its own item, which a survival player can get: the common case. */
    public static BlockItems of(ItemKey own) {
        return new BlockItems(Optional.of(own), true, Optional.empty(), Optional.empty());
    }
}
