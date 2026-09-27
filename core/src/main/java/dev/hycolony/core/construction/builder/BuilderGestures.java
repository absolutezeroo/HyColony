package dev.hycolony.core.construction.builder;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
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

    private final CitizenBodies bodies;
    private final BodyId body;

    private int delay;
    private @Nullable BodyAnimation animation;
    /** Game ticks since the animation last started. */
    private int sinceStroke;
    /** What the builder holds: the block it places or the tool it mines with (for the citizen window). */
    private @Nullable ItemKey inHand;

    BuilderGestures(CitizenBodies bodies, BodyId body) {
        this.bodies = bodies;
        this.body = body;
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
        if (delay <= 0) {
            return false;
        }
        sinceStroke += BuilderAI.MACHINE_RATE;
        if (animation == BodyAnimation.MINE && sinceStroke >= MINE_ANIMATION_TICKS) {
            bodies.playAnimation(body, animation);
            sinceStroke = 0;
        }
        delay -= BuilderAI.MACHINE_RATE;
        if (delay <= 0) {
            delay = 0;
            animation = null;
        }
        return true;
    }

    /** Waits {@code ticks} (the animation, if any, keeps playing). */
    void pause(int ticks) {
        delay = ticks;
    }

    void startDelay(int ticks, BodyAnimation anim) {
        delay = ticks;
        animation = anim;
        sinceStroke = 0;
        bodies.playAnimation(body, anim);
    }

    void hold(@Nullable ItemKey item) {
        inHand = item;
        bodies.setHeldItem(body, Optional.ofNullable(item));
    }

    @Nullable
    ItemKey inHand() {
        return inHand;
    }

    void lookAt(BlockPos pos) {
        bodies.lookAt(body, Vec3.middle(pos));
    }
}
