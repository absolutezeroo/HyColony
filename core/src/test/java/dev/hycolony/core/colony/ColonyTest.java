package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ColonyTest {
    private final TestContexts t = new TestContexts();
    private final UUID owner = UUID.randomUUID();
    private final TerritoryIndex territory = new TerritoryIndex();

    private Colony colony() {
        BlockPos center = new BlockPos(0, 64, 0);
        territory.claimSquare(1, ClaimCell.of(center), 4);
        return new Colony(
                t.context(),
                territory,
                new Colony.Founding(1, "Test", center, Permissions.createDefault(owner, "Alice")));
    }

    private void run(Colony c, int ticks) {
        for (int i = 0; i < ticks; i++) {
            t.clock.tick++;
            c.tick();
        }
    }

    @Test
    void inactiveWhenNobodyOnline() {
        Colony c = colony();
        run(c, 200);
        assertEquals(ColonyState.INACTIVE, c.state());
    }

    @Test
    void activeWhenAPlayerStandsInTerritory() {
        Colony c = colony();
        t.players.online.put(UUID.randomUUID(), new BlockPos(10, 64, 10));
        run(c, 200);
        assertEquals(ColonyState.ACTIVE, c.state());
    }

    @Test
    void unloadedWhenMemberOnlineButFarAndChunkUnloaded() {
        Colony c = colony();
        t.world.loaded = false;
        t.players.online.put(owner, new BlockPos(10_000, 64, 0));
        run(c, 200);
        assertEquals(ColonyState.UNLOADED, c.state());
    }

    @Test
    void activeWhenMemberOnlineAndTownHallChunkLoaded() {
        Colony c = colony();
        t.players.online.put(owner, new BlockPos(10_000, 64, 0));
        run(c, 200);
        assertEquals(ColonyState.ACTIVE, c.state());
    }

    @Test
    void dawnIncrementsDayAndPostsEvents() {
        Colony c = colony();
        List<Object> events = new ArrayList<>();
        t.bus.subscribe(ColonyEvents.DayStarted.class, events::add);
        t.bus.subscribe(ColonyEvents.NightFell.class, events::add);
        t.players.online.put(owner, new BlockPos(0, 64, 0));
        run(c, 200);
        t.clock.daytime = false;
        run(c, 40);
        t.clock.daytime = true;
        run(c, 40);
        assertEquals(1, c.day());
        assertEquals(2, events.size());
        assertTrue(c.isDirty());
    }

    @Test
    void exceptionSuspendsColonyForFiveMinutes() {
        Colony c = colony();
        t.players.online.put(owner, new BlockPos(0, 64, 0));
        c.citizens().failNextTickForTest();
        run(c, 600);
        long suspendedAt = t.clock.tick;
        assertTrue(c.isSuspended());
        t.clock.tick = suspendedAt + Colony.EXCEPTION_SUSPEND_TICKS + 1;
        c.tick();
        assertTrue(!c.isSuspended());
    }

    @Test
    void eventLogIsBoundedTo100() {
        EventLog log = new EventLog();
        for (int i = 0; i < 150; i++) {
            log.add("x", i);
        }
        assertEquals(100, log.entries().size());
        assertEquals(50, log.entries().getFirst().day());
    }
}
