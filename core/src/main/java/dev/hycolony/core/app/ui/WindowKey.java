package dev.hycolony.core.app.ui;

import dev.hycolony.core.kernel.BlockPos;

/** Which live colony window a player has open: a hut's, a town hall's, a citizen's, a clipboard or a field's. */
public sealed interface WindowKey {
    /** A hut window, the town hall's own hut window included. */
    record Hut(BlockPos pos) implements WindowKey {}

    record TownHall(int colonyId) implements WindowKey {}

    record Citizen(int colonyId, int citizenId) implements WindowKey {}

    /** A colony's clipboard window (MC WindowClipBoard). */
    record Clipboard(int colonyId) implements WindowKey {}

    /** A field block's window (MC WindowField). */
    record Field(BlockPos pos) implements WindowKey {}
}
