package dev.hycolony.core.app.wand;

/**
 * The build tool window's navigation state, as ST WindowExtendedBuildTool keeps it: the open folder ({@code depth},
 * "" at the root), what its lower list shows ({@link Grid}), whether the levels list shows, the category icon left
 * disabled by a click on it, whether the tree names the blueprint file, whether the tree shows a folder (after any
 * navigation, even back to the root: ST's {@code pack/}), the creative placement list, and the folder the chosen hut
 * was clicked in ({@code hutFolder}, ST's currentBlueprintCat), where the lone back button leads.
 */
record WandNav(
        String depth,
        Grid grid,
        boolean levels,
        String disabledIcon,
        boolean file,
        boolean navigated,
        boolean placing,
        String hutFolder) {
    /** What the folder and blueprint lists show (ST's folderList and blueprintList). */
    enum Grid {
        /** Nothing: both lists hidden (ST's XML default, and after the placement list opens). */
        NONE,
        /** Back, then the open folder's subfolders or blueprints (ST updateFolders/updateBlueprints). */
        LIST,
        /** Only a back button to the chosen hut's folder (ST handleBlueprintCategory's updateFolders(empty, cat)). */
        BACK
    }

    /** A new window (ST init with nothing cached). */
    static WandNav start() {
        return new WandNav("", Grid.NONE, false, "", false, false, false, "");
    }

    /**
     * ST init on a new window (a reopening, or back from the pack window): the icons are recreated enabled and the
     * lists hidden; a chosen hut with several levels shows its levels and the back button (handleBlueprintCategory
     * with onOpen).
     */
    WandNav reopened(boolean hut, boolean multiLevel) {
        boolean leveled = hut && multiLevel;
        return new WandNav(
                depth, leveled ? Grid.BACK : Grid.NONE, leveled, "", hut, !depth.isEmpty(), false, hutFolder);
    }

    /**
     * ST onButtonClicked on a category icon ({@code root}) or a subfolder: that folder's list; an icon click disables
     * that icon; a folder with subfolders hides the levels.
     */
    WandNav opened(String folder, boolean root, boolean subfolders) {
        return new WandNav(
                folder, Grid.LIST, levels && !subfolders, root ? folder : disabledIcon, false, true, false, hutFolder);
    }

    /** Whether ST shows a back button: on a folder's list, or alone after a hut is chosen. */
    boolean canGoBack() {
        return grid == Grid.BACK || (grid == Grid.LIST && !depth.isEmpty());
    }

    /**
     * ST's back button: alone, it shows the chosen hut's folder again, otherwise the folder above; the levels hide,
     * and the icons are enabled again at the root.
     */
    WandNav back() {
        String target = grid == Grid.BACK ? hutFolder : WandTree.parent(depth);
        return new WandNav(
                target, Grid.LIST, false, target.isEmpty() ? "" : disabledIcon, false, true, false, hutFolder);
    }

    /**
     * ST handleBlueprintCategory on a blueprint clicked in the open folder: the icons enabled again; a hut with
     * several levels shows its levels and only the back button, a single-level one hides the levels and keeps the
     * list.
     */
    WandNav chose(boolean multiLevel) {
        return new WandNav(depth, multiLevel ? Grid.BACK : grid, multiLevel, "", true, navigated, false, depth);
    }

    /** ST's level button: the icons enabled again, the lists unchanged. */
    WandNav leveled() {
        return new WandNav(depth, grid, levels, "", true, navigated, false, hutFolder);
    }

    /** ST updatePlacementOptions (hideOtherGuiForPlacement): the placement list, the other lists hidden. */
    WandNav placement() {
        return new WandNav(depth, Grid.NONE, false, disabledIcon, file, navigated, true, hutFolder);
    }
}
