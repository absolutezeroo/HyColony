package dev.hyangler.api.condition;

/**
 * Makes a condition from its object in a data file.
 *
 * @since 1.0
 */
@FunctionalInterface
public interface ConditionFactory {
    /** The condition; throws {@link IllegalArgumentException} with a reason when a field is missing or invalid. */
    Condition create(ConditionSpec spec);
}
