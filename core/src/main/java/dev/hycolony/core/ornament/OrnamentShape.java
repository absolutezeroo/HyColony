package dev.hycolony.core.ornament;

import java.util.List;

/**
 * One Domum Ornamentum shape of the generated manifest: its template block, cutter group, the DO tag of each
 * material slot, and whether the second slot may repeat the first (MC DO {@code IMateriallyTexturedBlock}).
 */
public record OrnamentShape(
        String id,
        String templateKey,
        String group,
        List<String> slotTags,
        boolean optionalSecond,
        int cutterQuantity) {

    public OrnamentShape {
        slotTags = List.copyOf(slotTags);
    }

    /** Number of material slots (1 or 2). */
    public int slotCount() {
        return slotTags.size();
    }
}
