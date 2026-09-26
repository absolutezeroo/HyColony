package dev.hycolony.core.kernel.nav;

import dev.hycolony.core.kernel.Vec3;
import java.util.List;

/**
 * Detour waypoints that keep a walk off dangerous blocks: MC AbstractPathJob.isPassable and SurfaceType never let a
 * path through a PathfindingUtils.isDangerous block (fire, campfire, lava...).
 *
 * <p>Deviation from MC: Hytale's nav owns the path and only avoids blocks with {@code DamageToEntities}, not the
 * collision interactions vanilla fire and campfires burn with. So the core searches the columns between start and
 * target itself (a flat grid at the walk's interpolated height, no terrain) and hands the nav straight, safe legs.
 * Hytale's steering drifts a little off a straight leg, so a walk first keeps {@link #WIDE_CLEARANCE} from danger and
 * only squeezes past at the body's own half-width when nothing wider exists.
 */
public final class SafeRoute {
    /** Clearance, in blocks, from the body's centre to any dangerous column when a route that wide exists. */
    static final double WIDE_CLEARANCE = 1.0;

    private final DangerousCells danger;

    public SafeRoute(DangerousCells danger) {
        this.danger = danger;
    }

    /** The points to walk to in turn, ending with the target, and the clearance they were planned with. */
    public record Plan(List<Vec3> waypoints, double clearance) {}

    /**
     * The route to {@code to}, with {@link #WIDE_CLEARANCE} if possible, else with the body's half-width: just
     * {@code to} when the straight line is clear, or when no detour exists within {@link RouteSearch#MARGIN} blocks of
     * the line (the nav then decides). At most two searches.
     */
    public Plan plan(Vec3 from, Vec3 to) {
        for (double clearance : new double[] {WIDE_CLEARANCE, RouteSearch.BODY_RADIUS}) {
            RouteSearch search = new RouteSearch(danger, from, to, clearance);
            if (search.clear(from, to)) {
                return new Plan(List.of(to), clearance);
            }
            List<Vec3> cells = search.cellPath();
            if (!cells.isEmpty()) {
                return new Plan(search.shorten(cells), clearance);
            }
        }
        return new Plan(List.of(to), RouteSearch.BODY_RADIUS);
    }

    /** Whether a body walking straight from {@code from} to {@code to} keeps {@code clearance} off danger. */
    public boolean clear(Vec3 from, Vec3 to, double clearance) {
        return new RouteSearch(danger, from, to, clearance).clear(from, to);
    }
}
