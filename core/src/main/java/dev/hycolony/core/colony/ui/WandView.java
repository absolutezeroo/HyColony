package dev.hycolony.core.colony.ui;

import java.util.List;

/**
 * The build tool window (ST WindowExtendedBuildTool): the styles and huts on offer, the current selection, and
 * whether the move/rotate/confirm buttons show ({@code manipulate}, once a hut is chosen). {@code maxLevel} is the
 * chosen hut's, 0 before one is chosen. {@code creative}: the player is in creative mode, so the paste button shows
 * (ST AbstractBlueprintManipulationWindow.updatePlacementOptions).
 */
public record WandView(
        List<String> styles,
        List<String> buildingTypeIds,
        int maxLevel,
        String style,
        String buildingTypeId,
        int level,
        int rotation,
        boolean manipulate,
        boolean creative) {}
