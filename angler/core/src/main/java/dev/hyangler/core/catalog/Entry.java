package dev.hyangler.core.catalog;

import dev.hyangler.api.CatchCategory;
import dev.hyangler.api.condition.Condition;
import java.util.List;

/**
 * One possible catch read from a data file (spec § 6.1, § 6.2).
 *
 * @param weight its weight in its category, at least 1
 * @param quality its sensitivity to luck (vanilla)
 * @param rarities whether a rarity state is rolled when it is caught
 */
public record Entry(
        String id,
        CatchCategory category,
        int weight,
        int quality,
        int minCount,
        int maxCount,
        boolean rarities,
        Condition condition,
        List<Modifier> modifiers) {}
