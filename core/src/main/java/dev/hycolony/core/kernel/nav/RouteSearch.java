package dev.hycolony.core.kernel.nav;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One {@link SafeRoute} search: a breadth-first walk over the columns between two points, then its shortcuts. */
final class RouteSearch {
    /** Blocks the search area extends beyond the start and the target on each side. */
    static final int MARGIN = 6;
    /** Most columns visited by one search, so a long walk costs a bounded number of block reads. */
    static final int SEARCH_LIMIT = 4096;
    /** Half the body's width, in blocks (the Player model's hitbox is 0.325): the tightest clearance a walk needs. */
    static final double BODY_RADIUS = 0.35;
    /** Distance between two points checked along a leg, in blocks. */
    private static final double STEP = 0.25;
    /** Blocks around the walk's height checked for danger: floor, feet, head, and 1 of slope either way. */
    private static final int HALF_HEIGHT = 2;

    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final DangerousCells danger;
    private final Vec3 from;
    private final Vec3 to;
    /** Half-width, in blocks, of the square around the body's centre that must stay off dangerous columns. */
    private final double clearance;

    private final Map<Long, Boolean> dangerous = new HashMap<>();

    RouteSearch(DangerousCells danger, Vec3 from, Vec3 to, double clearance) {
        this.danger = danger;
        this.from = from;
        this.to = to;
        this.clearance = clearance;
    }

    /** Whether a body walking straight from {@code a} to {@code b} keeps its clearance off every dangerous column. */
    boolean clear(Vec3 a, Vec3 b) {
        int steps = (int) Math.ceil(Math.hypot(b.x() - a.x(), b.z() - a.z()) / STEP);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            if (!clearAt(a.x() + (b.x() - a.x()) * t, a.z() + (b.z() - a.z()) * t)) {
                return false;
            }
        }
        return true;
    }

    /** Whether no column under the clearance square centred on (x, z) is dangerous. */
    private boolean clearAt(double x, double z) {
        for (int cx = floor(x - clearance); cx <= floor(x + clearance); cx++) {
            for (int cz = floor(z - clearance); cz <= floor(z + clearance); cz++) {
                if (dangerous(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Column centres from the one after the start's to the target's, each clear at its centre; empty if none. */
    List<Vec3> cellPath() {
        long start = key(floor(from.x()), floor(from.z()));
        long goal = key(floor(to.x()), floor(to.z()));
        Map<Long, Long> parent = new HashMap<>();
        parent.put(start, start);
        ArrayDeque<Long> open = new ArrayDeque<>(List.of(start));
        while (!open.isEmpty() && parent.size() < SEARCH_LIMIT) {
            long cell = open.poll();
            if (cell == goal) {
                return trace(parent, start, goal);
            }
            for (int[] n : NEIGHBOURS) {
                int x = x(cell) + n[0];
                int z = z(cell) + n[1];
                long next = key(x, z);
                if (inArea(x, z) && !parent.containsKey(next) && clearAt(x + 0.5, z + 0.5)) {
                    parent.put(next, cell);
                    open.add(next);
                }
            }
        }
        return List.of();
    }

    /** The fewest waypoints along {@code cells}: from each point, the farthest cell reachable in a clear line. */
    List<Vec3> shorten(List<Vec3> cells) {
        List<Vec3> points = new ArrayList<>();
        Vec3 here = from;
        int i = 0;
        while (i < cells.size()) {
            int j = cells.size() - 1;
            while (j > i && !clear(here, cells.get(j))) {
                j--;
            }
            here = j == cells.size() - 1 ? to : cells.get(j);
            points.add(here);
            i = j + 1;
        }
        return points;
    }

    private List<Vec3> trace(Map<Long, Long> parent, long start, long goal) {
        List<Vec3> cells = new ArrayList<>();
        for (long c = goal; c != start; c = parent.get(c)) {
            cells.add(new Vec3(x(c) + 0.5, height(x(c), z(c)), z(c) + 0.5));
        }
        Collections.reverse(cells);
        return cells;
    }

    /** The start's and target's own columns never count: the body is already in one, and targets are checked. */
    private boolean dangerous(int x, int z) {
        if (x == floor(from.x()) && z == floor(from.z()) || x == floor(to.x()) && z == floor(to.z())) {
            return false;
        }
        return dangerous.computeIfAbsent(
                key(x, z), k -> danger.inColumn(new BlockPos(x, (int) Math.floor(height(x, z)), z), HALF_HEIGHT));
    }

    /** The walk's height above column (x, z): start and target heights, interpolated along the straight line. */
    private double height(int x, int z) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        double len2 = dx * dx + dz * dz;
        double t = len2 == 0 ? 0 : ((x + 0.5 - from.x()) * dx + (z + 0.5 - from.z()) * dz) / len2;
        return from.y() + (to.y() - from.y()) * Math.clamp(t, 0, 1);
    }

    private boolean inArea(int x, int z) {
        return x >= Math.min(floor(from.x()), floor(to.x())) - MARGIN
                && x <= Math.max(floor(from.x()), floor(to.x())) + MARGIN
                && z >= Math.min(floor(from.z()), floor(to.z())) - MARGIN
                && z <= Math.max(floor(from.z()), floor(to.z())) + MARGIN;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    private static int x(long key) {
        return (int) (key >> 32);
    }

    private static int z(long key) {
        return (int) key;
    }
}
