package dev.hycolony.core.app.wand;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.HutPlacement;
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
     * MC EventHandler.onPlayerInteract for the hut item {@code hut} of type {@code buildingTypeId} placed at {@code
     * target}: first the hut's placing rules (handleEventCancellation; HutActions.checkPlacement), whose refusal is
     * returned with its reason and opens no window; then, in a colony where the player lacks ACCESS_HUTS, a silent
     * refusal (empty); then a creative player crouching gets the placement the rules allow; anyone else gets the
     * suggestion window, and empty. Empty means: cancel the placement, say nothing.
     *
     * <p>Deviation from MC: MC's onBlockHutPlaced lets a creative player place a hut outside any colony or a second
     * town hall; HutActions.checkPlacement, the port of AbstractBlockHut.canPaste, has no creative exception (M-26).
     * Deviation from MC: the colony is read at the placed cell, not the clicked one, which PlaceBlockEvent lacks. MC
     * also skips its storage components (IRSComponentBlock); none is ported yet, to exclude here when they are.
     */
    public Optional<HutPlacement> handPlaced(
            UUID player, BlockPos target, String buildingTypeId, ItemKey hut, boolean crouching) {
        HutPlacement rules = manager.huts().checkPlacement(player, target, buildingTypeId);
        if (rules instanceof HutPlacement.Denied) {
            return Optional.of(rules);
        }
        Optional<Colony> colony = manager.colonyAt(target);
        if (colony.isPresent() && !ColonyAccess.allows(colony.get(), player, Action.ACCESS_HUTS)) {
            return Optional.empty();
        }
        if (crouching && manager.context().players().isCreative(player)) {
            return Optional.of(rules);
        }
        manager.windows().ui().showSuggestBuildTool(player, new SuggestBuildToolView(target, hut));
        return Optional.empty();
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
