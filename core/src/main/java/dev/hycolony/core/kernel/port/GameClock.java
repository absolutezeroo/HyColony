package dev.hycolony.core.kernel.port;

/** Core time. {@code currentTick} advances 20 times per second while the world runs. */
public interface GameClock {
    long currentTick();

    boolean isDaytime();
}
