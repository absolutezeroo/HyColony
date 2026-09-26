package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.port.GameClock;

public final class FakeClock implements GameClock {
    public long tick;
    public boolean daytime = true;

    @Override
    public long currentTick() {
        return tick;
    }

    @Override
    public boolean isDaytime() {
        return daytime;
    }
}
