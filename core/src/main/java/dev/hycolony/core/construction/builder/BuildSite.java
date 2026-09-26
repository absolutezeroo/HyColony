package dev.hycolony.core.construction.builder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.kernel.BlockPos;
import java.util.List;

/**
 * The structure one builder works on (MC AbstractEntityAIStructure's current structure): its order, plan and target
 * building, loaded once per order. The order itself owns the progress, written through the hut's resources module.
 */
final class BuildSite {
    private final Colony colony;
    private final BuildingResourcesModule resources;
    private final WorkSpot spots;

    // Null when no structure is loaded.
    private WorkOrder order;
    private StructurePlan plan;
    private Building target;
    /** The walk over SOLID and DECORATE after the last stage ran for the loaded order. */
    private boolean finalCheckDone;

    BuildSite(Colony colony, BuildingResourcesModule resources, WorkSpot spots) {
        this.colony = colony;
        this.resources = resources;
        this.spots = spots;
    }

    void load(WorkOrder o, Building b, StructurePlan p) {
        order = o;
        target = b;
        plan = p;
    }

    /** Forgets the structure and the module's order (completion, cancellation, failure). */
    void clear() {
        order = null;
        plan = null;
        target = null;
        finalCheckDone = false;
        resources.reset();
    }

    boolean loaded() {
        return plan != null;
    }

    WorkOrder order() {
        return order;
    }

    StructurePlan plan() {
        return plan;
    }

    Building target() {
        return target;
    }

    boolean finalCheckDone() {
        return finalCheckDone;
    }

    void startFinalCheck() {
        finalCheckDone = true;
    }

    List<BlockPos> positions(Stage stage) {
        return switch (stage) {
            case CLEAR -> plan.clearList();
            case SOLID -> plan.solidPositions();
            case DECORATE -> plan.decoPositions();
            case REMOVE -> plan.removeList();
            case DONE -> List.of();
        };
    }

    /** The planned block at index {@code i} of SOLID or DECORATE. */
    BlueprintEntry entry(Stage stage, int i) {
        return (stage == Stage.SOLID ? plan.solidList() : plan.decoList()).get(i);
    }

    /** Where the builder stands to work on {@code block}. */
    WorkSpot.Spot workSpot(BlockPos block) {
        return spots.choose(block, order.buildingPos(), plan);
    }

    void progress(Stage stage, int index) {
        resources.progress(stage, index);
        colony.markDirty();
    }
}
