package dev.hycolony.core.app.ui;

import dev.hycolony.core.kernel.port.Msg;
import java.util.List;

/**
 * The build tool window (ST WindowExtendedBuildTool, AbstractBlueprintManipulationWindow): the chosen style and its
 * pack name, the window's {@link Panel}, the pack's top folders ({@code categories}), the open folder's subfolders
 * ({@code folders}) or huts ({@code huts}) when its list shows; the chosen hut, its level, its last level with a plan
 * and the rotation; {@code manipulate} once a hut is chosen, {@code canConfirm} unless the chosen hut is locked,
 * {@code creative} for the placement list, {@code tip} for Structurize's hint on a new position.
 */
public record WandView(
        String style,
        String packName,
        Panel panel,
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
     * What the window shows besides its lists: the open folder ("" at the root), the tree after the pack name (ST's
     * {@code /folder/builder2}, "" or "/" at the root), the category icon left disabled ("" for none), the back
     * button, the levels list and the creative placement list.
     */
    public record Panel(
            String depth, String treePath, String disabledCategory, boolean back, boolean levels, boolean placing) {}

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

    /** The open folder ("" at the root). */
    public String depth() {
        return panel.depth();
    }

    /** The hut types of {@link #huts}, in order. */
    public List<String> hutIds() {
        return huts.stream().map(Hut::buildingTypeId).toList();
    }
}
