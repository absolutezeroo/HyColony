package dev.hydomum.core;

import dev.hydomum.api.MaterialTags;
import dev.hydomum.api.ShapeCatalog;
import dev.hydomum.api.VariantKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The variants HyDomum creates at boot: its saved ones, then those other mods require (the Domum blocks of a style's
 * prefabs, which would load as "Unknown" otherwise). A required id is checked like a cutter request: its shape must
 * be known and each material accepted by its slot.
 */
public final class BootVariants {
    private BootVariants() {}

    /** The keys to create, saved ones first, each once; and the required ids refused, in order. */
    public record Result(List<VariantKey> keys, List<String> refused) {
        public Result {
            keys = List.copyOf(keys);
            refused = List.copyOf(refused);
        }
    }

    /** {@code saved}, then each valid id of {@code required} not already among them. */
    public static Result merge(
            List<VariantKey> saved, Collection<String> required, ShapeCatalog shapes, MaterialTags tags) {
        Set<VariantKey> keys = new LinkedHashSet<>(saved);
        List<String> refused = new ArrayList<>();
        for (String id : required) {
            Optional<VariantKey> key = VariantKey.parse(id, shapes)
                    .map(k -> VariantRequests.check(k.shape(), k.materials(), tags))
                    .filter(VariantRequests.Accepted.class::isInstance)
                    .map(r -> ((VariantRequests.Accepted) r).key());
            key.ifPresentOrElse(keys::add, () -> refused.add(id));
        }
        return new Result(List.copyOf(keys), refused);
    }
}
