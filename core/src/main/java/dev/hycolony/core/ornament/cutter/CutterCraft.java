package dev.hycolony.core.ornament.cutter;

import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.OrnamentShape;
import dev.hycolony.core.ornament.VariantKey;
import dev.hycolony.core.ornament.VariantRequests;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The architect's cutter recipe (MC DO ArchitectsCutterRecipe): each slot of the shape must hold a material of its
 * tag; crafting gives the shape's cutter quantity and takes 1 from each slot used (remove(1)). Slots past the shape's
 * material count are ignored and kept.
 *
 * <p>Deviation from MC: an empty optional second slot is accepted and repeats the first (DO-1 VariantRequests); DO's
 * matches() refuses it.
 */
public final class CutterCraft {
    static final String EMPTY_SLOT = "hycolony.ornament.cutter.emptySlot";

    private CutterCraft() {}

    /** What crafting a shape from the slots gives, or why it cannot. */
    public sealed interface Result permits Ready, Refused {}

    /** The variant, how many are given, and the slots losing 1 each. */
    public record Ready(VariantKey key, int quantity, List<Integer> consumed) implements Result {
        public Ready {
            consumed = List.copyOf(consumed);
        }
    }

    /** Refused: a translation key, the slot at fault (-1 when none), the materials that slot accepts. */
    public record Refused(String reasonKey, int slot, Set<String> allowed) implements Result {
        public Refused {
            allowed = Set.copyOf(allowed);
        }
    }

    /** Checks slots (the cutter's, in order; missing ones count as empty) for shape against tags. */
    public static Result check(OrnamentShape shape, List<SlotContent> slots, MaterialTags tags) {
        List<String> materials = new ArrayList<>();
        List<Integer> consumed = new ArrayList<>();
        for (int slot = 0; slot < shape.slotCount(); slot++) {
            SlotContent content = slot < slots.size() ? slots.get(slot) : SlotContent.EMPTY;
            if (content.isEmpty()) {
                if (shape.optionalSecond() && slot == shape.slotCount() - 1) {
                    continue;
                }
                return new Refused(
                        EMPTY_SLOT, slot, tags.materials(shape.slotTags().get(slot)));
            }
            materials.add(content.itemId());
            consumed.add(slot);
        }
        return switch (VariantRequests.check(shape, materials, tags)) {
            case VariantRequests.Accepted accepted -> new Ready(accepted.key(), shape.cutterQuantity(), consumed);
            case VariantRequests.Refused refused -> new Refused(refused.reasonKey(), refused.slot(), refused.allowed());
        };
    }
}
