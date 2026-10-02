package dev.hycolony.core.building;

import dev.hycolony.core.building.module.ModuleProducer;
import java.util.List;

/** Registry entry, like MineColonies' BuildingEntry. {@code hutBlockKey} is a logical id-map key. */
public record BuildingType(String id, String hutBlockKey, int maxLevel, List<ModuleProducer> modules) {
    public BuildingType {
        modules = List.copyOf(modules);
    }

    /** {@code name} as a message parameter: a HyColony hut type's translated key, any other name as is. */
    public static String nameParam(String name) {
        return name.startsWith("hycolony:")
                ? "%hycolony.ui.building.type." + name.substring("hycolony:".length())
                : name;
    }
}
