package dev.hycolony.core.logistics.pickup;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.List;
import java.util.Set;

/**
 * One tool of each listed type, usable at the building's level, stays in the building and in its worker's inventory
 * (MC {@code keepX.put(hasEquipmentLevel(type, TOOL_LEVEL_WOOD_OR_GOLD, getMaxEquipmentLevel()), (1, true))}, set in
 * each worker building's constructor, e.g. {@code BuildingBuilder}).
 *
 * <p>Deviation from MC: the max level is the hut level, the range the builder's tool requests already use, rather
 * than MC {@code getMaxEquipmentLevel} (L0 = 1, L5 = unlimited).
 */
public final class KeepToolsModule implements KeepsItems {
    private final Set<ToolType> types;

    public KeepToolsModule(Set<ToolType> types) {
        this.types = Set.copyOf(types);
    }

    @Override
    public List<KeepRule> keepRules(Building building, ItemCatalog catalog) {
        return types.stream()
                .map(type -> new KeepRule(
                        item -> catalog.tool(item)
                                .filter(t -> t.type() == type && t.level() <= building.level())
                                .isPresent(),
                        1,
                        true))
                .toList();
    }
}
