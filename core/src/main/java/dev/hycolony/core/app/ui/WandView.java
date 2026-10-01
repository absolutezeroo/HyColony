package dev.hycolony.core.app.ui;

import dev.hycolony.core.kernel.port.Msg;
import java.util.List;

/**
 * The build tool window (ST WindowExtendedBuildTool, AbstractBlueprintManipulationWindow): the chosen style and its
 * pack name, the open folder ({@code depth}, "" at the root), the pack's top folders ({@code categories}), then either
 * the open folder's subfolders ({@code folders}) or its huts ({@code huts}); the chosen hut, its level and maximum
 * level, the rotation; {@code manipulate} once a hut is chosen, {@code canConfirm} when it is not locked,
 * {@code creative} for the creative placement list, {@code tip} for Structurize's first-open hint.
 */
public record WandView(
        String style,
        String packName,
        String depth,
        List<String> categories,
        List<String> folders,
        List<Hut> huts,
        String buildingTypeId,
        int level,
        int maxLevel,
        int rotation,
        boolean manipulate,
        boolean canConfirm,
        boolean creative,
        boolean tip) {
    public WandView {
        categories = List.copyOf(categories);
        folders = List.copyOf(folders);
        huts = List.copyOf(huts);
    }

    /**
     * A hut button (ST handleBlueprint): its type, whether it is the chosen one, and what the player lacks to place it
     * (ST getRequirements, red tooltip lines); locked when that list is not empty.
     */
    public record Hut(String buildingTypeId, boolean selected, List<Msg> requirements) {
        public Hut {
            requirements = List.copyOf(requirements);
        }

        public boolean locked() {
            return !requirements.isEmpty();
        }
    }

    /** The hut types of {@link #huts}, in order. */
    public List<String> hutIds() {
        return huts.stream().map(Hut::buildingTypeId).toList();
    }
}
