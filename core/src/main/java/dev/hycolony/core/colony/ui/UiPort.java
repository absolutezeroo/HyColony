package dev.hycolony.core.colony.ui;

import java.util.UUID;

/** Renders core view models. Player actions come back through ColonyManager methods. */
public interface UiPort {
    void showFoundColony(UUID player, FoundColonyView view);

    void showTownHall(UUID player, TownHallView view);

    void showBuilding(UUID player, BuildingView view);

    void showRequests(UUID player, RequestsView view);

    void showCitizen(UUID player, CitizenView view);

    /** A chat line, not a window. */
    void notifyNeedsPlayer(UUID player, NeedsPlayerNotice notice);

    void close(UUID player);
}
