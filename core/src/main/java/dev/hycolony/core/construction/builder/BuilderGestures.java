package dev.hycolony.core.construction.builder;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import java.util.Optional;

/**
 * What the builder visibly does while it works: the item in its hand, the block it faces, and the animation it
 * repeats while it waits out a work delay (MC setDelay + waitingForSomething).
 */
final class BuilderGestures {
    private final CitizenBodies bodies;
    private final BodyId body;

    private int delay;
    private BodyAnimation animation;
    /** What the builder holds: the block it places or the tool it mines with (for the citizen window). */
    private ItemKey inHand;

    BuilderGestures(CitizenBodies bodies, BodyId body) {
        this.bodies = bodies;
        this.body = body;
    }

    /** MC waitingForSomething: the builder swings while it waits out the delay. */
    boolean waiting() {
        if (delay <= 0) {
            return false;
        }
        if (animation != null) {
            bodies.playAnimation(body, animation);
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
        bodies.playAnimation(body, anim);
    }

    void hold(ItemKey item) {
        inHand = item;
        bodies.setHeldItem(body, Optional.ofNullable(item));
    }

    ItemKey inHand() {
        return inHand;
    }

    void lookAt(BlockPos pos) {
        bodies.lookAt(body, Vec3.middle(pos));
    }
}
