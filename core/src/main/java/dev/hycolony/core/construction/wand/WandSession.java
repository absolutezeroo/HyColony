package dev.hycolony.core.construction.wand;

import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;

/**
 * One player's build tool state: the anchor, style, building type, level and rotation of the blueprint they are
 * placing (ST AbstractBlueprintManipulationWindow, l.525-590).
 */
record WandSession(Optional<BlockPos> anchor, String style, String buildingTypeId, int level, int rotation) {
    /** No anchor, no style or building chosen yet, level 1, rotation 0 (Structurize's window on first open). */
    static WandSession empty() {
        return new WandSession(Optional.empty(), "", "", 1, 0);
    }

    WandSession withAnchor(BlockPos anchor) {
        return new WandSession(Optional.of(anchor), style, buildingTypeId, level, rotation);
    }

    WandSession withStyle(String style) {
        return new WandSession(anchor, style, buildingTypeId, level, rotation);
    }

    WandSession withBuilding(String buildingTypeId) {
        return new WandSession(anchor, style, buildingTypeId, level, rotation);
    }

    WandSession withLevel(int level) {
        return new WandSession(anchor, style, buildingTypeId, level, rotation);
    }

    WandSession withRotation(int rotation) {
        return new WandSession(anchor, style, buildingTypeId, level, rotation);
    }

    /** True once a building type has been chosen (ST's tree navigation reached a blueprint). */
    boolean hasBuilding() {
        return !buildingTypeId.isEmpty();
    }
}
