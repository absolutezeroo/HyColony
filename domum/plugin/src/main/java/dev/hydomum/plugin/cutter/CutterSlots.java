package dev.hydomum.plugin.cutter;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import dev.hyblockui.api.ReturningContainerWindow;
import dev.hydomum.core.cutter.SlotContent;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.IntStream;

/**
 * One player's cutter material slots (MC DO ArchitectsCutterContainer's input slots): a 2-slot container opened as a
 * window with the cutter page, taking only what the chosen shape's slot accepts ({@code mayPlace}), and given back to
 * the player when the window closes ({@code removed} → {@code clearContainer}). World thread.
 */
final class CutterSlots {
    /** DO's cutter has as many input slots as the largest component count (ArchitectsCutterContainer). */
    static final int COUNT = 2;

    private final SimpleItemContainer container = new SimpleItemContainer((short) COUNT);
    private final ReturningContainerWindow window = new ReturningContainerWindow(container);

    /** Slots that take an item into slot i only when accepts(i, itemId) holds. */
    CutterSlots(BiPredicate<Integer, String> accepts) {
        for (short i = 0; i < COUNT; i++) {
            container.setSlotFilter(
                    FilterActionType.ADD,
                    i,
                    (type, target, slot, stack) -> stack == null || accepts.test((int) slot, stack.getItemId()));
        }
    }

    /** The window to open with the page; the page's slot grid is bound to its id. */
    ReturningContainerWindow window() {
        return window;
    }

    /** The slots' container. */
    ItemContainer container() {
        return container;
    }

    /** What each slot holds now, in order. */
    List<SlotContent> contents() {
        return IntStream.range(0, COUNT)
                .mapToObj(i -> {
                    ItemStack stack = container.getItemStack((short) i);
                    return stack == null || stack.isEmpty()
                            ? SlotContent.EMPTY
                            : new SlotContent(stack.getItemId(), stack.getQuantity());
                })
                .toList();
    }

    /**
     * Takes crafts items from each slot in consumed (1 per craft, MC DO output slot {@code onTake}), all or nothing:
     * false, taking nothing, when one of them holds fewer.
     */
    boolean take(List<Integer> consumed, int crafts) {
        List<SlotContent> now = contents();
        boolean enough =
                consumed.stream().allMatch(slot -> slot < COUNT && now.get(slot).quantity() >= crafts);
        if (!enough) {
            return false;
        }
        consumed.forEach(slot -> container.removeItemStackFromSlot(slot.shortValue(), crafts, true, false));
        return true;
    }
}
