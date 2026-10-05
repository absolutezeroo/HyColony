package dev.hyangler.plugin.api;

import org.jspecify.annotations.Nullable;

/**
 * Where {@link HyAnglerApi#get} finds HyAngler: filled at HyAngler's setup, emptied at its shutdown. Only HyAngler
 * installs it.
 *
 * @since 1.0
 */
public final class HyAnglerApiHolder {
    private static volatile @Nullable HyAnglerApi api;

    private HyAnglerApiHolder() {}

    static HyAnglerApi get() {
        HyAnglerApi current = api;
        if (current == null) {
            throw new IllegalStateException("HyAngler is not running: its api lives from its setup to its shutdown");
        }
        return current;
    }

    /** HyAngler's setup: makes {@code installed} the api. */
    public static void install(HyAnglerApi installed) {
        api = installed;
    }

    /** HyAngler's shutdown: no api any more. */
    public static void clear() {
        api = null;
    }
}
