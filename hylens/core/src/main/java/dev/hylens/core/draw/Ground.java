package dev.hylens.core.draw;

import java.util.OptionalInt;

/** The ground a walk's line is laid on; the plugin reads it in the world. A port of HyLens's core. */
@FunctionalInterface
public interface Ground {
    /**
     * The feet height of a body standing in column {@code x z}, the nearest to {@code nearY} within a few blocks; empty
     * when unknown, as in an unloaded chunk or over a void.
     */
    OptionalInt standY(int x, int z, int nearY);
}
