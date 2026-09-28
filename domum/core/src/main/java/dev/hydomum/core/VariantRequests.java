package dev.hydomum.core;

import dev.hydomum.api.MaterialTags;
import dev.hydomum.api.OrnamentShape;
import dev.hydomum.api.VariantKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks a request for a shape in given materials against DO's slot tags (MC DO {@code MateriallyTexturedBlockManager}
 * accepts a component only from its tag); an optional second slot left out repeats the first.
 *
 * <p>Deviation from MC: DO leaves an absent optional component unretextured (it shows its placeholder texture); a
 * Hytale variant needs a material for every tile of its layout, so the first is used.
 */
public final class VariantRequests {
    static final String BAD_COUNT = "hycolony.ornament.badCount";
    static final String BAD_MATERIAL = "hycolony.ornament.badMaterial";

    private VariantRequests() {}

    /** The outcome of a request: the variant's key, or why it is refused. */
    public sealed interface Result permits Accepted, Refused {}

    /** The request names a valid material for every slot. */
    public record Accepted(VariantKey key) implements Result {}

    /** The request is refused: reasonKey is a translation key; slot and allowed name the bad slot (-1, empty). */
    public record Refused(String reasonKey, int slot, Set<String> allowed) implements Result {
        public Refused {
            allowed = Set.copyOf(allowed);
        }
    }

    /** Accepts materials for shape when there is one per slot (or one for an optional second) and each fits. */
    public static Result check(OrnamentShape shape, List<String> materials, MaterialTags tags) {
        List<String> filled = new ArrayList<>(materials);
        if (shape.optionalSecond() && !filled.isEmpty() && filled.size() == shape.slotCount() - 1) {
            filled.add(filled.getFirst());
        }
        if (filled.size() != shape.slotCount() || filled.isEmpty()) {
            return new Refused(BAD_COUNT, -1, Set.of());
        }
        for (int slot = 0; slot < filled.size(); slot++) {
            String tag = shape.slotTags().get(slot);
            if (!tags.accepts(tag, filled.get(slot))) {
                return new Refused(BAD_MATERIAL, slot, tags.materials(tag));
            }
        }
        return new Accepted(new VariantKey(shape, filled));
    }
}
