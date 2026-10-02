package dev.hycolony.api.debug;

import dev.hycolony.api.Experimental;

/**
 * How {@link DebugAccess#modifySaturation} changes a citizen's saturation, as MC's {@code /mc citizens modify
 * saturation} operators.
 *
 * <ul>
 *   <li>{@code SET} ({@code =}): the value itself;
 *   <li>{@code INCREASE} ({@code +}): adds the value, up to the maximum;
 *   <li>{@code DECREASE} ({@code -}): takes the value times the server's food modifier, down to 0, and lets the
 *       citizen eat again, as its own hunger does.
 * </ul>
 *
 * @since 1.2
 */
@Experimental
public enum SaturationChange {
    SET,
    INCREASE,
    DECREASE
}
