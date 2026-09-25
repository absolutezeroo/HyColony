package dev.hycolony.core.colony.ui;

import java.util.List;

public record TownHallView(int colonyId, String colonyName, String ownerName, int day, List<CitizenRow> citizens,
                           boolean canRename) {
    public TownHallView {
        citizens = List.copyOf(citizens);
    }
}
