package dev.hycolony.core.construction.builder;

import dev.hycolony.core.job.work.WorkDelay;
import dev.hycolony.core.job.work.WorkerHands;
import dev.hycolony.core.job.work.WorkerMachine;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.WorldEffects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What the builder visibly does while it works: the item in its hand, the block it faces, and the animation it
 * plays while it waits out a work delay (MC setDelay + waitingForSomething).
 */
final class BuilderGestures {
    /**
     * Length of one Pickaxe/Mine stroke in game ticks: Mine.blockyanim lasts 14 frames at 60 fps, Speed 1, plus the
     * 0.05 s BlendingDuration of Server/Item/Animations/Pickaxe.json, so 5.67 ticks, rounded up.
     */
    static final int MINE_ANIMATION_TICKS = 6;
    /** MC CitizenConstants.DEFAULT_RANGE_FOR_DELAY: blocks within which the worker hits what it mines. */
    private static final int RANGE_FOR_DELAY = 4;

    private final CitizenBodies bodies;
    private final BodyId body;
    private final WorkerHands hands;
    private final WorldEffects effects;
    private final WorkDelay delay = new WorkDelay();

    /** The whole break delay, in game ticks, of the block being mined. */
    private int total;
    /** The block being mined (MC currentWorkingLocation); null while placing or pausing. */
    private @Nullable BlockPos target;

    private @Nullable BodyAnimation animation;
    /** Game ticks since the animation last started. */
    private int sinceStroke;
    /** What the builder holds: the block it places or the tool it mines with (for the citizen window). */
    private @Nullable ItemKey inHand;

    BuilderGestures(CitizenBodies bodies, BodyId body, WorkerHands hands, WorldEffects effects) {
        this.bodies = bodies;
        this.body = body;
        this.hands = hands;
        this.effects = effects;
    }

    /**
     * MC waitingForSomething: the builder swings while it waits out the delay.
     *
     * <p>Deviation from MC: MC swings the arm on every AI tick (5 game ticks), which reads as one continuous motion
     * because the swing is that short. Hytale's Block/Build and Pickaxe/Mine animations last longer, so restarting
     * them every 5 ticks shows them twice. Placement plays Build once per block (in startDelay); mining replays the
     * Mine stroke only once the previous one has finished ({@link #MINE_ANIMATION_TICKS}).
     */
    boolean waiting() {
        if (!delay.waiting(WorkerMachine.MACHINE_RATE)) {
            return false;
        }
        sinceStroke += WorkerMachine.MACHINE_RATE;
        if (animation == BodyAnimation.MINE && sinceStroke >= MINE_ANIMATION_TICKS) {
            hands.swing(animation);
            sinceStroke = 0;
        }
        if (delay.remaining() == 0) {
            animation = null;
        }
        hitTarget();
        return true;
    }

    /** MC waitingForSomething: hits the mined block when the builder stands within {@link #RANGE_FOR_DELAY}. */
    private void hitTarget() {
        BlockPos at = target;
        if (at == null) {
            return;
        }
        if (bodies.position(body)
                .filter(p -> p.toBlockPos().distSq(at) < (long) RANGE_FOR_DELAY * RANGE_FOR_DELAY)
                .isPresent()) {
            effects.blockHit(at, 1f - (float) delay.remaining() / total);
        }
        if (delay.remaining() == 0) {
            target = null; // MC clearWorkTarget
        }
    }

    /** Waits {@code ticks} (the animation, if any, keeps playing). */
    void pause(int ticks) {
        delay.set(ticks);
        target = null;
    }

    /** Waits out the break delay of {@code pos}, swinging at it and hitting it (MC mineBlock + setDelay). */
    void startMining(int ticks, BlockPos pos) {
        startDelay(ticks, BodyAnimation.MINE);
        target = pos;
        total = ticks;
    }

    void startDelay(int ticks, BodyAnimation anim) {
        target = null;
        delay.set(ticks);
        animation = anim;
        sinceStroke = 0;
        hands.swing(anim);
    }

    void hold(@Nullable ItemKey item) {
        inHand = item;
        hands.hold(Optional.ofNullable(item));
    }

    @Nullable
    ItemKey inHand() {
        return inHand;
    }

    void lookAt(BlockPos pos) {
        hands.face(pos);
    }
}
