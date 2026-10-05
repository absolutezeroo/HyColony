package dev.hyangler.core.cast;

import dev.hyangler.api.BiteTimes;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * One cast, from the throw to its end (spec § 7.3, § 7.4; vanilla FishingHook.tick and catchingFish): flying (or
 * lying on the ground), then floating and waiting, the fish approaching, the bite and its window. Every state has a
 * way out (CLAUDE.md § 4): a flight bound, vanilla's ground bound, the line length, a bound on each wait. Fed once a
 * core tick (20 a second) by the plugin, on the world thread; never saved.
 */
public final class CastSession {
    /** Deviation from vanilla: a safety bound it has not; a bobber that neither lands nor floats within it is lost. */
    public static final int MAX_FLYING_TICKS = 200;
    /** Vanilla FishingHook.tick: a bobber on the ground this many ticks in a row is removed ({@code life >= 1200}). */
    public static final int MAX_GROUNDED_TICKS = 1200;
    /**
     * Deviation from vanilla: a safety bound; a wait, approach and window not over within this many times their drawn
     * length are lost.
     */
    static final int CYCLE_SLACK = 4;
    /** Vanilla: open water is lost once the bobber was out of the water this many ticks (outOfWaterTime < 10). */
    static final int MAX_OUT_OF_WATER = 10;

    private final int lure;
    private final int maxLine;
    private final double biteMultiplier;
    private final RandomGenerator rng;
    private CastState state = CastState.FLYING;
    private Optional<CastEnd> end = Optional.empty();
    private int ticksLeft = MAX_FLYING_TICKS;
    private int groundTicks;
    private boolean onGround;
    private boolean reeledOnGround;
    private int outOfWater;
    private boolean openWater = true;
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
        if (in.distance() > maxLine) {
            return finish(CastEnd.BROKEN);
        }
        if (timedOut(in)) {
            return finish(CastEnd.CANCELLED);
        }
        switch (state) {
            case FLYING, GROUNDED -> fly(in);
            case FLOATING, APPROACH, BITING -> bob(in);
            default -> {}
        }
        return state;
    }

    /** Reels the line in: catches during a bite, else ends empty-handed (spec § 7.4); the same end once ended. */
    public CastEnd reel() {
        if (state != CastState.ENDED) {
            reeledOnGround = onGround;
            finish(
                    switch (state) {
                        case BITING -> CastEnd.CAUGHT;
                        case GROUNDED -> CastEnd.GROUNDED;
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

    /**
     * The counted ticks left of the wait, the approach or the bite window, whichever runs: vanilla draws the fish's
     * bubbles this many tenths of a block away while it approaches (catchingFish, timeUntilHooked × 0.1).
     */
    public int countdown() {
        return countdown;
    }

    /** How it ended; empty while it runs. */
    public Optional<CastEnd> end() {
        return end;
    }

    /** The durability the rod loses: its end's, or 2 if reeled in on the ground (vanilla retrieve); 0 while it runs. */
    public int wear() {
        return reeledOnGround ? CastEnd.GROUNDED.wear() : end.map(CastEnd::wear).orElse(0);
    }

    /** Open water as vanilla keeps it for the catch: true out of an approach or a bite, else held at each tick. */
    public boolean openWater() {
        return openWater;
    }

    /** Counts this tick against vanilla's ground bound and, unless GROUNDED, the flight or cycle bound; true past. */
    private boolean timedOut(CastInputs in) {
        onGround = in.onGround();
        groundTicks = onGround ? groundTicks + 1 : 0;
        if (groundTicks >= MAX_GROUNDED_TICKS) {
            return true;
        }
        return state != CastState.GROUNDED && --ticksLeft <= 0;
    }

    /** Vanilla's FLYING: water makes it bob, the ground holds it (GROUNDED, its flight bound frozen). */
    private void fly(CastInputs in) {
        if (in.inWater()) {
            // Vanilla draws the wait on the tick after the bobber starts bobbing; countdown 0 asks for it.
            state = CastState.FLOATING;
            countdown = 0;
        } else if (in.onGround()) {
            state = CastState.GROUNDED;
        } else {
            state = CastState.FLYING; // the flight bound was frozen on the ground and runs on
        }
    }

    /** Vanilla's BOBBING: open water checked through the approach and the bite, then the fish, only in water. */
    private void bob(CastInputs in) {
        openWater = state == CastState.FLOATING || (openWater && outOfWater < MAX_OUT_OF_WATER && in.openWater());
        if (in.inWater()) {
            outOfWater = Math.max(0, outOfWater - 1);
            fish(in);
        } else {
            outOfWater = Math.min(MAX_OUT_OF_WATER, outOfWater + 1);
        }
    }

    /** One tick of vanilla's catchingFish: draws the wait, counts it down, then the approach, then the window. */
    private void fish(CastInputs in) {
        if (state == CastState.FLOATING && countdown <= 0) {
            drawWait();
        } else if (state == CastState.BITING) {
            if (--countdown <= 0) {
                state = CastState.FLOATING; // countdown 0: the wait is drawn again next tick
            }
        } else {
            countDown(in);
        }
    }

    private void drawWait() {
        times = BiteTimer.roll(lure, biteMultiplier, rng);
        countdown = times.waitTicks();
        ticksLeft = CYCLE_SLACK * (times.waitTicks() + times.approachTicks() + times.windowTicks());
    }

    /** Counts the wait or the approach down by this tick's step; at 0, the fish approaches or bites. */
    private void countDown(CastInputs in) {
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

    private CastState finish(CastEnd how) {
        end = Optional.of(how);
        state = CastState.ENDED;
        return state;
    }
}
