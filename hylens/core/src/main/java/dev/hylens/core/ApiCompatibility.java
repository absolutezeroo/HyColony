package dev.hylens.core;

import dev.hycolony.api.ApiVersion;

/**
 * Which of HyColony's api versions HyLens runs with: the major and minor it was built against, any patch. A later
 * minor only adds to the stable api, but may change its @Experimental parts, which HyLens uses (spec 2026-09-30,
 * § 4.1, stability). The manifest pins HyColony's mod version, which a dev build does not bump: this reads the api's.
 */
public final class ApiCompatibility {
    /** The api HyLens is compiled against; {@link ApiVersion#CURRENT} is read from HyColony's jar at runtime. */
    public static final ApiVersion BUILT_AGAINST = new ApiVersion(1, 0, 0);

    private ApiCompatibility() {}

    /** Whether HyLens runs with {@code running}, the api HyColony provides. */
    public static boolean accepts(ApiVersion running) {
        return accepts(BUILT_AGAINST, running);
    }

    /** Whether an addon built against {@code built}, @Experimental parts included, runs with {@code running}. */
    static boolean accepts(ApiVersion built, ApiVersion running) {
        return running.major() == built.major() && running.minor() == built.minor();
    }
}
