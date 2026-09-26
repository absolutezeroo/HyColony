package dev.hycolony.core.kernel.nav;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Citizen bodies whose walks go around dangerous blocks: each {@link #moveTo} is split into the {@link SafeRoute}
 * legs, handed to the real bodies one at a time. Callers see one walk: {@link #navStatus} reports MOVING until the
 * last leg ends. See {@link SafeRoute} for why this is not left to Hytale's nav.
 */
public final class DetouringBodies implements CitizenBodies {
    /** Horizontal distance, in blocks, at which a waypoint counts as reached even if the nav never says so. */
    static final double WAYPOINT_REACH = 1.0;
    /** Replans from where the nav stopped, per walk, before the next leg is taken as it is (never stalls). */
    static final int MAX_REPLANS = 8;

    private final CitizenBodies bodies;
    private final SafeRoute route;
    private final ClearTarget clearTarget;
    /**
     * Per walking body, the waypoint it walks to now, then the rest, with the clearance they were planned with (the
     * next-leg check asks for no more, so it never refuses a leg its own plan accepted); absent once the last leg is
     * handed over.
     */
    private final Map<BodyId, SafeRoute.Plan> legs = new HashMap<>();
    /** Per walking body, how many times its walk was replanned. */
    private final Map<BodyId, Integer> replans = new HashMap<>();
    /**
     * Per walking body, the packed block from which its next leg was refused; the check reads blocks, so it is only
     * redone once the body changes block or waypoint. Cleared on every new leg.
     */
    private final Map<BodyId, Long> refusedFrom = new HashMap<>();

    public DetouringBodies(CitizenBodies bodies, WorldBlocks blocks, ItemCatalog catalog) {
        this.bodies = bodies;
        DangerousCells danger = new DangerousCells(blocks, catalog);
        this.route = new SafeRoute(danger);
        this.clearTarget = new ClearTarget(blocks, catalog, danger);
    }

    /**
     * Walks to the first leg of a safe route to {@code target}, moved up to {@link ClearTarget#RADIUS} blocks off the
     * edge of a dangerous block ({@link ClearTarget#of}); replaces any walk in progress.
     */
    @Override
    public void moveTo(BodyId body, Vec3 target) {
        replans.remove(body);
        Vec3 end = clearTarget.of(target);
        Optional<Vec3> from = bodies.position(body);
        walk(body, from.map(f -> route.plan(f, end)).orElse(new SafeRoute.Plan(List.of(end), 0)));
    }

    /**
     * The real status on the last leg; on an earlier one, MOVING. The next leg starts once this one is reached and the
     * line from the body to the next waypoint is clear: the body stops short of a waypoint, and cutting the corner
     * from there could cross the fire the detour avoids. Arrived short with no clear line, the walk is replanned.
     */
    @Override
    public NavStatus navStatus(BodyId body) {
        NavStatus status = bodies.navStatus(body);
        SafeRoute.Plan rest = legs.get(body);
        if (rest == null) {
            return status;
        }
        boolean arrived = status == NavStatus.ARRIVED;
        Vec3 waypoint = rest.waypoints().getFirst();
        if (arrived || reached(body, waypoint)) {
            Vec3 here = bodies.position(body).orElse(waypoint);
            if (nextLegClear(body, here, rest)) {
                walk(body, rest.withoutFirst());
            } else if (arrived) {
                replan(body, here, rest.withoutFirst());
            }
            return NavStatus.MOVING;
        }
        if (status == NavStatus.BLOCKED || status == NavStatus.FAILED) {
            forget(body);
        }
        return status;
    }

    /** Whether the line from {@code here} to the waypoint after the current one keeps the plan's clearance. */
    private boolean nextLegClear(BodyId body, Vec3 here, SafeRoute.Plan rest) {
        long block = packBlock(here);
        Long refused = refusedFrom.get(body);
        if (refused != null && refused == block) {
            return false;
        }
        if (route.clear(here, rest.waypoints().get(1), rest.clearance())) {
            return true;
        }
        refusedFrom.put(body, block);
        return false;
    }

    /** The block holding {@code p}, packed as MC BlockPos.asLong does (26 bits x, 26 bits z, 12 bits y). */
    private static long packBlock(Vec3 p) {
        long x = (long) Math.floor(p.x());
        long y = (long) Math.floor(p.y());
        long z = (long) Math.floor(p.z());
        return (x & 0x3FFFFFFL) << 38 | (z & 0x3FFFFFFL) << 12 | y & 0xFFFL;
    }

    /** Walks a new safe route from {@code here} to the walk's target; past {@link #MAX_REPLANS}, the next leg as is. */
    private void replan(BodyId body, Vec3 here, SafeRoute.Plan next) {
        int count = replans.merge(body, 1, Integer::sum);
        walk(
                body,
                count > MAX_REPLANS ? next : route.plan(here, next.waypoints().getLast()));
    }

    private void walk(BodyId body, SafeRoute.Plan plan) {
        refusedFrom.remove(body);
        bodies.moveTo(body, plan.waypoints().getFirst());
        if (plan.waypoints().size() > 1) {
            legs.put(body, plan);
        } else {
            forget(body);
        }
    }

    private void forget(BodyId body) {
        legs.remove(body);
        replans.remove(body);
        refusedFrom.remove(body);
    }

    private boolean reached(BodyId body, Vec3 waypoint) {
        return bodies.position(body)
                .map(p -> Math.hypot(p.x() - waypoint.x(), p.z() - waypoint.z()) <= WAYPOINT_REACH)
                .orElse(false);
    }

    @Override
    public void lookAt(BodyId body, Vec3 target) {
        forget(body);
        bodies.lookAt(body, target);
    }

    @Override
    public void teleport(BodyId body, Vec3 target) {
        forget(body);
        bodies.teleport(body, target);
    }

    @Override
    public void despawn(BodyId body) {
        forget(body);
        bodies.despawn(body);
    }

    @Override
    public Optional<BodyId> spawn(WorldKey world, BlockPos near, int colonyId, int citizenId, String displayName) {
        return bodies.spawn(world, near, colonyId, citizenId, displayName);
    }

    @Override
    public boolean isAlive(BodyId body) {
        return bodies.isAlive(body);
    }

    @Override
    public Optional<Vec3> position(BodyId body) {
        return bodies.position(body);
    }

    @Override
    public void setDisplayName(BodyId body, String name) {
        bodies.setDisplayName(body, name);
    }

    @Override
    public void setHeldItem(BodyId body, Optional<ItemKey> item) {
        bodies.setHeldItem(body, item);
    }

    @Override
    public void playAnimation(BodyId body, BodyAnimation animation) {
        bodies.playAnimation(body, animation);
    }
}
