package dev.hycolony.core.citizen.vitals;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import java.util.Locale;

/** How the diagnostics write positions and distances in a {@code Msg}: the same in every language, one decimal. */
public final class DebugText {
    private DebugText() {}

    /** "x y z". */
    public static String pos(BlockPos p) {
        return p.x() + " " + p.y() + " " + p.z();
    }

    /** "x.x y.y z.z". */
    public static String pos(Vec3 v) {
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", v.x(), v.y(), v.z());
    }

    /** {@code d} with one decimal. */
    public static String decimal(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }
}
