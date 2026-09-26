package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.FakeBodies;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuilderGesturesTest {
    private final FakeBodies bodies = new FakeBodies();
    private final BodyId body = bodies.existing(1, 1, new Vec3(0, 0, 0));
    private final BuilderGestures gestures = new BuilderGestures(bodies, body);

    /** Runs the delay out one machine tick at a time; returns the game tick of every play (0 = startDelay). */
    private List<Integer> playTicks(int delay, BodyAnimation anim) {
        List<Integer> ticks = new ArrayList<>();
        gestures.startDelay(delay, anim);
        ticks.add(0);
        int seen = bodies.bodies.get(body).animations;
        for (int tick = BuilderAI.MACHINE_RATE; gestures.waiting(); tick += BuilderAI.MACHINE_RATE) {
            if (bodies.bodies.get(body).animations > seen) {
                seen = bodies.bodies.get(body).animations;
                ticks.add(tick);
            }
        }
        return ticks;
    }

    @Test
    void placingABlockPlaysTheBuildAnimationExactlyOnce() {
        playTicks(BuilderTimings.placeDelay(0), BodyAnimation.BUILD);

        assertEquals(1, bodies.bodies.get(body).animations);
    }

    @Test
    void longMiningDelayReplaysStrokesOnlyOnceThePreviousFinished() {
        List<Integer> plays = playTicks(40, BodyAnimation.MINE);

        assertEquals(List.of(0, 10, 20, 30, 40), plays);
        for (int i = 1; i < plays.size(); i++) {
            assertTrue(plays.get(i) - plays.get(i - 1) >= BuilderGestures.MINE_ANIMATION_TICKS);
        }
    }

    @Test
    void miningDelayShorterThanOneStrokePlaysOnce() {
        playTicks(BuilderGestures.MINE_ANIMATION_TICKS - 1, BodyAnimation.MINE);

        assertEquals(1, bodies.bodies.get(body).animations);
    }
}
