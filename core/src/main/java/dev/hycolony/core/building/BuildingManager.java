package dev.hycolony.core.building;

import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BuildingManager {
    private final Map<BlockPos, Building> buildings = new LinkedHashMap<>();
    private final List<JsonObject> unknown = new ArrayList<>();

    public void add(Building building) {
        buildings.put(building.position(), building);
    }

    public Optional<Building> remove(BlockPos pos) {
        return Optional.ofNullable(buildings.remove(pos));
    }

    public Optional<Building> at(BlockPos pos) {
        return Optional.ofNullable(buildings.get(pos));
    }

    public Optional<Building> townHall() {
        return buildings.values().stream().filter(b -> b.type().equals(BuildingTypes.TOWN_HALL)).findFirst();
    }

    public Collection<Building> all() {
        return Collections.unmodifiableCollection(buildings.values());
    }

    public void onColonyTick() {
        for (Building building : buildings.values()) {
            for (BuildingModule module : building.modules().values()) {
                if (module instanceof TickingModule ticking) {
                    ticking.onColonyTick(building);
                }
            }
        }
    }

    /** Saved buildings whose type is not registered anymore: kept and written back untouched. */
    public void keepUnknown(JsonObject raw) {
        unknown.add(raw);
    }

    public List<JsonObject> unknown() {
        return Collections.unmodifiableList(unknown);
    }
}
