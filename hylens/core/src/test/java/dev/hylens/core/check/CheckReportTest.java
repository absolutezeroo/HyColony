package dev.hylens.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.debug.Violation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What /hylens check tells in the chat (spec 2026-09-30, § 6.5). */
class CheckReportTest {
    private static final ColonyRef COLONY = new ColonyRef("default", 1);
    private static final CitizenRef ANN = new CitizenRef(COLONY, 4);
    private static final ApiText DETAIL = ApiText.of("hycolony.debug.violation.queueEmpty", "DELIVERY");

    @Test
    void healthyColonySaysSo() {
        assertEquals(
                List.of(ApiText.of("hylens.check.healthy", "Alpha")), CheckReport.colony("Alpha", List.of(), Map.of()));
    }

    @Test
    void eachViolationNamesItsCitizenOrItsPlace() {
        Violation ofAnn = new Violation("QUEUE_INCOHERENT", DETAIL, Optional.of(ANN), Optional.empty());
        Violation atPlace = new Violation("WALK_ENDED_AWAY", DETAIL, Optional.empty(), Optional.of(new Pos(3, 64, -2)));
        Violation bare = new Violation("REQUEST_UNRESOLVED", DETAIL, Optional.empty(), Optional.empty());

        assertEquals(
                List.of(
                        ApiText.of("hylens.check.header", "Alpha", "3"),
                        ApiText.of("hylens.check.citizen", "Ann", DETAIL),
                        ApiText.of("hylens.check.place", "3 64 -2", DETAIL),
                        ApiText.of("hylens.check.plain", DETAIL)),
                CheckReport.colony("Alpha", List.of(ofAnn, atPlace, bare), Map.of(ANN, "Ann")));
    }

    @Test
    void citizenWithoutAKnownNameShowsItsId() {
        Violation ofAnn = new Violation("QUEUE_INCOHERENT", DETAIL, Optional.of(ANN), Optional.empty());

        assertEquals(
                ApiText.of("hylens.check.citizen", "#4", DETAIL),
                CheckReport.colony("Alpha", List.of(ofAnn), Map.of()).get(1));
    }

    @Test
    void newViolationSaysItsColony() {
        Violation ofAnn = new Violation("QUEUE_INCOHERENT", DETAIL, Optional.of(ANN), Optional.empty());

        assertEquals(
                ApiText.of("hylens.check.new", "Alpha", ApiText.of("hylens.check.citizen", "Ann", DETAIL)),
                CheckReport.fresh("Alpha", ofAnn, Map.of(ANN, "Ann")));
    }
}
