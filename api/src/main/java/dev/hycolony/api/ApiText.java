package dev.hycolony.api;

import java.util.List;
import java.util.Objects;

/**
 * A text for players: a translation key and its parameters ({@code {p0}}, {@code {p1}}...). A parameter is a string
 * (a number is formatted by the caller) or another {@code ApiText}, translated in the player's language.
 *
 * @since 1.0
 */
public record ApiText(String key, List<Object> params) {
    /**
     * Refuses a missing key, and a parameter that is null or neither a string nor a text (it would not stay the
     * same, or could not be shown); keeps its own copy of the parameters.
     */
    public ApiText {
        Objects.requireNonNull(key, "key");
        params = List.copyOf(params);
        for (Object p : params) {
            if (!(p instanceof String || p instanceof ApiText)) {
                throw new IllegalArgumentException("a text parameter is a String or an ApiText, not " + p.getClass());
            }
        }
    }

    /** The text {@code key} with {@code params}. */
    public static ApiText of(String key, Object... params) {
        return new ApiText(key, List.of(params));
    }
}
