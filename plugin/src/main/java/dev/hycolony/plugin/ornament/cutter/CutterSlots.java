package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import dev.hycolony.core.ornament.cutter.SlotContent;
import java.util.ArrayList;
import java.util.List;

/** The cutter block's 2 material slots, read as the core sees them. */
final class CutterSlots {
    /** Material slots of the block (its ItemContainerBlock capacity, tools/domum/blocks/cutter.py). */
    static final int COUNT = 2;

    private CutterSlots() {}

    /** Each slot's item and quantity; an empty or missing slot is {@link SlotContent#EMPTY}. */
    static List<SlotContent> read(ItemContainer container) {
        List<SlotContent> slots = new ArrayList<>();
        for (short slot = 0; slot < COUNT; slot++) {
            ItemStack stack = slot < container.getCapacity() ? container.getItemStack(slot) : null;
            slots.add(
                    stack == null || stack.isEmpty()
                            ? SlotContent.EMPTY
                            : new SlotContent(stack.getItemId(), stack.getQuantity()));
        }
        return slots;
    }
}
