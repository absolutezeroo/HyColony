package dev.hylens.core.draw;

import dev.hycolony.api.Vec;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * A walk's line laid on the ground it crosses, as the body walks it (spec 2026-09-30, § 6.3): Hytale steers straight
 * where nothing blocks, its body following the ground, so a straight line drawn in the air cuts through every hill.
 */
final class GroundLine {
    private GroundLine() {}

    /**
     * The polyline through {@code points} (feet heights), each leg sampled about every block at the {@code ground}'s
     * height there, a sample where the ground is unknown left out (the leg stays straight); at most {@code max} points,
     * samples spread over longer walks. Every point given is kept as it is. The ground is sought near the last ground
     * found, not near the leg: a body climbs a hill step by step, however far above the leg its top is.
     */
    static List<Vec> drape(List<Vec> points, Ground ground, int max) {
        int budget = max - points.size();
        if (points.size() < 2 || budget <= 0) {
            return points;
        }
        double step = Math.max(1.0, planarLength(points) / budget);
        List<Vec> out = new ArrayList<>(max);
        out.add(points.getFirst());
        for (int i = 1; i < points.size(); i++) {
            samples(out, points.get(i - 1), points.get(i), step, ground);
            out.add(points.get(i));
        }
        return out;
    }

    /**
     * The samples strictly between {@code a} and {@code b}, at most one every {@code step} blocks in plan, on known
     * ground, each sought near the one before it.
     */
    private static void samples(List<Vec> out, Vec a, Vec b, double step, Ground ground) {
        double len = Math.hypot(b.x() - a.x(), b.z() - a.z());
        int count = (int) Math.ceil(len / step) - 1;
        double lastFeet = a.y();
        for (int k = 1; k <= count; k++) {
            double t = (double) k / (count + 1);
            double x = a.x() + (b.x() - a.x()) * t;
            double z = a.z() + (b.z() - a.z()) * t;
            OptionalInt feet = ground.standY((int) Math.floor(x), (int) Math.floor(z), (int) Math.round(lastFeet));
            if (feet.isPresent()) {
                lastFeet = feet.getAsInt();
                out.add(new Vec(x, lastFeet, z));
            }
        }
    }

    private static double planarLength(List<Vec> points) {
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            Vec a = points.get(i - 1);
            Vec b = points.get(i);
            total += Math.hypot(b.x() - a.x(), b.z() - a.z());
        }
        return total;
    }
}
