package dev.hylens.core.menu;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.ApiText;

/** What the menu tells the operator of an action's result (spec 2026-09-30, § 4.1, actions). */
public final class ActionReport {
    private ActionReport() {}

    /** The text of {@code result}; a refusal carries HyColony's reason, nested. */
    public static ApiText text(ActionResult result) {
        return switch (result) {
            case ActionResult.Done _ -> ApiText.of("hylens.action.done");
            case ActionResult.Refused r -> ApiText.of("hylens.action.refused", r.reason());
            case ActionResult.NotFound _ -> ApiText.of("hylens.action.notFound");
            case ActionResult.Unavailable _ -> ApiText.of("hylens.action.unavailable");
        };
    }

    /** The text of a fulfil's {@code result}: as {@link #text}, but naming the request or what was missing. */
    public static ApiText fulfilled(ActionResult result) {
        return switch (result) {
            case ActionResult.NotFound _ -> ApiText.of("hylens.action.requestNotFound");
            case ActionResult.Unavailable _ -> ApiText.of("hylens.action.nothingToGive");
            default -> text(result);
        };
    }

    /** The text of a request system reset's {@code result}: as {@link #text}, but naming the colony when it is gone. */
    public static ApiText reset(ActionResult result) {
        return result instanceof ActionResult.NotFound ? ApiText.of("hylens.action.colonyNotFound") : text(result);
    }

    /** The text of a spawn's {@code result}: as {@link #text}, but naming the colony or town hall when it failed. */
    public static ApiText spawned(ActionResult result) {
        return switch (result) {
            case ActionResult.NotFound _ -> ApiText.of("hylens.action.colonyNotFound");
            case ActionResult.Unavailable _ -> ApiText.of("hylens.action.spawnUnavailable");
            default -> text(result);
        };
    }
}
