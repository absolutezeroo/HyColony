package dev.hylens.core.testing;

import dev.hylens.core.draw.Ground;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * A ground of columns along x, found as the plugin finds it: only within 2 blocks above and 4 below the height asked
 * near; a column never set is unknown.
 */
public final class FakeGround implements Ground {
    private final Map<Integer, Integer> feetByX = new HashMap<>();

    /** Column {@code x}'s feet height, every z. */
    public FakeGround at(int x, int feet) {
        feetByX.put(x, feet);
        return this;
    }

    /** Columns {@code from} to {@code to}, each at {@code feet}. */
    public FakeGround flat(int from, int to, int feet) {
        for (int x = from; x <= to; x++) {
            feetByX.put(x, feet);
        }
        return this;
    }

    @Override
    public OptionalInt standY(int x, int z, int nearY) {
        Integer feet = feetByX.get(x);
        return feet != null && feet - nearY <= 2 && nearY - feet <= 4 ? OptionalInt.of(feet) : OptionalInt.empty();
    }
}
