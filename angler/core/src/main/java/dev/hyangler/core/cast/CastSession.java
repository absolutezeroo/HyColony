package dev.hyangler.core.cast;

import dev.hyangler.api.BiteTimes;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * One cast, from the throw to its end (spec § 7.3, § 7.4; vanilla FishingHook): flying, then floating and waiting,
 * the fish approaching, the bite and its window. Every state has a way out (CLAUDE.md § 4): a flight bound, the line
 * length, a session bound. Fed once a core tick (20 a second) by the plugin, on the world thread; never saved.
 */
public final class CastSession {
    /** A bobber that neither lands nor floats within this time is lost. */
    public static final int MAX_FLYING_TICKS = 200;
    /** A safety bound past vanilla's longest wait, approach and window (600 + 80 + 40 ticks). */
    public static final int MAX_SESSION_TICKS = 2400;

    private final int lure;
    private final int maxLine;
    private final double biteMultiplier;
    private final RandomGenerator rng;
    private CastState state = CastState.FLYING;
    private Optional<CastEnd> end = Optional.empty();
    private int ticks;
    private int countdown;
    private BiteTimes times = new BiteTimes(1, 1, 1);

    public CastSession(int lure, int maxLine, double biteMultiplier, RandomGenerator rng) {
        this.lure = lure;
        this.maxLine = maxLine;
        this.biteMultiplier = biteMultiplier;
        this.rng = rng;
    }

    /** Advances one core tick; returns the new state. Ended casts stay ended. */
    public CastState tick(CastInputs in) {
        if (state == CastState.ENDED) {
            return state;
        }
        ticks++;
        if (in.distance() > maxLine) {
            return finish(CastEnd.BROKEN);
        }
        if (ticks >= MAX_SESSION_TICKS) {
            return finish(CastEnd.CANCELLED);
        }
        switch (state) {
            case FLYING -> fly(in);
            case FLOATING, APPROACH -> waitForFish(in);
            case BITING -> bite();
            default -> {}
        }
        return state;
    }

    /** Reels the line in: catches during a bite, else ends empty-handed (spec § 7.4); the same end once ended. */
    public CastEnd reel() {
        if (state != CastState.ENDED) {
            finish(
                    switch (state) {
                        case BITING -> CastEnd.CAUGHT;
                        case GROUNDED -> CastEnd.GROUNDED;
                        case FLYING -> CastEnd.CANCELLED;
                        default -> CastEnd.ESCAPED;
                    });
        }
        return end.orElseThrow();
    }

    /** Ends the cast without the angler: rod put away, angler gone, chunk unloaded. */
    public void cancel() {
        if (state != CastState.ENDED) {
            finish(CastEnd.CANCELLED);
        }
    }

    public CastState state() {
        return state;
    }

    /** How it ended; empty while it runs. */
    public Optional<CastEnd> end() {
        return end;
    }

    private void fly(CastInputs in) {
        if (in.inWater()) {
            startWaiting();
        } else if (in.onGround()) {
            state = CastState.GROUNDED;
        } else if (ticks >= MAX_FLYING_TICKS) {
            finish(CastEnd.CANCELLED);
        }
    }

    private void startWaiting() {
        times = BiteTimer.roll(lure, biteMultiplier, rng);
        countdown = times.waitTicks();
        state = CastState.FLOATING;
    }

    private void waitForFish(CastInputs in) {
        countdown -= BiteTimer.step(in.raining(), in.skyVisible(), rng);
        if (countdown > 0) {
            return;
        }
        if (state == CastState.FLOATING) {
            state = CastState.APPROACH;
            countdown = times.approachTicks();
        } else {
            state = CastState.BITING;
            countdown = times.windowTicks();
        }
    }

    private void bite() {
        countdown--;
        if (countdown <= 0) {
            startWaiting();
        }
    }

    private CastState finish(CastEnd how) {
        end = Optional.of(how);
        state = CastState.ENDED;
        return state;
    }
}
