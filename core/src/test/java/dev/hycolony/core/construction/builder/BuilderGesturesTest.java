package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.construction.shared.BuilderTimings;
import dev.hycolony.core.job.work.WorkerHands;
import dev.hycolony.core.job.work.WorkerMachine;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.FakeBodies;
import dev.hycolony.core.testing.FakeWorldEffects;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuilderGesturesTest {
    private final FakeBodies bodies = new FakeBodies();
    private final BodyId body = bodies.existing(1, 1, new Vec3(0, 0, 0));
    private final FakeWorldEffects effects = new FakeWorldEffects();
    private final BuilderGestures gestures =
            new BuilderGestures(bodies, body, new WorkerHands(bodies, body, new CitizenData(1)), effects);

    /** Runs the delay out one machine tick at a time; returns the game tick of every play (0 = startDelay). */
    private List<Integer> playTicks(int delay, BodyAnimation anim) {
        List<Integer> ticks = new ArrayList<>();
        gestures.startDelay(delay, anim);
        ticks.add(0);
        int seen = bodies.bodies.get(body).animations;
        for (int tick = WorkerMachine.MACHINE_RATE; gestures.waiting(); tick += WorkerMachine.MACHINE_RATE) {
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

    @Test
    void miningHitsTheBlockEveryAiTickWithIncreasingProgress() {
        BlockPos target = new BlockPos(2, 0, 0);
        gestures.startMining(20, target);
        while (gestures.waiting()) {
            // runs the delay out
        }

        assertEquals(List.of(target, target, target, target), effects.hits);
        assertEquals(List.of(0.25f, 0.5f, 0.75f, 1f), effects.hitProgress);
    }

    @Test
    void miningFartherThanFourBlocksDoesNotHitTheBlock() {
        gestures.startMining(20, new BlockPos(4, 0, 0));
        while (gestures.waiting()) {
            // runs the delay out
        }

        assertTrue(effects.hits.isEmpty());
    }

    @Test
    void placingABlockDoesNotHitIt() {
        gestures.startDelay(BuilderTimings.placeDelay(0), BodyAnimation.BUILD);
        while (gestures.waiting()) {
            // runs the delay out
        }

        assertTrue(effects.hits.isEmpty());
    }
}
