package dev.hylens.core.draw;

import dev.hycolony.api.Pos;
import dev.hycolony.api.Vec;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.debug.WalkEnded;
import dev.hycolony.api.read.CitizenSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * What is drawn for the operator who watches a citizen (spec 2026-09-30, § 6.3): a line from its body along the path
 * it plans to walk, else straight, to its walk target and a sphere on that target, red once HyColony confirmed that a
 * walk to that target ended away, or that the stuck handler teleported or gave up; a cube on the cell where its last
 * walk ended, and one on its workplace's hut.
 */
public final class WatchShapes {
    /** HyColony's alert of a walk that ended away from its goal (invariant 1): it tells of the last walk ended. */
    static final String ENDED_AWAY = "WALK_ENDED_AWAY";
    /** HyColony's alert of a walk teleported or given up (invariant 10): it tells of the walk under way or the last. */
    static final String STUCK = "STUCK_ESCALATED";

    /** Blocks above the feet the line starts at, so it is not buried in the floor. */
    private static final double BODY_HEIGHT = 0.5;
    /** The most points of the walk's path drawn, then a last leg to the target. */
    static final int PATH_POINTS = 12;
    /**
     * The most points of the line once laid on the ground: each leg is a packet sent to the operator at every
     * refresh.
     */
    static final int LINE_POINTS = 24;

    private WatchShapes() {}

    /**
     * The shapes for {@code citizen}, from its debug snapshot {@code s} and its confirmed {@code alerts}, as far as
     * the operator's {@code layers} show them, its walk's line laid on the {@code ground}.
     */
    public static List<Shape> shapes(
            CitizenSnapshot citizen, CitizenDebugSnapshot s, List<Violation> alerts, Layers layers, Ground ground) {
        List<Shape> out = new ArrayList<>(4);
        Shape.Colour walk = layers.alerts() && failed(s, alerts) ? Shape.Colour.FAILED : Shape.Colour.WALK;
        s.walkTarget().filter(t -> layers.target()).ifPresent(target -> {
            citizen.position().ifPresent(body -> out.addAll(legs(body, s.path(), target, walk, ground)));
            out.add(Shape.sphere(centre(target), walk));
        });
        s.lastWalkEnd()
                .filter(e -> layers.stop())
                .ifPresent(end -> out.add(Shape.cube(centre(cell(end.at())), Shape.Colour.STOP)));
        citizen.work().filter(w -> layers.zone()).ifPresent(hut -> out.add(Shape.cube(centre(hut), Shape.Colour.WORK)));
        return out;
    }

    /**
     * A line from the {@code body}'s feet through the first {@link #PATH_POINTS} points of the walk's {@code path} (its
     * navigation's and HyColony's detours), then on to the {@code target} cell, laid on the {@code ground}; raised to
     * show above the floor. One line per leg.
     */
    private static List<Shape> legs(Vec body, List<Vec> path, Pos target, Shape.Colour colour, Ground ground) {
        List<Vec> feet = new ArrayList<>(PATH_POINTS + 2);
        feet.add(body);
        feet.addAll(path.subList(0, Math.min(path.size(), PATH_POINTS)));
        feet.add(new Vec(target.x() + 0.5, target.y(), target.z() + 0.5));
        List<Vec> line = GroundLine.drape(feet, ground, LINE_POINTS);
        List<Shape> out = new ArrayList<>(line.size());
        for (int i = 1; i < line.size(); i++) {
            out.add(Shape.line(raised(line.get(i - 1)), raised(line.get(i)), colour));
        }
        return out;
    }

    /** {@code feet} raised to where a line shows above the floor. */
    private static Vec raised(Vec feet) {
        return new Vec(feet.x(), feet.y() + BODY_HEIGHT, feet.z());
    }

    /**
     * Whether the drawn walk failed: stuck, or its target is the one of the last walk ended, which ended away. An
     * alert of a walk that ended away elsewhere says nothing of this one.
     */
    private static boolean failed(CitizenDebugSnapshot s, List<Violation> alerts) {
        boolean sameTarget = s.lastWalkEnd().map(WalkEnded::target).equals(s.walkTarget());
        return alerts.stream()
                .anyMatch(v -> v.code().equals(STUCK) || (sameTarget && v.code().equals(ENDED_AWAY)));
    }

    private static Pos cell(Vec v) {
        return new Pos((int) Math.floor(v.x()), (int) Math.floor(v.y()), (int) Math.floor(v.z()));
    }

    private static Vec centre(Pos p) {
        return new Vec(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
    }
}
