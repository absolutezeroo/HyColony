package dev.hycolony.core.request.model;

import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.Objects;

/** A single tool of {@code type} with a level in [minLevel, maxLevel]. */
public record ToolRequest(ToolType type, int minLevel, int maxLevel) implements Deliverable {
    public ToolRequest {
        Objects.requireNonNull(type, "type");
    }

    @Override
    public boolean matches(ItemKey item, ItemCatalog catalog) {
        return catalog.tool(item)
                .filter(t -> t.type() == type && t.level() >= minLevel && t.level() <= maxLevel)
                .isPresent();
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
