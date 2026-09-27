package dev.hycolony.core.job.work;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.ToolRequest;

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
}
