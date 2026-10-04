package dev.hyangler.api;

import java.util.Objects;
import java.util.Optional;

/**
 * One catch: the item, how many, its category, the rarity rolled for a fish with rarity states, and the data file it
 * comes from (spec § 8.1).
 *
 * @param itemId the base item's id; a rarity names one of its states
 * @param count at least 1
 * @param source the file's id under {@code Server/HyAngler/}, the item id for our files
 * @since 1.0
 */
public record Catch(String itemId, int count, CatchCategory category, Optional<Rarity> rarity, String source) {
    /** Throws {@link IllegalArgumentException} on a count under 1. */
    public Catch {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(source, "source");
        if (count < 1) {
            throw new IllegalArgumentException("count " + count);
        }
    }
}
