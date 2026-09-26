package dev.hycolony.core.kernel.port;

import java.util.List;

/**
 * A translatable message: an i18n key and positional parameters. A parameter starting with {@code %} is itself a key,
 * translated in the player's language (as {@code %key} in .ui files); any other parameter is shown as is.
 */
public record Msg(String key, List<String> params) {
    public Msg {
        params = List.copyOf(params);
    }

    public static Msg of(String key, String... params) {
        return new Msg(key, List.of(params));
    }
}
