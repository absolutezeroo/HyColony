package dev.hycolony.core.construction.blueprint;

import dev.hycolony.core.kernel.item.BlockKey;
import java.util.List;
import java.util.Optional;

/** Port: the blueprints of every style, by building type, level and rotation. */
public interface BlueprintSource {
    /** The rotated blueprint; empty when the style, type or level is unknown or cannot be read. */
    Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation);

    List<String> styles();

    /** Whether the style has a plan for that hut and level, without reading it; by default, a load of it. */
    default boolean hasPlan(String style, String buildingTypeId, int level) {
        return load(style, buildingTypeId, level, 0).isPresent();
    }

    /** The style's pack metadata (ST pack.json); {@link PackInfo#defaults} when the source has none. */
    default PackInfo pack(String style) {
        return PackInfo.defaults(style);
    }

    /**
     * The blueprint folder of a hut type, as the packs of MC lay them out ({@code fundamentals},
     * {@code craftsmanship/storage}); {@link PackInfo#DEFAULT_CATEGORY} when the source names none.
     */
    default String category(String buildingTypeId) {
        return PackInfo.DEFAULT_CATEGORY;
    }

    /**
     * The fill block of a builder hut that chose none (MC BUILDER_SETTINGS fillblock, dirt by default); empty when
     * the source has none, and fill cells then stay as the world has them.
     */
    default Optional<BlockKey> defaultFillBlock() {
        return Optional.empty();
    }

    /** The blocks a player may choose as a hut's fill block (MC BlockSetting's list); empty when none. */
    default List<BlockKey> fillBlockChoices() {
        return List.of();
    }
}
