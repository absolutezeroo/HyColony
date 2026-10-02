package dev.hylens.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.api.ApiVersion;
import org.junit.jupiter.api.Test;

/** Which of HyColony's api versions HyLens runs with (spec 2026-09-30, § 4.1, stability). */
class ApiCompatibilityTest {
    private static final ApiVersion BUILT = ApiCompatibility.BUILT_AGAINST;

    @Test
    void builtAgainstIsTheApiItCompilesWith() {
        assertEquals(ApiVersion.CURRENT, BUILT, "bump BUILT_AGAINST with the api HyLens is built against");
    }

    @Test
    void sameVersionIsAccepted() {
        assertTrue(ApiCompatibility.accepts(BUILT));
    }

    @Test
    void laterPatchIsAccepted() {
        assertTrue(ApiCompatibility.accepts(new ApiVersion(BUILT.major(), BUILT.minor(), BUILT.patch() + 1)));
    }

    @Test
    void laterMinorIsRefused() {
        assertFalse(
                ApiCompatibility.accepts(new ApiVersion(BUILT.major(), BUILT.minor() + 1, 0)),
                "HyLens uses the api's @Experimental parts, which a minor version may change");
    }

    @Test
    void earlierMinorIsRefused() {
        ApiVersion built = new ApiVersion(1, 2, 0);

        assertFalse(ApiCompatibility.accepts(built, new ApiVersion(1, 1, 5)), "it may lack what HyLens calls");
    }

    @Test
    void otherMajorIsRefused() {
        assertFalse(ApiCompatibility.accepts(new ApiVersion(BUILT.major() + 1, 0, 0)));
        assertFalse(ApiCompatibility.accepts(new ApiVersion(BUILT.major() - 1, BUILT.minor(), BUILT.patch())));
    }

    @Test
    void hyColonyOneOneIsRefusedLackingSpawnCitizenAndSetSaturation() {
        assertFalse(ApiCompatibility.accepts(new ApiVersion(1, 1, 0)));
    }
}
