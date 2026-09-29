package dev.hycolony.core.app.ui;

import dev.hycolony.core.kernel.BlockPos;

/** Which live colony window a player has open: a hut's, a colony's town hall, or a citizen's. */
public sealed interface WindowKey {
    /** A hut window, the town hall's own hut window included. */
    record Hut(BlockPos pos) implements WindowKey {}

    record TownHall(int colonyId) implements WindowKey {}

    record Citizen(int colonyId, int citizenId) implements WindowKey {}
}
