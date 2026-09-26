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
 */
public final class SafeRoute {
    private final DangerousCells danger;

    public SafeRoute(DangerousCells danger) {
        this.danger = danger;
    }

    /**
     * The points to walk to in turn, ending with {@code to}: just {@code to} when the straight line is clear, or when
     * no detour exists within {@link RouteSearch#MARGIN} blocks of the line (the nav then decides).
     */
    public List<Vec3> plan(Vec3 from, Vec3 to) {
        RouteSearch search = new RouteSearch(danger, from, to);
        if (search.clear(from, to)) {
            return List.of(to);
        }
        List<Vec3> cells = search.cellPath();
        return cells.isEmpty() ? List.of(to) : search.shorten(cells);
    }
}
