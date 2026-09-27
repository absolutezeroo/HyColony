package dev.hycolony.core.kernel.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FeatureFlagsTest {

    @Test
    void aPackAbsentFromTheConfigKeepsItsManifestDefault() {
        FeatureFlags flags = new FeatureFlags(Map.of("Other", false));

        assertTrue(flags.enabled("Styles_Outlander", true));
        assertFalse(flags.enabled("Styles_Outlander", false));
    }

    @Test
    void theConfigFlagOverridesTheManifestDefault() {
        FeatureFlags flags = new FeatureFlags(Map.of("Styles_Outlander", false, "Extra", true));

        assertFalse(flags.enabled("Styles_Outlander", true));
        assertTrue(flags.enabled("Extra", false));
    }

    @Test
    void aFlagThatIsNotABooleanFallsBackToTheManifestDefault() {
        FeatureFlags flags = new FeatureFlags(Map.of("Styles_Outlander", "no", "Styles_Kweebec", 0));

        assertTrue(flags.enabled("Styles_Outlander", true));
        assertFalse(flags.enabled("Styles_Kweebec", false));
    }
}
