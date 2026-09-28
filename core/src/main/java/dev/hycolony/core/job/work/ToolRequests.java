package dev.hycolony.core.job.work;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.ToolRequest;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * A worker's requests for a missing or broken tool (MC AbstractEntityAIBasic.checkForToolOrWeapon), filed under its
 * hut with the citizen's id (sync: the worker waits for it). Any job composes one around its citizen and hut.
 */
public final class ToolRequests {
    private final Colony colony;
    private final CitizenData citizen;
    private final Building hut;

    public ToolRequests(Colony colony, CitizenData citizen, Building hut) {
        this.colony = colony;
        this.citizen = citizen;
        this.hut = hut;
    }

    /**
     * One ToolRequest(type, 0, hut max equipment level) unless one of that type is live (MC checkForToolOrWeapon:
     * {@code Tool(type, TOOL_LEVEL_WOOD_OR_GOLD, max(maxEquip, min))}; min is 0, so the max is maxEquip).
     */
    public void requestTool(ToolType type) {
        for (Request r : colony.requests().byRequester(hut.requesterId())) {
            if (r.requestable() instanceof ToolRequest t && t.type() == type) {
                return;
            }
        }
        colony.requests().createAndAssign(hut, new ToolRequest(type, 0, hut.maxEquipmentLevel()), citizen.id());
    }

    /**
     * MC checkForToolOrWeapon(type) with checkForNeededTool: whether the worker lacks a usable tool of {@code type}.
     * With none in its inventory it walks to the hut ({@code walkToHut}, true once there) and takes one from there;
     * with none there either, or while still walking, it asks for one ({@link #requestTool}) and true is returned.
     */
    public boolean missing(ToolType type, WorkerStock stock, BooleanSupplier walkToHut) {
        if (stock.toolInInventory(type).isPresent()) {
            return false;
        }
        if (walkToHut.getAsBoolean()) {
            Optional<ItemKey> inHut = stock.toolInHut(type);
            if (inHut.isPresent() && stock.take(inHut.get(), 1) > 0) {
                return false;
            }
        }
        requestTool(type);
        return true;
    }
}
