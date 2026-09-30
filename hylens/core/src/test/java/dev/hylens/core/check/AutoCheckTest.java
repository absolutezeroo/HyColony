package dev.hylens.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.debug.Violation;
import dev.hylens.core.menu.Menus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Turning an operator's automatic check on or off (spec 2026-09-30, § 6.5). */
class AutoCheckTest {
    private static final UUID OPERATOR = UUID.randomUUID();
    private static final ColonyRef COLONY = new ColonyRef("default", 1);
    private static final Violation STALE = new Violation(
            "JOB_STEP_STALE",
            ApiText.of("hycolony.debug.violation.jobStepStale", "PICKUP", "300"),
            Optional.of(new CitizenRef(COLONY, 4)),
            Optional.empty());

    private final Menus menus = new Menus();
    private final NewAlerts alerts = new NewAlerts();

    @Test
    void toggleTurnsItOnThenOff() {
        assertTrue(AutoCheck.toggle(menus, alerts, OPERATOR));
        assertTrue(menus.state(OPERATOR).autoCheck());
        assertFalse(AutoCheck.toggle(menus, alerts, OPERATOR));
        assertFalse(menus.state(OPERATOR).autoCheck());
    }

    @Test
    void turningItOnTellsEveryLastingViolationAfresh() {
        AutoCheck.toggle(menus, alerts, OPERATOR);
        alerts.fresh(OPERATOR, COLONY, List.of(STALE));
        AutoCheck.toggle(menus, alerts, OPERATOR);
        alerts.fresh(OPERATOR, COLONY, List.of(STALE)); // a round racing the switch-off, as a disconnect may

        AutoCheck.toggle(menus, alerts, OPERATOR);

        assertEquals(List.of(STALE), alerts.fresh(OPERATOR, COLONY, List.of(STALE)));
    }
}
