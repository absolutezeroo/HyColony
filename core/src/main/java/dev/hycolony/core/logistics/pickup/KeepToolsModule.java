package dev.hycolony.core.logistics.pickup;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.catalog.ItemCatalog;
import dev.hycolony.core.kernel.item.ToolType;
import java.util.List;
import java.util.Set;

/**
 * One tool of each listed type, up to the building's max equipment level, stays in the building and in its worker's
 * inventory (MC {@code keepX.put(hasEquipmentLevel(type, TOOL_LEVEL_WOOD_OR_GOLD, getMaxEquipmentLevel()), (1,
 * true))}, set in each worker building's constructor, e.g. {@code BuildingBuilder}).
 */
public final class KeepToolsModule implements KeepsItems {
    private final Set<ToolType> types;

    public KeepToolsModule(Set<ToolType> types) {
        this.types = Set.copyOf(types);
    }

    @Override
    public List<KeepRule> keepRules(Colony colony, Building building) {
        ItemCatalog catalog = colony.context().ports().catalog();
        return types.stream()
                .map(type -> new KeepRule(
                        item -> catalog.tool(item)
                                .filter(t -> t.type() == type && t.level() <= building.maxEquipmentLevel())
                                .isPresent(),
                        1,
                        true))
                .toList();
    }
}
