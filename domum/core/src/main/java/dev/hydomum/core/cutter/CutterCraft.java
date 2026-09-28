package dev.hydomum.core.cutter;

import dev.hydomum.api.MaterialTags;
import dev.hydomum.api.OrnamentShape;
import dev.hydomum.api.VariantKey;
import dev.hydomum.core.VariantRequests;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The architect's cutter recipe (MC DO ArchitectsCutterRecipe): each slot of the shape must hold a material of its
 * tag, and crafting gives the shape's cutter quantity, at least one per material. Taking the result removes 1 from
 * each slot used (MC DO ArchitectsCutterContainer output slot {@code onTake}, {@code remove(1)}); slots past the
 * shape's material count are ignored and kept.
 *
 * <p>Deviation from MC: an empty optional second slot is accepted and repeats the first (DO-1 VariantRequests); DO's
 * matches() refuses it.
 */
public final class CutterCraft {
    static final String EMPTY_SLOT = "hydomum.ornament.cutter.emptySlot";
    /** Most crafts one click makes when nothing limits it (a creative player's All). */
    public static final int MAX_BATCH = 64;

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

    /**
     * As {@link #check(OrnamentShape, List, MaterialTags)}, for a player who may be in creative mode: then nothing
     * is consumed (MC DO ArchitectsCutterContainer output slot {@code onTake}: {@code !thePlayer.isCreative()}).
     */
    public static Result check(OrnamentShape shape, List<SlotContent> slots, MaterialTags tags, boolean creative) {
        Result result = check(shape, slots, tags);
        return creative && result instanceof Ready ready ? new Ready(ready.key(), ready.quantity(), List.of()) : result;
    }

    /** Checks slots (the cutter's, in order; missing ones count as empty) for shape against tags. */
    public static Result check(OrnamentShape shape, List<SlotContent> slots, MaterialTags tags) {
        List<String> materials = new ArrayList<>();
        List<Integer> consumed = new ArrayList<>();
        for (int slot = 0; slot < shape.slotCount(); slot++) {
            SlotContent content = slot < slots.size() ? slots.get(slot) : SlotContent.EMPTY;
            if (content.isEmpty()) {
                // DO's first component is never optional, whatever a malformed manifest entry says.
                if (slot > 0 && shape.optionalSecond() && slot == shape.slotCount() - 1) {
                    continue;
                }
                return new Refused(
                        EMPTY_SLOT, slot, tags.materials(shape.slotTags().get(slot)));
            }
            materials.add(content.itemId());
            consumed.add(slot);
        }
        return switch (VariantRequests.check(shape, materials, tags)) {
            case VariantRequests.Accepted accepted -> new Ready(accepted.key(), quantity(shape), consumed);
            case VariantRequests.Refused refused -> new Refused(refused.reasonKey(), refused.slot(), refused.allowed());
        };
    }

    /**
     * How many times ready can be crafted from slots (the cutter's, in order): each consumed slot gives 1 per craft
     * from its own stack, so the smallest one limits; {@link #MAX_BATCH} when nothing is consumed (creative).
     *
     * <p>Deviation from MC: DO crafts one at a time; the cutter window also offers x10 and All, as Hytale's benches.
     */
    public static int maxCrafts(Ready ready, List<SlotContent> slots) {
        if (ready.consumed().isEmpty()) {
            return MAX_BATCH;
        }
        return ready.consumed().stream()
                .mapToInt(slot -> slot < slots.size() ? slots.get(slot).quantity() : 0)
                .min()
                .orElse(0);
    }

    /** How many one craft gives: at least one per material (MC DO ArchitectsCutterRecipe.assemble). */
    private static int quantity(OrnamentShape shape) {
        return Math.max(shape.slotCount(), shape.cutterQuantity());
    }
}
