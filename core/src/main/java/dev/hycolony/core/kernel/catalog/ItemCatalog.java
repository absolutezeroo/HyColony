package dev.hycolony.core.kernel.catalog;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import java.util.List;
import java.util.Optional;

/** What the game says of an item: its stack size, the tool it is, and how long it lasts. */
public interface ItemCatalog {
    /** How many of {@code item} one inventory slot holds (MC getMaxStackSize). */
    int maxStack(ItemKey item);

    /** Every item {@link #tool} knows, in no set order (the tools MC's ToolRequest shows, sorted by it). */
    List<ItemKey> tools();

    /** The tool type, level and speed of {@code item}; empty when it is no tool. */
    Optional<ToolInfo> tool(ItemKey item);

    /**
     * How many blocks the tool mines before it breaks (its uses, MC max damage), or how many hits an armour piece
     * takes; 0 = unbreakable (or neither).
     */
    int durability(ItemKey item);

    /**
     * Whether {@code stack} is worn to its {@link #durability}: a tool Hytale broke (it keeps it at 0 durability, MC
     * destroys it). Such a stack no longer exists for MC, so it never answers a request nor serves as a tool.
     */
    default boolean wornOut(ItemAmount stack) {
        int uses = durability(stack.item());
        return uses > 0 && stack.damage() >= uses;
    }
}
