package dev.hyangler.core.port;

/** The weather at a block. A port: never throws; unknown is an empty id and no rain. */
public interface WeatherProbe {
    /** The weather's asset id at the block. */
    String weather(int x, int y, int z);

    /** Whether rain or snow falls on the block. */
    boolean raining(int x, int y, int z);
}
