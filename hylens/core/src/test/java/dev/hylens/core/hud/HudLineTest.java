package dev.hylens.core.hud;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hycolony.api.ApiText;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A watch panel line holds a value only when it is a field. */
class HudLineTest {
    private static final ApiText TEXT = ApiText.of("hylens.hud.none");

    @Test
    void aLineThatIsNotAFieldRefusesAValue() {
        assertThrows(IllegalArgumentException.class, () -> new HudLine(HudLine.Kind.TITLE, TEXT, Optional.of(TEXT)));
    }

    @Test
    void aFieldRefusesToGoWithoutAValue() {
        assertThrows(IllegalArgumentException.class, () -> new HudLine(HudLine.Kind.FIELD, TEXT, Optional.empty()));
    }
}
