package dev.hycolony.core.building;

import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class Building {
    private final BuildingType type;
    private final BlockPos position;
    private final int rotation;
    private int level;
    private boolean built;
    private String customName = "";
    private String style = "";
    private final Map<String, BuildingModule> modules = new LinkedHashMap<>();
    private final Map<String, JsonObject> unknownModules = new LinkedHashMap<>();

    private Building(BuildingType type, BlockPos position, int rotation) {
        this.type = type;
        this.position = position;
        this.rotation = rotation;
    }

    /** New building at level 0 with one fresh instance of each module of its type. */
    public static Building create(BuildingType type, BlockPos position, int rotation) {
        Building b = new Building(type, position, rotation);
        for (ModuleProducer producer : type.modules()) {
            b.modules.put(producer.key(), producer.factory().get());
        }
        return b;
    }

    public <T extends BuildingModule> Optional<T> module(Class<T> kind) {
        return modules.values().stream().filter(kind::isInstance).map(kind::cast).findFirst();
    }

    public BuildingType type() { return type; }
    public BlockPos position() { return position; }
    public int rotation() { return rotation; }
    public int level() { return level; }
    public void setLevel(int level) { this.level = level; }
    public boolean isBuilt() { return built; }
    public void setBuilt(boolean built) { this.built = built; }
    public String customName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }
    public String style() { return style; }
    public void setStyle(String style) { this.style = style; }
    public Map<String, BuildingModule> modules() { return Collections.unmodifiableMap(modules); }
    /** Saved data of modules no longer registered for this type: written back untouched. */
    public Map<String, JsonObject> unknownModules() { return unknownModules; }
}
