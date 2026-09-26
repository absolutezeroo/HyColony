package dev.hycolony.core.construction.builder;

import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The builder's current activity as one line for the citizen window (a diagnosis aid): e.g. "structure: block 102 -
 * walking" or "structure: block 102 - placing Wood_Deadwood_Roof". Keys are {@code hycolony.ai.builder.<activity>};
 * the stage is a nested translation ({@code %hycolony.ui.stage.<stage>}).
 */
final class BuilderActivity {
    /** Each state's activity once an order is loaded, the builder standing still. */
    private static final Map<BuilderState, String> ACTIVITIES = Map.of(
            BuilderState.IDLE, "starting",
            BuilderState.START_WORKING, "starting",
            BuilderState.LOAD_STRUCTURE, "loading",
            BuilderState.GATHERING_REQUIRED_MATERIALS, "gathering",
            BuilderState.NEEDS_ITEM, "waiting",
            BuilderState.INVENTORY_FULL, "dumping",
            BuilderState.COMPLETE_BUILD, "completing",
            BuilderState.BUILDING_STEP, "placing",
            BuilderState.MINE_BLOCK, "breaking");
    /** The states working on one block: their line also names the stage, the block index and the held item. */
    private static final Set<BuilderState> AT_A_BLOCK = EnumSet.of(BuilderState.BUILDING_STEP, BuilderState.MINE_BLOCK);

    private BuilderActivity() {}

    static Msg describe(BuilderState state, WorkOrder order, boolean walking, ItemKey inHand) {
        String key = "hycolony.ai.builder." + activity(state, order, walking);
        if (order == null || !AT_A_BLOCK.contains(state)) {
            return Msg.of(key);
        }
        return Msg.of(
                key,
                "%hycolony.ui.stage." + order.stage().name().toLowerCase(Locale.ROOT),
                String.valueOf(order.progressIndex()),
                inHand == null ? "-" : inHand.id());
    }

    private static String activity(BuilderState state, WorkOrder order, boolean walking) {
        if (order == null && (state == BuilderState.IDLE || state == BuilderState.START_WORKING)) {
            return "idle";
        }
        return walking && AT_A_BLOCK.contains(state) ? "walking" : ACTIVITIES.get(state);
    }
}
