package dev.hycolony.core.request.model;

import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.Objects;
import java.util.Optional;

/** A single tool of {@code type} with a level in [minLevel, maxLevel]. */
public record ToolRequest(ToolType type, int minLevel, int maxLevel) implements Deliverable {
    /** MC EquipmentLevelConstants.TOOL_LEVEL_MAXIMUM: any tool level. */
    public static final int ANY_LEVEL = Integer.MAX_VALUE;

    public ToolRequest {
        Objects.requireNonNull(type, "type");
    }

    @Override
    public boolean matches(ItemKey item, ItemCatalog catalog) {
        return catalog.tool(item)
                .filter(t -> t.type() == type && t.level() >= minLevel && t.level() <= maxLevel)
                .isPresent();
    }

    /** The first of the catalog's tools, by id, that it accepts (MC ToolRequest's display stacks: the tools fit). */
    @Override
    public Optional<ItemKey> displayed(ItemCatalog catalog) {
        return catalog.tools().stream().filter(t -> matches(t, catalog)).findFirst();
    }

    @Override
    public int count() {
        return 1;
    }

    @Override
    public int minCount() {
        return 1;
    }

    /** A tool request is always for exactly one tool. */
    @Override
    public ToolRequest withCount(int count) {
        return this;
    }

    @Override
    public boolean canBeResolvedByBuilding() {
        return true;
    }

    @Override
    public String describe() {
        return "1 x " + type + " (level " + minLevel + "-" + maxLevel + ")";
    }
}
