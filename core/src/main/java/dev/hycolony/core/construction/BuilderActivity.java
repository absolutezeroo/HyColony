package dev.hycolony.core.construction;

import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Locale;

/**
 * The builder's current activity as one line for the citizen window (a diagnosis aid): e.g. "structure: block 102 -
 * walking" or "structure: block 102 - placing Wood_Deadwood_Roof". Keys are {@code hycolony.ai.builder.<activity>};
 * the stage is a nested translation ({@code %hycolony.ui.stage.<stage>}).
 */
final class BuilderActivity {
    private BuilderActivity() {}

    static Msg describe(BuilderState state, WorkOrder order, boolean walking, ItemKey inHand) {
        String activity = switch (state) {
            case IDLE, START_WORKING -> order == null ? "idle" : "starting";
            case LOAD_STRUCTURE -> "loading";
            case GATHERING_REQUIRED_MATERIALS -> "gathering";
            case NEEDS_ITEM -> "waiting";
            case INVENTORY_FULL -> "dumping";
            case COMPLETE_BUILD -> "completing";
            case BUILDING_STEP -> walking ? "walking" : "placing";
            case MINE_BLOCK -> walking ? "walking" : "breaking";
        };
        if (order == null || !(activity.equals("walking") || activity.equals("placing") || activity.equals("breaking"))) {
            return Msg.of("hycolony.ai.builder." + activity);
        }
        return Msg.of("hycolony.ai.builder." + activity,
                "%hycolony.ui.stage." + order.stage().name().toLowerCase(Locale.ROOT),
                String.valueOf(order.progressIndex()), inHand == null ? "-" : inHand.id());
    }
}
