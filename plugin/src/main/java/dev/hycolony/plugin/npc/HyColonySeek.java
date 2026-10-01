package dev.hycolony.plugin.npc;

import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.movement.BodyMotionFind;
import com.hypixel.hytale.server.npc.corecomponents.movement.builders.BuilderBodyMotionFind;
import com.hypixel.hytale.server.npc.navigation.IWaypoint;
import dev.hycolony.core.kernel.Vec3;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Hytale's "Seek" body motion, which also tells the path it follows: Hytale keeps the waypoints its A* computed in a
 * protected path follower, with no accessor, and its own VisPath shows them to every player and drops the nameplate.
 * Behaves exactly as Seek; the citizen role names it "HyColonySeek".
 */
public final class HyColonySeek extends BodyMotionFind {
    /** The most waypoints read, a bound on a long path (CLAUDE.md § 4, SCAN_LIMIT). */
    private static final int MAX_WAYPOINTS = 64;

    HyColonySeek(BuilderBodyMotionFind builder, BuilderSupport support) {
        super(builder, support);
    }

    /** The waypoints still to walk, the next first; empty while it steers straight at its target, or stands. */
    public List<Vec3> waypoints() {
        List<Vec3> out = new ArrayList<>();
        IWaypoint w = pathFollower.getCurrentWaypoint();
        for (int i = 0; w != null && i < MAX_WAYPOINTS; i++, w = w.next()) {
            Vector3d p = w.getPosition();
            out.add(new Vec3(p.x, p.y, p.z));
        }
        return out;
    }

    /** Builds {@link HyColonySeek} from Seek's own settings. */
    public static final class Builder extends BuilderBodyMotionFind {
        @Nonnull
        @Override
        public String getShortDescription() {
            return "Seek, telling the path it follows (HyColony)";
        }

        @Nonnull
        @Override
        public String getLongDescription() {
            return getShortDescription();
        }

        @Nonnull
        @Override
        public BodyMotionFind build(@Nonnull BuilderSupport support) {
            return new HyColonySeek(this, support);
        }
    }
}
