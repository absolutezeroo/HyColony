package dev.hycolony.plugin.api;

import org.jspecify.annotations.Nullable;

/**
 * Where {@link HyColonyApi#get} finds HyColony: filled at HyColony's setup, emptied at its shutdown. Only HyColony
 * installs it.
 *
 * @since 1.0
 */
public final class HyColonyApiHolder {
    private static volatile @Nullable HyColonyApi api;

    private HyColonyApiHolder() {}

    static HyColonyApi get() {
        HyColonyApi current = api;
        if (current == null) {
            throw new IllegalStateException("HyColony is not running: its api lives from its setup to its shutdown");
        }
        return current;
    }

    /** HyColony's setup: makes {@code installed} the api. */
    public static void install(HyColonyApi installed) {
        api = installed;
    }

    /** HyColony's shutdown: no api any more. */
    public static void clear() {
        api = null;
    }
}
