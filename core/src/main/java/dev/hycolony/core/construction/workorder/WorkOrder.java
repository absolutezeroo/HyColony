package dev.hycolony.core.construction.workorder;

import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.persist.SavedJson;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** One build/upgrade/repair/remove job on a building. Port of MineColonies' WorkOrderBuilding. */
public final class WorkOrder {
    private final int id;
    private final WorkOrderType type;
    private final BlockPos buildingPos;
    private final int targetLevel;
    private final int blueprintLevel;
    private final String style;
    private final int rotation;
    private int priority;
    private @Nullable BlockPos claimedBy;
    private Stage stage;
    private int progressIndex;
    private boolean free;
    private boolean active;

    /** The blueprint an order follows: its style, level (see {@link #blueprintLevel()}) and rotation. */
    record Layout(String style, int blueprintLevel, int rotation) {}

    WorkOrder(int id, WorkOrderType type, BlockPos buildingPos, int targetLevel, Layout layout) {
        this.id = id;
        this.type = type;
        this.buildingPos = buildingPos;
        this.targetLevel = targetLevel;
        this.blueprintLevel = layout.blueprintLevel();
        this.style = layout.style();
        this.rotation = layout.rotation();
        this.stage = initialStage();
    }

    public int id() {
        return id;
    }

    public WorkOrderType type() {
        return type;
    }

    public BlockPos buildingPos() {
        return buildingPos;
    }

    public int targetLevel() {
        return targetLevel;
    }
    /**
     * Level of the plan the builder follows: the target for BUILD/UPGRADE/REPAIR, the current level for REMOVE
     * (whose target is 0, as in MineColonies, so any builder may take it). Completing a REMOVE marks the building
     * deconstructed without lowering its level.
     */
    public int blueprintLevel() {
        return blueprintLevel;
    }

    public String style() {
        return style;
    }

    public int rotation() {
        return rotation;
    }

    public int priority() {
        return priority;
    }

    void setPriority(int p) {
        this.priority = p;
    }

    public Optional<BlockPos> claimedBy() {
        return Optional.ofNullable(claimedBy);
    }

    public boolean isClaimedBy(BlockPos builderHut) {
        return builderHut.equals(claimedBy);
    }

    public Stage stage() {
        return stage;
    }

    public int progressIndex() {
        return progressIndex;
    }
    /** Built without materials: nothing is requested, fetched or consumed. Set at creation, never for REMOVE. */
    public boolean free() {
        return free;
    }

    void setClaimedBy(BlockPos builderHut) {
        this.claimedBy = builderHut;
    }

    /** The claimer works on this order (MC AbstractBuildingStructureBuilder.workOrderId); its others are queued. */
    boolean active() {
        return active;
    }

    void activate() {
        active = true;
    }

    /** An old save's order already under way, preferred when a builder's active order is chosen. */
    boolean started() {
        return stage != initialStage() || progressIndex > 0;
    }

    /** Where the builder stands in the order; public for the building's resources module, which mirrors it. */
    public void progress(Stage stage, int progressIndex) {
        this.stage = stage;
        this.progressIndex = progressIndex;
    }

    void setFree(boolean free) {
        this.free = free;
    }

    /**
     * BUILD and REPAIR start by clearing the site, REMOVE by removing, UPGRADE builds over what stands (MC
     * AbstractEntityAIStructure.loadStructure: CLEAR only for a level-0 building).
     */
    Stage initialStage() {
        return switch (type) {
            // Deviation from MC: a REPAIR clears too (MC skips CLEAR for a built building), so the blocks players put
            // in the wrong place inside the plan's box are removed.
            case BUILD, REPAIR -> Stage.CLEAR;
            case REMOVE -> Stage.REMOVE;
            case UPGRADE -> Stage.SOLID;
        };
    }

    /** Unclaimed, back to the first stage; blocks already placed stay. */
    void release() {
        claimedBy = null;
        active = false;
        stage = initialStage();
        progressIndex = 0;
    }

    public JsonObject write() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("type", type.name());
        o.add("pos", SavedJson.pos(buildingPos));
        o.addProperty("targetLevel", targetLevel);
        o.addProperty("blueprintLevel", blueprintLevel);
        o.addProperty("style", style);
        o.addProperty("rotation", rotation);
        o.addProperty("priority", priority);
        if (claimedBy != null) {
            o.add("claimedBy", SavedJson.pos(claimedBy));
        }
        o.addProperty("stage", stage.name());
        o.addProperty("progressIndex", progressIndex);
        o.addProperty("free", free);
        o.addProperty("active", active);
        return o;
    }

    /**
     * The saved order; empty when what it is cannot be read (§ 5): its id, a type and stage this build knows, its
     * building's position or its level. A missing optional key takes its default.
     */
    public static Optional<WorkOrder> read(JsonObject o) {
        Optional<WorkOrderType> type = SavedJson.enumOf(WorkOrderType.class, o.get("type"));
        Optional<Stage> stage = SavedJson.enumOf(Stage.class, o.get("stage"));
        Optional<BlockPos> pos = SavedJson.tryPos(o.get("pos"));
        int id = SavedJson.intOr(o.get("id"), 0); // ids start at 1: 0 means no order
        int targetLevel = SavedJson.intOr(o.get("targetLevel"), -1);
        String style = SavedJson.stringOr(o.get("style"), "");
        if (type.isEmpty() || stage.isEmpty() || pos.isEmpty() || id <= 0 || targetLevel < 0) {
            return Optional.empty();
        }
        WorkOrder w = new WorkOrder(
                id,
                type.get(),
                pos.get(),
                targetLevel,
                new Layout(
                        style,
                        SavedJson.intOr(o.get("blueprintLevel"), targetLevel),
                        SavedJson.intOr(o.get("rotation"), 0)));
        w.priority = SavedJson.intOr(o.get("priority"), 0);
        w.claimedBy = SavedJson.readPos(o.get("claimedBy"));
        w.stage = stage.get();
        w.progressIndex = SavedJson.intOr(o.get("progressIndex"), 0);
        w.free = SavedJson.boolOr(o.get("free"), false);
        w.active = SavedJson.boolOr(o.get("active"), false);
        return Optional.of(w); // an old "requested" flag is ignored
    }
}
