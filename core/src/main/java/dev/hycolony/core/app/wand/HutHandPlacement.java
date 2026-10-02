package dev.hycolony.core.app.wand;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.SuggestBuildToolView;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import java.util.UUID;

/**
 * A hut block placed by hand, the town hall's included (MC EventHandler.onPlayerInteract on an AbstractBlockHut): it is
 * refused and the build tool suggested (MC WindowSuggestBuildTool), unless a creative player places it crouching.
 * Built by its caller, like {@code FieldActions}.
 */
public final class HutHandPlacement {
    private final ColonyManager manager;
    private final WandActions wand;
    private final ItemKey buildTool;

    /** {@code buildTool}: the build tool's item, which only the plugin's id map knows. */
    public HutHandPlacement(ColonyManager manager, WandActions wand, ItemKey buildTool) {
        this.manager = manager;
        this.wand = wand;
        this.buildTool = buildTool;
    }

    /**
     * Whether {@code player} may place the hut item {@code hut} at {@code target} as it is: refused in a colony where
     * it lacks ACCESS_HUTS, allowed for a creative player crouching, else refused with the suggestion window shown.
     * The placing rules of the hut (HutActions.checkPlacement) still apply after a true.
     */
    public boolean handPlaced(UUID player, BlockPos target, ItemKey hut, boolean crouching) {
        Optional<Colony> colony = manager.colonyAt(target);
        if (colony.isPresent() && !ColonyAccess.allows(colony.get(), player, Action.ACCESS_HUTS)) {
            return false;
        }
        if (crouching && manager.context().players().isCreative(player)) {
            return true;
        }
        manager.windows().ui().showSuggestBuildTool(player, new SuggestBuildToolView(target, hut));
        return false;
    }

    /**
     * MC WindowSuggestBuildTool's "Use build tool": without a build tool in the inventory, MC's missing tool message
     * and false; otherwise the hut and the build tool swap their slots (MC SwitchBuildingWithToolMessage) and the tool
     * opens at {@code pos}, its ghost there unless it already stands somewhere (ST's window constructor).
     */
    public boolean useBuildTool(UUID player, BlockPos pos, ItemKey hut) {
        if (manager.context().ports().playerInventory().count(player, buildTool) < 1) {
            manager.context().notifier().send(player, Msg.of("hycolony.wand.missingTool"));
            return false;
        }
        manager.context().ports().playerInventory().swapIntoHotbar(player, hut, buildTool);
        return wand.open(player, Optional.of(pos));
    }
}
