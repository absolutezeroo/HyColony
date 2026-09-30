package dev.hycolony.api.read;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.ApiText;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A job's name in the player's language, as HyColony's windows name jobs. */
class JobNamesTest {
    @Test
    void jobIdNamesItsTranslation() {
        assertEquals(ApiText.of("hycolony.ui.job.deliveryman"), JobNames.of(Optional.of("hycolony:deliveryman")));
    }

    @Test
    void idWithoutNamespaceIsTakenWhole() {
        assertEquals(ApiText.of("hycolony.ui.job.farmer"), JobNames.of(Optional.of("farmer")));
    }

    @Test
    void noJobIsNamedAsSuch() {
        assertEquals(ApiText.of("hycolony.ui.job.none"), JobNames.of(Optional.empty()));
    }
}
