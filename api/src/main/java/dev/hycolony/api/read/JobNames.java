package dev.hycolony.api.read;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.Experimental;
import java.util.Optional;

/**
 * A job's name in the player's language, as HyColony's own windows name jobs: HyColony keeps the translation, an addon
 * only shows it.
 *
 * @since 1.0
 */
@Experimental
public final class JobNames {
    private JobNames() {}

    /** The name of the job {@code id} ({@link CitizenSnapshot#job}); HyColony's "no job" without one. */
    public static ApiText of(Optional<String> id) {
        return ApiText.of("hycolony.ui.job."
                + id.map(j -> j.substring(j.indexOf(':') + 1)).orElse("none"));
    }
}
