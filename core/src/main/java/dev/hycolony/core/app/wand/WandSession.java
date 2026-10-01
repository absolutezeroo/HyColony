package dev.hycolony.core.app.wand;

import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;

/**
 * One player's build tool state: the anchor, style, building type, level and rotation of the blueprint they are
 * placing (ST AbstractBlueprintManipulationWindow, l.525-590), and the window's navigation ({@link WandNav}).
 */
record WandSession(
        Optional<BlockPos> anchor, String style, String buildingTypeId, int level, int rotation, WandNav nav) {
    /** No anchor, no style or building chosen yet, level 1, rotation 0, at the root (Structurize on first open). */
    static WandSession empty() {
        return new WandSession(Optional.empty(), "", "", 1, 0, WandNav.start());
    }

    WandSession withAnchor(BlockPos anchor) {
        return new WandSession(Optional.of(anchor), style, buildingTypeId, level, rotation, nav);
    }

    WandSession withStyle(String style) {
        return new WandSession(anchor, style, buildingTypeId, level, rotation, nav);
    }

    WandSession withBuilding(String buildingTypeId) {
        return new WandSession(anchor, style, buildingTypeId, level, rotation, nav);
    }

    WandSession withLevel(int level) {
        return new WandSession(anchor, style, buildingTypeId, level, rotation, nav);
    }

    WandSession withRotation(int rotation) {
        return new WandSession(anchor, style, buildingTypeId, level, rotation, nav);
    }

    WandSession withNav(WandNav nav) {
        return new WandSession(anchor, style, buildingTypeId, level, rotation, nav);
    }

    /** The open pack folder ("" at the root). */
    String depth() {
        return nav.depth();
    }

    /** True once a building type has been chosen (ST's tree navigation reached a blueprint). */
    boolean hasBuilding() {
        return !buildingTypeId.isEmpty();
    }
}
