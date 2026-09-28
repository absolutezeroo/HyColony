package dev.hycolony.core.ornament;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One material combination of a shape: its runtime block key ({@code <template>__<m1>[__<m2>]}) and its saved id
 * ({@code <shape>|<m1>[|<m2>]}). Materials are Hytale block ids, one per slot, in DO's component order.
 */
public record VariantKey(OrnamentShape shape, List<String> materials) {
    private static final String KEY_SEPARATOR = "__";
    private static final String ID_SEPARATOR = "|";

    public VariantKey {
        materials = List.copyOf(materials);
    }

    /** The BlockType (and item) key of the variant. */
    public String blockTypeKey() {
        return shape.templateKey() + KEY_SEPARATOR + String.join(KEY_SEPARATOR, materials);
    }

    /** The id the variant is saved under. */
    public String id() {
        return shape.id() + ID_SEPARATOR + String.join(ID_SEPARATOR, materials);
    }

    /**
     * The key a saved id names (its shape found case ignored, then named as the catalog spells it); empty when the
     * shape is not in catalog, the material count is wrong or a material is blank.
     */
    public static Optional<VariantKey> parse(String id, ShapeCatalog catalog) {
        List<String> parts = Arrays.asList(id.split(Pattern.quote(ID_SEPARATOR), -1));
        return catalog.shape(parts.getFirst())
                .filter(shape -> parts.size() - 1 == shape.slotCount())
                .filter(shape -> parts.stream().noneMatch(String::isBlank))
                .map(shape -> new VariantKey(shape, parts.subList(1, parts.size())));
    }
}
