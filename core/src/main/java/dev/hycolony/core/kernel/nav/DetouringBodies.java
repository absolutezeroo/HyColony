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
import java.util.ArrayDeque;
import java.util.Deque;
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
    /** Per walking body, the waypoint it walks to now, then the rest; absent once the last leg is handed over. */
    private final Map<BodyId, Deque<Vec3>> legs = new HashMap<>();
    /** Per walking body, how many times its walk was replanned. */
    private final Map<BodyId, Integer> replans = new HashMap<>();

    public DetouringBodies(CitizenBodies bodies, WorldBlocks blocks, ItemCatalog catalog) {
        this.bodies = bodies;
        this.route = new SafeRoute(new DangerousCells(blocks, catalog));
    }

    /** Walks to the first leg of a safe route to {@code target}; replaces any walk in progress. */
    @Override
    public void moveTo(BodyId body, Vec3 target) {
        replans.remove(body);
        Optional<Vec3> from = bodies.position(body);
        walk(body, new ArrayDeque<>(from.isPresent() ? route.plan(from.get(), target) : List.of(target)));
    }

    /**
     * The real status on the last leg; on an earlier one, MOVING. The next leg starts once this one is reached and the
     * line from the body to the next waypoint is clear: the body stops short of a waypoint, and cutting the corner
     * from there could cross the fire the detour avoids. Arrived short with no clear line, the walk is replanned.
     */
    @Override
    public NavStatus navStatus(BodyId body) {
        NavStatus status = bodies.navStatus(body);
        Deque<Vec3> rest = legs.get(body);
        if (rest == null) {
            return status;
        }
        boolean arrived = status == NavStatus.ARRIVED;
        if (arrived || reached(body, rest.peek())) {
            Vec3 here = bodies.position(body).orElse(rest.peek());
            List<Vec3> next = List.copyOf(rest).subList(1, rest.size());
            if (route.clear(here, next.getFirst())) {
                rest.poll();
                walk(body, rest);
            } else if (arrived) {
                replan(body, here, next);
            }
            return NavStatus.MOVING;
        }
        if (status == NavStatus.BLOCKED || status == NavStatus.FAILED) {
            forget(body);
        }
        return status;
    }

    /** Walks a new safe route from {@code here} to the walk's target; past {@link #MAX_REPLANS}, the next leg as is. */
    private void replan(BodyId body, Vec3 here, List<Vec3> next) {
        int count = replans.merge(body, 1, Integer::sum);
        walk(body, new ArrayDeque<>(count > MAX_REPLANS ? next : route.plan(here, next.getLast())));
    }

    private void walk(BodyId body, Deque<Vec3> waypoints) {
        bodies.moveTo(body, waypoints.peek());
        if (waypoints.size() > 1) {
            legs.put(body, waypoints);
        } else {
            forget(body);
        }
    }

    private void forget(BodyId body) {
        legs.remove(body);
        replans.remove(body);
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
