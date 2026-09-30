package dev.hylens.core.send;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which operators' next map "Teleport" sends a citizen instead (spec 2026-09-30, § 6.6). */
class ArmedMapsTest {
    private static final UUID OPERATOR = UUID.randomUUID();

    private final ArmedMaps armed = new ArmedMaps();

    @Test
    void unarmedOperatorTeleportsAsUsual() {
        assertFalse(armed.use(OPERATOR));
    }

    @Test
    void armedMapServesOnceThenTeleportsAsUsual() {
        armed.arm(OPERATOR);

        assertTrue(armed.use(OPERATOR));
        assertFalse(armed.use(OPERATOR), "one click only");
    }

    @Test
    void disarmedMapTeleportsAsUsualOthersStayArmed() {
        UUID other = UUID.randomUUID();
        armed.arm(OPERATOR);
        armed.arm(other);

        armed.disarm(OPERATOR);

        assertFalse(armed.use(OPERATOR));
        assertTrue(armed.use(other));
    }

    @Test
    void operatorsAreArmedApart() {
        armed.arm(OPERATOR);

        assertFalse(armed.use(UUID.randomUUID()));
        assertTrue(armed.use(OPERATOR));
    }
}
