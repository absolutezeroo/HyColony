package dev.hycolony.core.app.ui;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.List;

/**
 * MC WindowBuildBuilding: the hut ({@code building}: allowed orders, upgrade warning), its style (MC lists only the
 * hut's own), the colony's builders nearest first, what the plan of the level it would build still needs, whether
 * that plan exists, and whether Pick Up shows (MC: at level 0, deconstructed or without a plan; never the town hall,
 * see Building.canBePickedUp).
 */
public record BuildOptionsView(
        BuildingView building,
        String style,
        List<BuilderChoice> builders,
        List<ItemAmount> resources,
        boolean blueprintFound,
        boolean showPickUp) {
    /** A builder hut with a worker, offered to take the order (MC updateBuilders). */
    public record BuilderChoice(BlockPos hut, String workerName) {}

    public BuildOptionsView {
        builders = List.copyOf(builders);
        resources = List.copyOf(resources);
    }
}
