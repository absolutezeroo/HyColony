package dev.hyangler.core.port;

/** Hytale's environments. A port: never throws; an unknown block or environment gives an empty string. */
public interface EnvironmentProbe {
    /** The environment's asset id at a block. */
    String environment(int x, int y, int z);

    /** The zone's name of an environment. */
    String zone(String environment);
}
