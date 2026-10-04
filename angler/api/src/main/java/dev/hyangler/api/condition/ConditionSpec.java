package dev.hyangler.api.condition;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * The fields of one condition object of a data file, as its factory reads them; absent or mistyped fields are empty.
 *
 * @since 1.0
 */
public interface ConditionSpec {
    /** The condition's {@code Type}. */
    String type();

    /** A string field. */
    Optional<String> string(String key);

    /** A list of strings; empty when absent. */
    List<String> strings(String key);

    /** An integer field. */
    OptionalInt integer(String key);

    /** A number field. */
    OptionalDouble number(String key);

    /** A boolean field. */
    Optional<Boolean> bool(String key);

    /** A list of integers; empty when absent. */
    List<Integer> integers(String key);
}
