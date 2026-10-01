package dev.hycolony.core.app.ui;

import java.util.UUID;

/** Renders core view models. Player actions come back through ColonyManager methods. */
public interface UiPort {
    void showFoundColony(UUID player, FoundColonyView view);

    void showTownHall(UUID player, TownHallView view);

    void showBuilding(UUID player, BuildingView view);

    /** A hut's build options window (MC WindowBuildBuilding); not kept live. */
    void showBuildOptions(UUID player, BuildOptionsView view);

    void showRequests(UUID player, RequestsView view);

    void showCitizen(UUID player, CitizenView view);

    /** Whether {@code player} still has {@code window} open, and not another page in its place. */
    boolean isShowing(UUID player, WindowKey window);

    /**
     * Redraws in place the window of this hut if {@code player} still has it open (MC building view sync); never
     * opens one. False when that window is closed or replaced by another.
     */
    boolean refreshBuilding(UUID player, BuildingView view);

    /** {@link #refreshBuilding} for the town hall window of the view's colony. */
    boolean refreshTownHall(UUID player, TownHallView view);

    /** {@link #refreshBuilding} for this citizen's window. */
    boolean refreshCitizen(UUID player, CitizenView view);

    /** {@link #refreshBuilding} for the clipboard window of the view's colony. */
    boolean refreshRequests(UUID player, RequestsView view);

    /** A field block's window (MC WindowField); re-shown after every button. */
    void showField(UUID player, FieldView view);

    /** {@link #refreshBuilding} for the window of the view's field. */
    boolean refreshField(UUID player, FieldView view);

    /** The build tool window; re-shown after every button. */
    void showWand(UUID player, WandView view);

    /** The build tool's pack window (ST WindowSwitchPack); re-shown after every keystroke of its filter. */
    void showWandPacks(UUID player, WandPacksView view);

    /**
     * The citizen's own inventory as a container window, live on its core inventory (MC ContainerCitizenInventory),
     * shown in the citizen window's Inventory tab. The caller has checked the permission; a player or citizen gone by
     * now is ignored.
     */
    void openCitizenInventory(UUID player, int colonyId, int citizenId);

    /** A chat line, not a window. */
    void notifyNeedsPlayer(UUID player, NeedsPlayerNotice notice);

    void close(UUID player);
}
