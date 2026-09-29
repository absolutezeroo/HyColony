package dev.hycolony.core.building;

import com.google.gson.JsonObject;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.model.RequesterId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BuildingManager {
    /** Told about every building added or removed (the colony plugs them into its request manager). */
    public interface Listener {
        void added(Building building);

        void removed(Building building);
    }

    private final Listener listener;
    private final Map<BlockPos, Building> buildings = new LinkedHashMap<>();
    private final Map<RequesterId, Building> byRequester = new HashMap<>();
    private final List<JsonObject> unknown = new ArrayList<>();

    public BuildingManager() {
        this(new Listener() {
            @Override
            public void added(Building building) {}

            @Override
            public void removed(Building building) {}
        });
    }

    public BuildingManager(Listener listener) {
        this.listener = listener;
    }

    /** The listener runs first: if it rejects the building, nothing is added. */
    public void add(Building building) {
        listener.added(building);
        buildings.put(building.position(), building);
        byRequester.put(building.requesterId(), building);
    }

    /** The listener runs first, while the building can still be found (its requests are cancelled). */
    public Optional<Building> remove(BlockPos pos) {
        Building removed = buildings.get(pos);
        if (removed != null) {
            listener.removed(removed);
            buildings.remove(pos);
            byRequester.remove(removed.requesterId());
        }
        return Optional.ofNullable(removed);
    }

    public Optional<Building> byRequester(RequesterId id) {
        return Optional.ofNullable(byRequester.get(id));
    }

    /** The building whose hut block or registered containers include {@code pos}. */
    public Optional<Building> owningContainer(BlockPos pos) {
        Building hut = buildings.get(pos);
        if (hut != null) {
            return Optional.of(hut);
        }
        // ponytail: scans a few buildings x few containers; index it if colonies grow large.
        return buildings.values().stream()
                .filter(b -> b.registeredBlocks().containers().contains(pos))
                .findFirst();
    }

    public Optional<Building> at(BlockPos pos) {
        return Optional.ofNullable(buildings.get(pos));
    }

    public Optional<Building> townHall() {
        return buildings.values().stream()
                .filter(b -> b.type().equals(BuildingTypes.TOWN_HALL))
                .findFirst();
    }

    public Collection<Building> all() {
        return Collections.unmodifiableCollection(buildings.values());
    }

    public void onColonyTick(Colony colony) {
        for (Building building : buildings.values()) {
            for (BuildingModule module : building.modules().values()) {
                if (module instanceof TickingModule ticking) {
                    ticking.onColonyTick(colony, building);
                }
            }
        }
    }

    /**
     * Saved buildings this build cannot load (a type not registered anymore, or an unreadable position): kept and
     * written back untouched.
     */
    public void keepUnknown(JsonObject raw) {
        unknown.add(raw);
    }

    public List<JsonObject> unknown() {
        return Collections.unmodifiableList(unknown);
    }
}
