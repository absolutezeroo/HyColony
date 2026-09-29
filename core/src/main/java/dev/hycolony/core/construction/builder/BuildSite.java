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
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The structure one builder works on (MC AbstractEntityAIStructure's current structure): its order, plan and target
 * building, loaded once per order. The order itself owns the progress, written through the hut's resources module.
 */
final class BuildSite {
    private final Colony colony;
    private final BuildingResourcesModule resources;
    private final WorkSpot spots;

    private static final String NOT_LOADED = "no structure loaded";

    // Null when no structure is loaded: the accessors below but order() are only called once loaded().
    private @Nullable WorkOrder order;
    private @Nullable StructurePlan plan;
    /** An UPGRADE's previous level, whose leftovers {@link Stage#CLEAR_LEFTOVERS} mines. */
    private @Nullable StructurePlan previousPlan;

    private @Nullable Building target;
    /** The walk over SOLID and DECORATE after the last stage ran for the loaded order. */
    private boolean finalCheckDone;

    BuildSite(Colony colony, BuildingResourcesModule resources, WorkSpot spots) {
        this.colony = colony;
        this.resources = resources;
        this.spots = spots;
    }

    /** {@code previous} is the plan of the level an UPGRADE replaces, null for other orders. */
    void load(WorkOrder o, Building b, StructurePlan p, @Nullable StructurePlan previous) {
        order = o;
        target = b;
        plan = p;
        previousPlan = previous;
    }

    /** Forgets the structure and the module's order (completion, cancellation, failure). */
    void clear() {
        order = null;
        plan = null;
        previousPlan = null;
        target = null;
        finalCheckDone = false;
        resources.reset();
    }

    boolean loaded() {
        return plan != null;
    }

    /** The loaded order, null when none is. */
    @Nullable
    WorkOrder order() {
        return order;
    }

    /** The loaded order, for callers that only run once {@link #loaded()}. */
    WorkOrder loadedOrder() {
        return Objects.requireNonNull(order, NOT_LOADED);
    }

    StructurePlan plan() {
        return Objects.requireNonNull(plan, NOT_LOADED);
    }

    /** The replaced level's plan; only called for positions of {@link Stage#CLEAR_LEFTOVERS}, never null there. */
    StructurePlan previousPlan() {
        return Objects.requireNonNull(previousPlan, "no previous level");
    }

    Building target() {
        return Objects.requireNonNull(target, NOT_LOADED);
    }

    boolean finalCheckDone() {
        return finalCheckDone;
    }

    void startFinalCheck() {
        finalCheckDone = true;
    }

    List<BlockPos> positions(Stage stage) {
        return switch (stage) {
            case CLEAR -> plan().clearList();
            case SOLID -> plan().solidPositions();
            case DECORATE -> plan().decoPositions();
            case REMOVE -> plan().removeList();
            case CLEAR_LEFTOVERS -> leftovers();
            case DONE -> List.of();
        };
    }

    /**
     * CLEAR_LEFTOVERS' positions: a MineColonies plan's air cells, as MC CLEAR_NON_SOLIDS visits (its levels share
     * one footprint, and its substitutions keep what is there); else the previous level's blocks (see {@link Stage}).
     */
    private List<BlockPos> leftovers() {
        if (plan().hasMarkers()) {
            return plan().airPositions();
        }
        return previousPlan == null ? List.of() : previousPlan.removeList();
    }

    /** The planned block at index {@code i} of SOLID or DECORATE. */
    BlueprintEntry entry(Stage stage, int i) {
        return (stage == Stage.SOLID ? plan().solidList() : plan().decoList()).get(i);
    }

    /** Where the builder stands to work on {@code block}. */
    WorkSpot.Spot workSpot(BlockPos block) {
        return spots.choose(block, loadedOrder().buildingPos(), plan());
    }

    void progress(Stage stage, int index) {
        resources.progress(stage, index);
        colony.markDirty();
    }
}
