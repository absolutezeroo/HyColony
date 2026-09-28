package dev.hycolony.plugin.ornament.api;

import java.util.Optional;

/** One material combination of one shape: the cache key of a runtime BlockType. */
public record VariantKey(OrnamentShape shape, OrnamentMaterial primary, OrnamentMaterial secondary) {
    private static final String SEPARATOR = ":";

    /**
     * BlockType key of this variant, e.g. {@code HyColony_Ornament_TimberFrame_OAK_STONE}. Chunks save blocks by this
     * key, so it must never change for a given combination.
     */
    public String blockTypeKey() {
        return shape.templateKey() + "_" + primary.name() + "_" + secondary.name();
    }

    /** Stable text form {@code SHAPE:PRIMARY:SECONDARY}, read back by {@link #parse}. */
    public String id() {
        return shape.name() + SEPARATOR + primary.name() + SEPARATOR + secondary.name();
    }

    /** The key written by {@link #id}; empty on a malformed text or an unknown name. */
    public static Optional<VariantKey> parse(String id) {
        String[] parts = id.split(SEPARATOR, -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            OrnamentShape shape = OrnamentShape.valueOf(parts[0]);
            return OrnamentMaterial.parse(parts[1])
                    .flatMap(p -> OrnamentMaterial.parse(parts[2]).map(s -> new VariantKey(shape, p, s)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
