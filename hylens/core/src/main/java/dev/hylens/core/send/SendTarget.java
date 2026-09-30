package dev.hylens.core.send;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Pos;
import java.util.Optional;

/**
 * Where "send here" sends a citizen, and which one (spec 2026-09-30, § 6.6). HyColony's walkTo takes the cell a body
 * stands in, not the floor under it: a walk to a floor block would end a block too low, flagged "ended away".
 */
public final class SendTarget {
    private SendTarget() {}

    /** The cell a body stands in on the block at {@code x y z}: the aimed block, or the ground found by the map. */
    public static Pos standingOn(int x, int y, int z) {
        return new Pos(x, y + 1, z);
    }

    /** The menu's coordinates as a cell; empty unless all three are whole numbers, spaces ignored. */
    public static Optional<Pos> parse(String x, String y, String z) {
        try {
            return Optional.of(
                    new Pos(Integer.parseInt(x.strip()), Integer.parseInt(y.strip()), Integer.parseInt(z.strip())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The citizen to send: the one {@code watched}, else the one {@code chosen} in the menu; empty with neither. */
    public static Optional<CitizenRef> who(Optional<CitizenRef> watched, Optional<CitizenRef> chosen) {
        return watched.or(() -> chosen);
    }
}
