package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.kernel.port.GameClock;

/** Core tick counter (advanced by ColonyTickSystem) + Hytale day/night. */
public final class HytaleGameClock implements GameClock {
    /** Daytime window in game hours. Hytale day = 60% of 24h; verify at dawn/dusk in game (docs/TESTING.md). */
    static final int DAY_START_HOUR = 6, NIGHT_START_HOUR = 20;

    private final World world;
    private long tick;

    public HytaleGameClock(World world) {
        this.world = world;
    }

    public void advance() {
        tick++;
    }

    @Override
    public long currentTick() {
        return tick;
    }

    @Override
    public boolean isDaytime() {
        WorldTimeResource time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        int hour = time.getGameDateTime().getHour();
        return hour >= DAY_START_HOUR && hour < NIGHT_START_HOUR;
    }
}
