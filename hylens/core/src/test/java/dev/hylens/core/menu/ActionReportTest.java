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

    @Test
    void aFulfilThatCannotHappenSaysWhy() {
        assertEquals(ApiText.of("hylens.action.requestNotFound"), ActionReport.fulfilled(new ActionResult.NotFound()));
        assertEquals(ApiText.of("hylens.action.nothingToGive"), ActionReport.fulfilled(new ActionResult.Unavailable()));
        assertEquals(ApiText.of("hylens.action.done"), ActionReport.fulfilled(new ActionResult.Done()));
    }

    @Test
    void aResetOfAColonyGoneSaysSo() {
        assertEquals(ApiText.of("hylens.action.colonyNotFound"), ActionReport.reset(new ActionResult.NotFound()));
        assertEquals(ApiText.of("hylens.action.done"), ActionReport.reset(new ActionResult.Done()));
    }

    @Test
    void aSpawnThatCannotHappenSaysWhy() {
        assertEquals(
                ApiText.of("hylens.action.spawnUnavailable"), ActionReport.spawned(new ActionResult.Unavailable()));
        assertEquals(ApiText.of("hylens.action.colonyNotFound"), ActionReport.spawned(new ActionResult.NotFound()));
        assertEquals(ApiText.of("hylens.action.done"), ActionReport.spawned(new ActionResult.Done()));
    }
}
