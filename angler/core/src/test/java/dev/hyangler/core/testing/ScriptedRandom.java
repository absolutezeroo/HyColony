package dev.hyangler.core.testing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.random.RandomGenerator;

/** A random source that answers bounded nextInt calls from a script, in order; anything else fails the test. */
public final class ScriptedRandom implements RandomGenerator {
    private final Deque<Integer> ints = new ArrayDeque<>();

    public ScriptedRandom(int... values) {
        for (int v : values) {
            ints.add(v);
        }
    }

    @Override
    public int nextInt(int origin, int bound) {
        Integer v = ints.poll();
        if (v == null || v < origin || v >= bound) {
            throw new AssertionError("scripted int " + v + " outside [" + origin + ", " + bound + ")");
        }
        return v;
    }

    @Override
    public long nextLong() {
        throw new AssertionError("unscripted nextLong");
    }
}
