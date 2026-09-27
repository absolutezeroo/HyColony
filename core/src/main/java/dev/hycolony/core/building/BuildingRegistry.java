package dev.hycolony.core.building;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BuildingRegistry {
    private final Map<String, BuildingType> byId = new LinkedHashMap<>();

    public void register(BuildingType type) {
        if (byId.putIfAbsent(type.id(), type) != null) {
            throw new IllegalArgumentException("Duplicate building type " + type.id());
        }
    }

    public Optional<BuildingType> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Every registered type, in registration order. */
    public List<BuildingType> all() {
        return List.copyOf(byId.values());
    }

    public Optional<BuildingType> byHutKey(String hutBlockKey) {
        return byId.values().stream()
                .filter(t -> t.hutBlockKey().equals(hutBlockKey))
                .findFirst();
    }
}
