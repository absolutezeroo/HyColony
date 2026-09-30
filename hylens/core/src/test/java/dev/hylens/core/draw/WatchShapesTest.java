package dev.hylens.core.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Vec;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.debug.WalkEnded;
import dev.hycolony.api.read.CitizenSnapshot;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What is drawn for the operator who watches a citizen (spec 2026-09-30, § 6.3). */
class WatchShapesTest {
    private static final CitizenRef ANN = new CitizenRef(new ColonyRef("default", 1), 4);
    private static final Vec BODY = new Vec(0.5, 64, 0.5);
    private static final Pos TARGET = new Pos(10, 64, -3);
    private static final Pos HUT = new Pos(20, 64, 5);

    private static CitizenSnapshot citizen(Optional<Vec> body, Optional<Pos> work) {
        return new CitizenSnapshot(ANN, "Ann", Optional.empty(), Optional.empty(), work, body);
    }

    private static CitizenDebugSnapshot debug(Optional<Pos> target, Optional<WalkEnded> last) {
        return new CitizenDebugSnapshot(
                ANN, 100, "WORK", 0, "", 0, Optional.empty(), target, last, "", 0, List.of(), 0, List.of());
    }

    private static Violation alert(String code) {
        return new Violation(code, ApiText.of("k"), Optional.of(ANN), Optional.empty());
    }

    private static Vec centre(Pos p) {
        return new Vec(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
    }

    @Test
    void walkingBodyGetsALineAndASphereOnItsTarget() {
        List<Shape> shapes = WatchShapes.shapes(
                citizen(Optional.of(BODY), Optional.empty()),
                debug(Optional.of(TARGET), Optional.empty()),
                List.of(),
                Layers.ALL);

        assertEquals(
                List.of(
                        Shape.line(new Vec(0.5, 64.5, 0.5), centre(TARGET), Shape.Colour.WALK),
                        Shape.sphere(centre(TARGET), Shape.Colour.WALK)),
                shapes);
    }

    @Test
    void lineTurnsRedOnceAWalkToItEndedAwayOrItIsStuck() {
        WalkEnded away = new WalkEnded(ANN, TARGET, new Vec(9.5, 64, -1.5), "NAV_ENDED", 2.1, "IDLE");
        for (String code : List.of("WALK_ENDED_AWAY", "STUCK_ESCALATED")) {
            List<Shape> shapes = WatchShapes.shapes(
                    citizen(Optional.of(BODY), Optional.empty()),
                    debug(Optional.of(TARGET), Optional.of(away)),
                    List.of(alert(code)),
                    Layers.ALL);

            assertEquals(Shape.Colour.FAILED, shapes.getFirst().colour(), code);
            assertEquals(Shape.Colour.FAILED, shapes.get(1).colour(), code);
        }
    }

    @Test
    void otherAlertsLeaveTheLineAsItIs() {
        List<Shape> shapes = WatchShapes.shapes(
                citizen(Optional.of(BODY), Optional.empty()),
                debug(Optional.of(TARGET), Optional.empty()),
                List.of(alert("JOB_STEP_STALE")),
                Layers.ALL);

        assertEquals(Shape.Colour.WALK, shapes.getFirst().colour());
    }

    @Test
    void unloadedBodyGetsNoLineButItsTargetStillShows() {
        List<Shape> shapes = WatchShapes.shapes(
                citizen(Optional.empty(), Optional.empty()),
                debug(Optional.of(TARGET), Optional.empty()),
                List.of(),
                Layers.ALL);

        assertEquals(List.of(Shape.sphere(centre(TARGET), Shape.Colour.WALK)), shapes);
    }

    @Test
    void lastWalksStopCellAndWorkplaceAreBoxed() {
        WalkEnded last = new WalkEnded(ANN, TARGET, new Vec(-7.8, 64.0, -2.2), "GAVE_UP", 2.3, "MOVING");

        List<Shape> shapes = WatchShapes.shapes(
                citizen(Optional.of(BODY), Optional.of(HUT)),
                debug(Optional.empty(), Optional.of(last)),
                List.of(),
                Layers.ALL);

        assertEquals(
                List.of(
                        Shape.cube(centre(new Pos(-8, 64, -3)), Shape.Colour.STOP),
                        Shape.cube(centre(HUT), Shape.Colour.WORK)),
                shapes);
    }

    @Test
    void idleCitizenWithoutWorkplaceDrawsNothing() {
        assertEquals(
                List.of(),
                WatchShapes.shapes(
                        citizen(Optional.of(BODY), Optional.empty()),
                        debug(Optional.empty(), Optional.empty()),
                        List.of(),
                        Layers.ALL));
    }

    @Test
    void walkEndedAwayOnlyReddensAWalkToThatSameTarget() {
        WalkEnded onTheRoof = new WalkEnded(ANN, new Pos(3, 70, 3), new Vec(3.5, 72, 3.5), "NAV_ENDED", 2.1, "IDLE");

        List<Shape> toHut = WatchShapes.shapes(
                citizen(Optional.of(BODY), Optional.empty()),
                debug(Optional.of(TARGET), Optional.of(onTheRoof)),
                List.of(alert("WALK_ENDED_AWAY")),
                Layers.ALL);
        WalkEnded atTarget = new WalkEnded(ANN, TARGET, new Vec(9.5, 64, -1.5), "NAV_ENDED", 2.1, "IDLE");
        List<Shape> again = WatchShapes.shapes(
                citizen(Optional.of(BODY), Optional.empty()),
                debug(Optional.of(TARGET), Optional.of(atTarget)),
                List.of(alert("WALK_ENDED_AWAY")),
                Layers.ALL);

        assertEquals(Shape.Colour.WALK, toHut.getFirst().colour(), "the failed walk went elsewhere");
        assertEquals(Shape.Colour.FAILED, again.getFirst().colour(), "a new try at the same target");
    }

    @Test
    void anyOfSeveralAlertsCanRedden() {
        List<Shape> shapes = WatchShapes.shapes(
                citizen(Optional.of(BODY), Optional.empty()),
                debug(Optional.of(TARGET), Optional.empty()),
                List.of(alert("JOB_STEP_STALE"), alert("STUCK_ESCALATED")),
                Layers.ALL);

        assertEquals(Shape.Colour.FAILED, shapes.getFirst().colour());
    }

    @Test
    void layersTurnedOffAreNotDrawn() {
        WalkEnded last = new WalkEnded(ANN, TARGET, new Vec(-7.8, 64.0, -2.2), "GAVE_UP", 2.3, "MOVING");
        CitizenSnapshot c = citizen(Optional.of(BODY), Optional.of(HUT));
        CitizenDebugSnapshot d = debug(Optional.of(TARGET), Optional.of(last));

        assertEquals(
                List.of(Shape.Kind.CUBE, Shape.Kind.CUBE),
                WatchShapes.shapes(c, d, List.of(), Layers.ALL.toggle(Layers.Layer.TARGET)).stream()
                        .map(Shape::kind)
                        .toList());
        assertEquals(
                List.of(Shape.Colour.WALK, Shape.Colour.WALK, Shape.Colour.WORK),
                WatchShapes.shapes(c, d, List.of(), Layers.ALL.toggle(Layers.Layer.STOP)).stream()
                        .map(Shape::colour)
                        .toList());
        assertEquals(
                List.of(Shape.Colour.WALK, Shape.Colour.WALK, Shape.Colour.STOP),
                WatchShapes.shapes(c, d, List.of(), Layers.ALL.toggle(Layers.Layer.ZONE)).stream()
                        .map(Shape::colour)
                        .toList());
    }

    @Test
    void alertsLayerOffNeverReddens() {
        List<Shape> shapes = WatchShapes.shapes(
                citizen(Optional.of(BODY), Optional.empty()),
                debug(Optional.of(TARGET), Optional.empty()),
                List.of(alert("STUCK_ESCALATED")),
                Layers.ALL.toggle(Layers.Layer.ALERTS));

        assertEquals(Shape.Colour.WALK, shapes.getFirst().colour());
    }
}
