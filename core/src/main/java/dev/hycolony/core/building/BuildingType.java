package dev.hycolony.core.building;

import java.util.List;

/** Registry entry, like MineColonies' BuildingEntry. {@code hutBlockKey} is a logical id-map key. */
public record BuildingType(String id, String hutBlockKey, int maxLevel, List<ModuleProducer> modules) {
    public BuildingType {
        modules = List.copyOf(modules);
    }
}
