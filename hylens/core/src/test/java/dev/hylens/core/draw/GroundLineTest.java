package dev.hylens.core.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.api.Vec;
import dev.hylens.core.testing.FakeGround;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A walk's line laid on the ground it crosses, as the body walks it (spec 2026-09-30, § 6.3). */
class GroundLineTest {
    @Test
    void straightLineOverAHillFollowsItsTop() {
        Ground hill = new FakeGround().flat(0, 5, 64).at(2, 65).at(3, 65);

        List<Vec> line = GroundLine.drape(List.of(new Vec(0.5, 64, 0.5), new Vec(5.5, 64, 0.5)), hill, 24);

        assertEquals(new Vec(0.5, 64, 0.5), line.getFirst());
        assertEquals(new Vec(5.5, 64, 0.5), line.getLast());
        assertTrue(line.contains(new Vec(2.5, 65, 0.5)), "on the hill: " + line);
        assertTrue(line.contains(new Vec(3.5, 65, 0.5)), "on the hill: " + line);
        assertTrue(line.contains(new Vec(4.5, 64, 0.5)), "down again: " + line);
    }

    @Test
    void hillFarAboveTheChordIsClimbedStepByStep() {
        Ground hill = new FakeGround()
                .flat(0, 20, 64)
                .at(6, 66)
                .at(7, 68)
                .at(8, 69)
                .at(9, 69)
                .at(10, 69)
                .at(11, 68)
                .at(12, 66);

        List<Vec> line = GroundLine.drape(List.of(new Vec(0.5, 64, 0.5), new Vec(20.5, 64, 0.5)), hill, 24);

        assertTrue(line.contains(new Vec(9.5, 69, 0.5)), "on the top, 5 above the chord: " + line);
    }

    @Test
    void risingLegIsLaidOnItsSlopeFromItsFoot() {
        FakeGround stairs = new FakeGround();
        for (int x = 0; x <= 8; x++) {
            stairs.at(x, 64 + x);
        }

        List<Vec> line = GroundLine.drape(List.of(new Vec(0.5, 64, 0.5), new Vec(8.5, 72, 0.5)), stairs, 24);

        assertTrue(line.contains(new Vec(1.5, 65, 0.5)), "the first step, sought from the foot: " + line);
        assertTrue(line.contains(new Vec(2.5, 66, 0.5)), line.toString());
    }

    @Test
    void unknownGroundKeepsTheStraightLineAsItIs() {
        List<Vec> straight = List.of(new Vec(0.5, 64, 0.5), new Vec(4.5, 68, 0.5));

        assertEquals(straight, GroundLine.drape(straight, new FakeGround(), 24), "no point added: each is a packet");
    }

    @Test
    void everyCornerOfThePathIsKept() {
        List<Vec> path = List.of(new Vec(0.5, 64, 0.5), new Vec(2.5, 64, 0.5), new Vec(2.5, 64, 3.5));

        List<Vec> line = GroundLine.drape(path, new FakeGround().flat(0, 3, 64), 24);

        assertTrue(line.containsAll(path), "corners: " + line);
    }

    @Test
    void longLineIsSampledMoreSparselyNeverPastItsBound() {
        for (double end : List.of(100.5, 44.5, 23.5)) {
            List<Vec> line = GroundLine.drape(
                    List.of(new Vec(0.5, 64, 0.5), new Vec(end, 64, 0.5)), new FakeGround().flat(0, 101, 64), 24);

            assertTrue(line.size() <= 24, end + " bounded: " + line.size());
            assertEquals(new Vec(end, 64, 0.5), line.getLast());
        }
    }
}
