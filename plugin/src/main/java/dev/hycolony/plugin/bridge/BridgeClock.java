package dev.hycolony.plugin.bridge;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.app.api.ColonyClockState;
import dev.hycolony.plugin.api.ColonyClock;

/** A world's {@link ColonyClock}, over the core's {@link ColonyClockState}: each call checks the world's thread. */
final class BridgeClock implements ColonyClock {
    private final World world;
    private final ColonyClockState state;

    BridgeClock(World world, ColonyClockState state) {
        this.world = world;
        this.state = state;
    }

    /** Pauses for {@code owner}, and lifts the pause when it stops (from its unloading thread: only a note). */
    @Override
    public boolean pause(PluginBase owner) {
        ApiThreads.check(world);
        String key = OwnerBinding.key(owner);
        boolean held = state.owner().filter(key::equals).isPresent();
        if (!state.pause(key)) {
            return false;
        }
        if (!held) { // tied once per pause, not per renewal; each pause after a resume leaves one small shutdown task
            OwnerBinding.bind(owner, () -> state.release(key));
        }
        return true;
    }

    @Override
    public boolean step(int ticks) {
        ApiThreads.check(world);
        return state.step(ticks);
    }

    @Override
    public void resume() {
        ApiThreads.check(world);
        state.resume();
    }

    @Override
    public boolean paused() {
        ApiThreads.check(world);
        return state.paused();
    }
}
