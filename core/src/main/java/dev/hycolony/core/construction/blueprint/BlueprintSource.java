package dev.hycolony.core.construction.blueprint;

import dev.hycolony.core.kernel.item.BlockKey;
import java.util.List;
import java.util.Optional;

/** Port: the blueprints of every style, by building type, level and rotation. */
public interface BlueprintSource {
    /** The rotated blueprint; empty when the style, type or level is unknown or cannot be read. */
    Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation);

    List<String> styles();

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
