package dev.hycolony.core.request.model;

import dev.hycolony.core.kernel.catalog.ItemCatalog;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import java.util.Comparator;
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

    /**
     * The lowest tier of the catalog's tools it accepts, by id within a tier (MC AbstractRequest.getDisplayStacks: the
     * items that fit, in its creative tabs' order, wood before stone before iron). Deviation from MC: Hytale has no
     * creative tab order to follow; the tier gives the same lowest-first choice.
     */
    @Override
    public Optional<ItemKey> displayed(ItemCatalog catalog) {
        return catalog.tools().stream()
                .filter(t -> matches(t, catalog))
                .min(Comparator.comparingInt((ItemKey t) ->
                                catalog.tool(t).map(ToolInfo::level).orElse(0))
                        .thenComparing(ItemKey::id));
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
