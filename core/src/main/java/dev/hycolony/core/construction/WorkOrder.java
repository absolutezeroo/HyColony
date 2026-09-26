package dev.hycolony.core.construction;

import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;

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
    private BlockPos claimedBy;
    private Stage stage;
    private int progressIndex;
    private boolean free;

    WorkOrder(
            int id,
            WorkOrderType type,
            BlockPos buildingPos,
            int targetLevel,
            int blueprintLevel,
            String style,
            int rotation) {
        this.id = id;
        this.type = type;
        this.buildingPos = buildingPos;
        this.targetLevel = targetLevel;
        this.blueprintLevel = blueprintLevel;
        this.style = style;
        this.rotation = rotation;
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

    public void setPriority(int p) {
        this.priority = p;
    }

    public Optional<BlockPos> claimedBy() {
        return Optional.ofNullable(claimedBy);
    }

    boolean isClaimedBy(BlockPos builderHut) {
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

    void setStage(Stage stage) {
        this.stage = stage;
    }

    void setProgressIndex(int progressIndex) {
        this.progressIndex = progressIndex;
    }

    void setFree(boolean free) {
        this.free = free;
    }

    /** BUILD starts by clearing the site, REMOVE by removing, UPGRADE and REPAIR build over what stands. */
    Stage initialStage() {
        return switch (type) {
            case BUILD -> Stage.CLEAR;
            case REMOVE -> Stage.REMOVE;
            case UPGRADE, REPAIR -> Stage.SOLID;
        };
    }

    /** Unclaimed, back to the first stage; blocks already placed stay. */
    void release() {
        claimedBy = null;
        stage = initialStage();
        progressIndex = 0;
    }

    public JsonObject write() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("type", type.name());
        o.add("pos", pos(buildingPos));
        o.addProperty("targetLevel", targetLevel);
        o.addProperty("blueprintLevel", blueprintLevel);
        o.addProperty("style", style);
        o.addProperty("rotation", rotation);
        o.addProperty("priority", priority);
        if (claimedBy != null) {
            o.add("claimedBy", pos(claimedBy));
        }
        o.addProperty("stage", stage.name());
        o.addProperty("progressIndex", progressIndex);
        o.addProperty("free", free);
        return o;
    }

    public static WorkOrder read(JsonObject o) {
        WorkOrder w = new WorkOrder(
                o.get("id").getAsInt(),
                WorkOrderType.valueOf(o.get("type").getAsString()),
                readPos(o.getAsJsonObject("pos")),
                o.get("targetLevel").getAsInt(),
                o.has("blueprintLevel")
                        ? o.get("blueprintLevel").getAsInt()
                        : o.get("targetLevel").getAsInt(),
                o.get("style").getAsString(),
                o.get("rotation").getAsInt());
        w.priority = o.get("priority").getAsInt();
        w.claimedBy = o.has("claimedBy") ? readPos(o.getAsJsonObject("claimedBy")) : null;
        w.stage = Stage.valueOf(o.get("stage").getAsString());
        w.progressIndex = o.get("progressIndex").getAsInt();
        w.free = o.has("free") && o.get("free").getAsBoolean();
        return w; // an old "requested" flag is ignored
    }

    private static JsonObject pos(BlockPos p) {
        JsonObject o = new JsonObject();
        o.addProperty("x", p.x());
        o.addProperty("y", p.y());
        o.addProperty("z", p.z());
        return o;
    }

    private static BlockPos readPos(JsonObject o) {
        return new BlockPos(
                o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
    }
}
