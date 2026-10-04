package dev.hyangler.api.condition;

import java.util.Set;

/**
 * The condition types the data files may name (spec § 6.4): HyAngler's own, and those other mods register.
 *
 * @since 1.0
 */
public interface ConditionTypes {
    /** Registers a type; throws {@link IllegalArgumentException} when the type is already registered. */
    void register(String type, ConditionFactory factory);

    /** Every registered type. */
    Set<String> types();
}
