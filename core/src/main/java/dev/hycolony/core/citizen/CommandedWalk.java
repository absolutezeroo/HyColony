package dev.hycolony.core.citizen;

import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.colony.BlockApproach;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.ai.AIOneTimeEventTarget;
import dev.hycolony.core.kernel.ai.IState;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * MC CommandCitizenTriggerWalkTo: a citizen sent to a position walks there (MC walkToPos(citizen, pos, 4, true): our
 * {@link BlockApproach#walkToSafePos}, up to 4 blocks from it), for {@link #MAX_WALK_TICKS} at most, through an event
 * transition of its citizen AI that holds the AI meanwhile, then for {@link #HOLD_TICKS} more.
 *
 * <p>Deviations from MC (spec 2026-09-30, § 5, § 9):
 *
 * <ul>
 *   <li>MC pauses the navigation 100 ticks while its AI goes on; here the AI waits {@link #HOLD_TICKS}, and nothing
 *       sends the body elsewhere (a nav still under way may finish its path, as in MC).
 *   <li>One walk per citizen, a new command replacing it; MC keys the walk by the player who sent it, and the replaced
 *       transition clears the new walk's entry, ending both.
 *   <li>Each command walks with a fresh walker ({@code walkers}): our walkers keep state (their target, where they
 *       settled) that MC's stateless walkToPos does not.
 *   <li>A citizen without a job walks the same way, watched by the stuck handler and bounded: MC gives it a bare
 *       moveTo, watched by its navigation's stuck handler, where our moveTo has none. A nav left running once the
 *       walk gave up or ran out of time is for the wander's own time limit (spec § 5, task 6).
 *   <li>The stuck handler teleports only to a checked standable cell beside the target, else gives up, which ends the
 *       walk early; MC searches a safe spot within 10 blocks and never gives up.
 *   <li>The time limit is checked before walking, so the walk ends even if the walker throws; MC walks first. The AI's
 *       decisions wait too (MC's decideAiTask is an event that runs first), and the step runs every tick, where MC's
 *       citizen AI ticks every 5.
 * </ul>
 */
final class CommandedWalk {
    /** MC: {@code 20 * 60 * 3}, the ticks a commanded walk may last. */
    static final int MAX_WALK_TICKS = 20 * 60 * 3;
    /** MC setPauseTicks(100): the ticks its AI still waits once the walk is over. */
    static final int HOLD_TICKS = 100;

    private static final long WALKING = -1;

    private final Supplier<BlockApproach> walkers;
    private final LongSupplier clock;
    private @Nullable BlockPos target;
    private @Nullable BlockApproach approach;
    private long start;
    /** The tick its hold ends once the walk is over, {@link #WALKING} before. */
    private long holdUntil = WALKING;

    /** Walks with a fresh walker from {@code walkers} at each command. */
    CommandedWalk(Supplier<BlockApproach> walkers, LongSupplier clock) {
        this.walkers = walkers;
        this.clock = clock;
    }

    /** The commanded walks of {@code data}'s {@code body}: a fresh block approach at each command, walks reported. */
    static CommandedWalk of(Colony colony, CitizenData data, BodyId body) {
        LongSupplier clock = colony.context().clock()::currentTick;
        return new CommandedWalk(
                () -> new BlockApproach(
                        colony.context().ports(),
                        new BodyWalker(colony.context().bodies(), body, clock, new CitizenWalkReports(colony, data))),
                clock);
    }

    /** Whether a commanded walk is under way, or holding. */
    boolean active() {
        return target != null;
    }

    /** Sends the citizen to {@code to} from now, with a fresh walker, replacing any walk under way. */
    void start(BlockPos to) {
        target = to;
        approach = walkers.get();
        start = clock.getAsLong();
        holdUntil = WALKING;
    }

    /**
     * The citizen AI's event transition: each tick, the walk goes on and the AI stays in {@code state}, doing nothing
     * else; it is removed once the walk and its hold are over (MC AIOneTimeEventTarget.shouldRemove).
     */
    <S extends IState> AIOneTimeEventTarget<S> transition(Supplier<S> state) {
        return new AIOneTimeEventTarget<>(() -> {
            step();
            return state.get();
        }) {
            @Override
            public boolean shouldRemove() {
                return !active();
            }
        };
    }

    /** One tick: walks there until arrived, out of time or given up, then waits, then ends. */
    private void step() {
        BlockPos to = target;
        BlockApproach walker = approach;
        if (to == null || walker == null) {
            return;
        }
        long now = clock.getAsLong();
        if (holdUntil == WALKING) {
            if (now - start < MAX_WALK_TICKS && !walker.walkToSafePos(to)) {
                return;
            }
            holdUntil = now + HOLD_TICKS;
        }
        if (now >= holdUntil) {
            target = null;
            approach = null;
        }
    }
}
