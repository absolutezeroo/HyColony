package dev.hyangler.plugin.bridge;

import com.hypixel.hytale.server.core.universe.world.World;

/**
 * The api's thread rule (spec § 8.2, as HyColony's api spec 2026-09-30 § 4.1): a call off its world's thread throws,
 * as Hytale's Store.assertThread does. Deviation from CLAUDE.md § 4 by design: this is no port, and such a call is an
 * addon's programming error.
 */
final class ApiThreads {
    private ApiThreads() {}

    /** Throws {@link IllegalStateException} off {@code world}'s thread. */
    static void check(World world) {
        if (!world.isInThread()) {
            throw new IllegalStateException("HyAngler's api was called outside the thread of world " + world.getName());
        }
    }
}
