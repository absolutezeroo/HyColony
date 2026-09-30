package dev.hycolony.plugin.api;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import dev.hycolony.api.Experimental;

/**
 * One world's colony clock, for a debugging tool: the colonies' AI paused, stepped a few core ticks at a time, resumed.
 * A pause stops the citizens where they stand, not the autosave, is never saved, and ends when its owner stops. World
 * thread.
 *
 * @since 1.0
 */
@Experimental
public interface ColonyClock {
    /** Pauses the world's colonies for {@code owner}; false while another plugin holds the pause. */
    boolean pause(PluginBase owner);

    /**
     * While paused, runs {@code ticks} more core ticks at the pace of time, 10 pending at most; false while the
     * colonies run.
     */
    boolean step(int ticks);

    /** Resumes the world's colonies, whoever paused them. */
    void resume();

    /** Whether the world's colonies are paused. */
    boolean paused();
}
