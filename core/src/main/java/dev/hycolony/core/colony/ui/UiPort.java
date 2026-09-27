package dev.hycolony.core.colony.ui;

import java.util.UUID;

/** Renders core view models. Player actions come back through ColonyManager methods. */
public interface UiPort {
    void showFoundColony(UUID player, FoundColonyView view);

    void showTownHall(UUID player, TownHallView view);

    void showBuilding(UUID player, BuildingView view);

    void showRequests(UUID player, RequestsView view);

    void showCitizen(UUID player, CitizenView view);

    /**
     * Redraws in place the window of this hut if {@code player} still has it open (MC building view sync); never
     * opens one. False when that window is closed or replaced by another.
     */
    boolean refreshBuilding(UUID player, BuildingView view);

    /** {@link #refreshBuilding} for the town hall window of the view's colony. */
    boolean refreshTownHall(UUID player, TownHallView view);

    /** {@link #refreshBuilding} for this citizen's window. */
    boolean refreshCitizen(UUID player, CitizenView view);

    /** The build tool window; re-shown after every button. */
    void showWand(UUID player, WandView view);

    /**
     * The citizen's own inventory as a container window, live on its core inventory (MC ContainerCitizenInventory).
     * The caller has checked the permission; a player or citizen gone by now is ignored.
     */
    void openCitizenInventory(UUID player, int colonyId, int citizenId);

    /** A chat line, not a window. */
    void notifyNeedsPlayer(UUID player, NeedsPlayerNotice notice);

    void close(UUID player);
}
