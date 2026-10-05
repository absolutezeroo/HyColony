package dev.hyangler.plugin.cast;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyangler.api.Angler;
import dev.hyangler.api.Pos;
import dev.hyangler.api.event.FishBiting;
import dev.hyangler.core.FishingService;
import dev.hyangler.core.cast.BiteTimer;
import dev.hyangler.core.cast.CastInputs;
import dev.hyangler.core.cast.CastState;
import dev.hyangler.core.port.BlockKind;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Drives a player's cast at the core's 20 ticks a second from the world's 30 (spec § 7.3, ColonyTickSystem's way),
 * feeding it what the bobber meets, and plays what its changes mean: bubbles while a fish approaches; the bite's
 * splash, sound, animation and FishBiting; the end of a cast the core ended (a broken line, a lost bobber).
 */
final class CastTicks {
    /** Bubbles every this many core ticks while a fish approaches. */
    private static final int BUBBLE_EVERY = 5;
    /**
     * How far below the bobber's pinned height its water is looked for, in blocks: Hytale pins it when its box (±0.075)
     * meets the fluid, so its centre may sit just above the surface.
     */
    private static final double WATER_PROBE = 0.2;
    /** How far the approaching fish is from the bobber per tick of its approach left (vanilla: × 0.1), in blocks. */
    private static final double FISH_TICK_BLOCKS = 0.1;
    /** The widest a tick turns the fish's heading (vanilla: random.triangle(0, 9.188) degrees), in radians. */
    private static final double FISH_WANDER = Math.toRadians(9.188);
    /** How far a teasing fish splashes from the bobber (vanilla: nextFloat(25, 60) × 0.1), in blocks. */
    private static final double TEASE_MIN_BLOCKS = 2.5;

    private static final double TEASE_MAX_BLOCKS = 6.0;

    private final FishingService service;
    private final Landing landing;
    private final CastEffects effects;
    private final CastAnimations animations;

    CastTicks(FishingService service, Landing landing, CastEffects effects, CastAnimations animations) {
        this.service = service;
        this.landing = landing;
        this.effects = effects;
        this.animations = animations;
    }

    /**
     * Runs the core ticks due since the last world tick (dt seconds), at most MAX_CATCH_UP: a longer stall drops its
     * backlog. Stops at the cast's end.
     */
    void advance(ActiveCast cast, Bobber bobber, Vector3dc at, float dt, CommandBuffer<EntityStore> buffer) {
        cast.acc += dt;
        int due = 0;
        while (cast.acc >= ActiveCast.CORE_TICK_SECONDS && due < ActiveCast.MAX_CATCH_UP) {
            cast.acc -= ActiveCast.CORE_TICK_SECONDS;
            due++;
            if (!step(cast, bobber, at, buffer)) {
                return;
            }
        }
        if (due == ActiveCast.MAX_CATCH_UP) {
            cast.acc = 0f;
        }
    }

    /** One core tick of the cast; false once it has ended. */
    private boolean step(ActiveCast cast, Bobber bobber, Vector3dc at, CommandBuffer<EntityStore> buffer) {
        int x = (int) Math.floor(at.x());
        // from the pinned surface, not the bobbing position: the bob would lift the probe into the air above
        int y = (int) waterTop(bobber, at) - 1;
        int z = (int) Math.floor(at.z());
        readWeather(cast, x, y, z);
        CastState before = cast.session.state();
        // the 5 × 4 × 5 scan only while a fish comes, as vanilla (CastInputs.openWater)
        boolean openWater =
                (before != CastState.APPROACH && before != CastState.BITING) || cast.world.openWater(x, y, z);
        boolean inWater = bobber.floating && water(cast.world.blocks().kind(x, y, z));
        int waitLeft = cast.session.countdown();
        CastState after = cast.session.tick(new CastInputs(
                inWater, bobber.onGround, distance(cast, at, buffer), cast.raining, cast.skyVisible, openWater));
        if (before == CastState.FLOATING && waitLeft > 0 && inWater && after != CastState.ENDED) {
            // vanilla teases in its timeUntilLured > 0 branch, after counting it down: 0 or less once it is over
            tease(cast, bobber, after == CastState.FLOATING ? cast.session.countdown() : 0, at, buffer);
        }
        react(cast, bobber, before, at, buffer);
        return after != CastState.ENDED;
    }

    /** Reads the rain and sky over the bobber again every WEATHER_EVERY core ticks; counts the cast's core ticks. */
    private static void readWeather(ActiveCast cast, int x, int y, int z) {
        if (cast.coreTicks++ % ActiveCast.WEATHER_EVERY == 0) {
            cast.raining = cast.world.weather().raining(x, y, z);
            cast.skyVisible = cast.world.blocks().skyVisible(x, y, z);
        }
    }

    private static boolean water(BlockKind kind) {
        return kind == BlockKind.WATER_SOURCE || kind == BlockKind.WATER_FLOWING;
    }

    /** Plays the change from before to the session's state now: bubbles, the bite, or the end of the cast. */
    private void react(
            ActiveCast cast, Bobber bobber, CastState before, Vector3dc at, CommandBuffer<EntityStore> buffer) {
        CastState after = cast.session.state();
        bobber.taut = after == CastState.BITING;
        if (after == CastState.APPROACH) {
            approach(cast, bobber, before, at, buffer);
        } else if (after == CastState.BITING && before != CastState.BITING) {
            effects.bite(at, buffer);
            animations.play(bobber, CastAnimations.BITE, buffer);
            service.publish(new FishBiting(new Angler.Player(cast.player), pos(at)));
        } else if (after == CastState.ENDED) {
            landing.finish(cast, cast.session.end().orElseThrow(), Optional.empty(), buffer);
        }
    }

    /**
     * The approaching fish (vanilla FishingHook.catchingFish): a heading drawn when it starts and wandering by
     * triangle(0, 9.188) degrees a tick; its bubbles every BUBBLE_EVERY ticks where it swims, countdown × 0.1 blocks
     * from the bobber along it, at the top of the bobber's block, and only over water.
     */
    private void approach(
            ActiveCast cast, Bobber bobber, CastState before, Vector3dc at, CommandBuffer<EntityStore> buffer) {
        RandomGenerator random = ThreadLocalRandom.current();
        cast.fishHeading = before == CastState.APPROACH
                ? cast.fishHeading + FISH_WANDER * triangle(random)
                : random.nextDouble(0, 2 * Math.PI);
        if (cast.coreTicks % BUBBLE_EVERY != 0) {
            return;
        }
        double away = cast.session.countdown() * FISH_TICK_BLOCKS;
        double fishX = at.x() + Math.sin(cast.fishHeading) * away;
        double fishY = waterTop(bobber, at);
        double fishZ = at.z() + Math.cos(cast.fishHeading) * away;
        BlockKind below = cast.world.blocks().kind((int) Math.floor(fishX), (int) fishY - 1, (int) Math.floor(fishZ));
        if (water(below)) {
            effects.bubbles(new Vector3d(fishX, fishY, fishZ), buffer);
        }
    }

    /**
     * A fish teasing the bait while the wait runs (vanilla catchingFish, timeUntilLured > 0): a small splash 2.5 to 6
     * blocks away in a random direction, over water, more likely as the wait ends (BiteTimer.teaseChance of the wait's
     * ticks left, waitLeft).
     */
    private void tease(ActiveCast cast, Bobber bobber, int waitLeft, Vector3dc at, CommandBuffer<EntityStore> buffer) {
        RandomGenerator random = ThreadLocalRandom.current();
        if (random.nextDouble() >= BiteTimer.teaseChance(waitLeft)) {
            return;
        }
        double heading = random.nextDouble(0, 2 * Math.PI);
        double away = random.nextDouble(TEASE_MIN_BLOCKS, TEASE_MAX_BLOCKS);
        double x = at.x() + Math.sin(heading) * away;
        double y = waterTop(bobber, at);
        double z = at.z() + Math.cos(heading) * away;
        BlockKind below = cast.world.blocks().kind((int) Math.floor(x), (int) y - 1, (int) Math.floor(z));
        if (water(below)) {
            effects.tease(new Vector3d(x, y, z), buffer);
        }
    }

    /** The top of the bobber's water block (vanilla's floor(y) + 1), found below the pinned surface (WATER_PROBE). */
    private static double waterTop(Bobber bobber, Vector3dc at) {
        return Math.floor((bobber.floating ? bobber.surfaceY : at.y()) - WATER_PROBE) + 1;
    }

    /** A draw of the triangular law on (-1, 1), peaked at 0 (vanilla RandomSource.triangle): two draws apart. */
    private static double triangle(RandomGenerator random) {
        double first = random.nextDouble();
        double second = random.nextDouble();
        return first - second;
    }

    /** The line's length: from the angler's feet to the bobber, in blocks; 0 for an angler gone (BobberSystem). */
    private static double distance(ActiveCast cast, Vector3dc at, CommandBuffer<EntityStore> buffer) {
        TransformComponent angler =
                cast.angler.isValid() ? buffer.getComponent(cast.angler, TransformComponent.getComponentType()) : null;
        return angler == null ? 0 : angler.getPosition().distance(at);
    }

    private static Pos pos(Vector3dc at) {
        return new Pos((int) Math.floor(at.x()), (int) Math.floor(at.y()), (int) Math.floor(at.z()));
    }
}
