package dev.hycolony.core.app.ui;

import dev.hycolony.core.kernel.BlockPos;

/** Which live colony window a player has open: a hut's, a colony's town hall, a citizen's, or a clipboard. */
public sealed interface WindowKey {
    /** A hut window, the town hall's own hut window included. */
    record Hut(BlockPos pos) implements WindowKey {}

    record TownHall(int colonyId) implements WindowKey {}

    record Citizen(int colonyId, int citizenId) implements WindowKey {}

    /** A colony's clipboard window (MC WindowClipBoard). */
    record Clipboard(int colonyId) implements WindowKey {}
}
