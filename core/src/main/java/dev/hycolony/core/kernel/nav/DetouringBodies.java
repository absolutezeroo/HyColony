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

    private final CitizenBodies bodies;
    private final SafeRoute route;
    /** Per walking body, the waypoint it walks to now, then the rest; absent once the last leg is handed over. */
    private final Map<BodyId, Deque<Vec3>> legs = new HashMap<>();

    public DetouringBodies(CitizenBodies bodies, WorldBlocks blocks, ItemCatalog catalog) {
        this.bodies = bodies;
        this.route = new SafeRoute(new DangerousCells(blocks, catalog));
    }

    /** Walks to the first leg of a safe route to {@code target}; replaces any walk in progress. */
    @Override
    public void moveTo(BodyId body, Vec3 target) {
        Optional<Vec3> from = bodies.position(body);
        walk(body, new ArrayDeque<>(from.isPresent() ? route.plan(from.get(), target) : List.of(target)));
    }

    /** The real status on the last leg; on an earlier one, MOVING, and the next leg starts once this one is reached. */
    @Override
    public NavStatus navStatus(BodyId body) {
        NavStatus status = bodies.navStatus(body);
        Deque<Vec3> rest = legs.get(body);
        if (rest == null) {
            return status;
        }
        if (status == NavStatus.ARRIVED || reached(body, rest.peek())) {
            rest.poll();
            walk(body, rest);
            return NavStatus.MOVING;
        }
        if (status == NavStatus.BLOCKED || status == NavStatus.FAILED) {
            legs.remove(body);
        }
        return status;
    }

    private void walk(BodyId body, Deque<Vec3> waypoints) {
        bodies.moveTo(body, waypoints.peek());
        if (waypoints.size() > 1) {
            legs.put(body, waypoints);
        } else {
            legs.remove(body);
        }
    }

    private boolean reached(BodyId body, Vec3 waypoint) {
        return bodies.position(body)
                .map(p -> Math.hypot(p.x() - waypoint.x(), p.z() - waypoint.z()) <= WAYPOINT_REACH)
                .orElse(false);
    }

    @Override
    public void lookAt(BodyId body, Vec3 target) {
        legs.remove(body);
        bodies.lookAt(body, target);
    }

    @Override
    public void teleport(BodyId body, Vec3 target) {
        legs.remove(body);
        bodies.teleport(body, target);
    }

    @Override
    public void despawn(BodyId body) {
        legs.remove(body);
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
