package dev.hylens.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.ApiText;
import org.junit.jupiter.api.Test;

/** What the menu says of an action's result (spec 2026-09-30, § 4.1, actions). */
class ActionReportTest {
    @Test
    void eachResultHasItsText() {
        assertEquals(ApiText.of("hylens.action.done"), ActionReport.text(new ActionResult.Done()));
        assertEquals(ApiText.of("hylens.action.notFound"), ActionReport.text(new ActionResult.NotFound()));
        assertEquals(ApiText.of("hylens.action.unavailable"), ActionReport.text(new ActionResult.Unavailable()));
    }

    @Test
    void refusalShowsHyColonysReason() {
        ApiText reason = ApiText.of("hycolony.debug.refused.colony");

        assertEquals(ApiText.of("hylens.action.refused", reason), ActionReport.text(new ActionResult.Refused(reason)));
    }
}
