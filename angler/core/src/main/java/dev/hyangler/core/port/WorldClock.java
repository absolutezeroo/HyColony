package dev.hyangler.core.port;

/** The world's time. A port: never throws. */
public interface WorldClock {
    /** The hour of day, from 0 to 24. */
    double hour();

    /** The moon phase index, from 0. */
    int moonPhase();
}
