package dev.hycolony.core.colony.ui;

import java.util.UUID;

/** Renders core view models. Player actions come back through ColonyManager methods. */
public interface UiPort {
    void showFoundColony(UUID player, FoundColonyView view);

    void showTownHall(UUID player, TownHallView view);

    void close(UUID player);
}
