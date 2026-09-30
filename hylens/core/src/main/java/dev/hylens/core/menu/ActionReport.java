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
}
