package dev.hylens.plugin.command;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.api.ApiText;
import dev.hycolony.plugin.api.ColonyClock;
import dev.hycolony.plugin.api.HyColonyApi;
import dev.hylens.core.menu.Pauses;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The menu's colony clock (spec 2026-09-30, § 6.4): pause the world's colonies for HyLens, run N core ticks, resume.
 * HyColony holds the pause for the plugin; {@link Pauses} remembers which operator asked it, so the colonies resume
 * when they leave. World thread.
 */
public final class MenuClock {
    private final PluginBase owner;
    private final Pauses pauses;

    public MenuClock(PluginBase owner, Pauses pauses) {
        this.owner = owner;
        this.pauses = pauses;
    }

    /** Whether {@code world}'s colonies are paused; false where HyColony does not run. */
    static boolean paused(World world) {
        return clock(world).map(ColonyClock::paused).orElse(false);
    }

    /**
     * Runs {@code action} ("pause", "step" of {@code ticks}, "resume") on {@code world}'s clock for {@code operator};
     * the text of its result, empty for another action.
     */
    Optional<ApiText> run(String action, World world, UUID operator, int ticks) {
        Optional<ColonyClock> clock = clock(world);
        if (clock.isEmpty()) {
            return Optional.of(ApiText.of("hylens.notRunning"));
        }
        ColonyClock c = clock.get();
        return switch (action) {
            case "pause" -> {
                if (!c.pause(owner)) {
                    yield Optional.of(ApiText.of("hylens.clock.busy"));
                }
                pauses.paused(world.getName(), operator);
                yield Optional.of(ApiText.of("hylens.clock.paused"));
            }
            case "step" ->
                Optional.of(
                        c.step(ticks)
                                ? ApiText.of("hylens.clock.queued", String.valueOf(ticks))
                                : ApiText.of("hylens.clock.notPaused"));
            case "resume" -> {
                c.resume();
                pauses.resumed(world.getName());
                yield Optional.of(ApiText.of("hylens.clock.resumed"));
            }
            default -> Optional.empty();
        };
    }

    /**
     * The worlds whose colonies {@code operator} paused through HyLens, as they leave: each to be handed to
     * {@link #resumeFor} on its own thread. Any thread.
     */
    public Set<String> pausedBy(UUID operator) {
        return pauses.pausedBy(operator);
    }

    /**
     * On {@code world}'s thread: resumes its colonies if {@code operator}, leaving, still holds their pause through
     * HyLens. The pause is taken again first, so a pause another plugin holds since is left alone.
     */
    public void resumeFor(World world, UUID operator) {
        if (pauses.forget(world.getName(), operator)) {
            clock(world).filter(c -> c.pause(owner)).ifPresent(ColonyClock::resume);
        }
    }

    private static Optional<ColonyClock> clock(World world) {
        HyColonyApi api;
        try {
            api = HyColonyApi.get();
        } catch (IllegalStateException e) {
            return Optional.empty(); // HyColony stopped: its api holder is empty
        }
        return api.clock(world);
    }
}
