package dev.hycolony.core.kernel.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class EventBusTest {
    record Ping(int n) {}

    record Other() {}

    private final EventBus bus = new EventBus();
    private final List<String> seen = new ArrayList<>();

    @Test
    void deliversOnlyMatchingTypeInSubscriptionOrder() {
        bus.subscribe(Ping.class, p -> seen.add("a" + p.n()));
        bus.subscribe(Ping.class, p -> seen.add("b" + p.n()));
        bus.subscribe(Other.class, o -> seen.add("other"));
        bus.post(new Ping(1));
        assertEquals(List.of("a1", "b1"), seen);
    }

    @Test
    void failingListenerDoesNotStopOthers() {
        bus.subscribe(Ping.class, p -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(Ping.class, p -> seen.add("ok"));
        bus.post(new Ping(1));
        assertEquals(List.of("ok"), seen);
    }

    @Test
    void listenerOfAnUnloadedAddonDoesNotStopOthers() {
        bus.subscribe(Ping.class, p -> {
            throw new NoClassDefFoundError("dev/addon/Gone");
        });
        bus.subscribe(Ping.class, p -> seen.add("ok"));

        bus.post(new Ping(1));

        assertEquals(List.of("ok"), seen);
    }

    @Test
    void closedRegistrationHearsNothingMore() {
        EventBus.Registration r = bus.subscribe(Ping.class, p -> seen.add("a" + p.n()));
        bus.post(new Ping(1));

        r.close();
        r.close(); // idempotent
        bus.post(new Ping(2));

        assertEquals(List.of("a1"), seen);
    }

    @Test
    void registrationClosesFromAnotherThread() throws InterruptedException {
        EventBus.Registration r = bus.subscribe(Ping.class, p -> seen.add("a" + p.n()));

        Thread unload = new Thread(r::close);
        unload.start();
        unload.join();
        bus.post(new Ping(1));

        assertEquals(List.of(), seen);
    }

    @Test
    void listenerMayCloseItselfWhileHearing() {
        EventBus.Registration[] self = new EventBus.Registration[1];
        self[0] = bus.subscribe(Ping.class, p -> {
            seen.add("once" + p.n());
            self[0].close();
        });
        bus.subscribe(Ping.class, p -> seen.add("b" + p.n()));

        bus.post(new Ping(1));
        bus.post(new Ping(2));

        assertEquals(List.of("once1", "b1", "b2"), seen);
    }

    @Test
    void listenerMaySubscribeWhileHearingFromTheNextEventOn() {
        bus.subscribe(Ping.class, p -> {
            if (p.n() == 1) {
                bus.subscribe(Ping.class, q -> seen.add("late" + q.n()));
            }
        });

        bus.post(new Ping(1));
        bus.post(new Ping(2));

        assertEquals(List.of("late2"), seen);
    }

    @Test
    void failingListenerWarnsOnceThenLogsQuietly() {
        Logger log = Logger.getLogger(EventBus.class.getName());
        List<Level> levels = new ArrayList<>();
        Handler spy = new Handler() {
            @Override
            public void publish(LogRecord r) {
                levels.add(r.getLevel());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        Level before = log.getLevel();
        log.setLevel(Level.ALL);
        log.addHandler(spy);
        try {
            bus.subscribe(Ping.class, p -> {
                throw new IllegalStateException("boom");
            });
            bus.post(new Ping(1));
            bus.post(new Ping(2));
        } finally {
            log.removeHandler(spy);
            log.setLevel(before);
        }

        assertEquals(List.of(Level.WARNING, Level.FINE), levels);
    }

    @Test
    void closedListenerIsDroppedOnceNobodyHearsItsType() {
        EventBus.Registration r = bus.subscribe(Ping.class, p -> {});

        r.close();

        assertFalse(bus.hasListeners(Ping.class));
        assertEquals(0, bus.registrations(Ping.class), "nothing is kept of an unloaded addon");
    }

    @Test
    void listenerSubscribedWhileClosedOnesAreDroppedIsKept() {
        EventBus.Registration unloaded = bus.subscribe(Ping.class, p -> {});
        bus.subscribe(Ping.class, p -> {
            if (p.n() == 1) {
                bus.subscribe(Ping.class, q -> seen.add("late" + q.n()));
            }
        });
        unloaded.close(); // after the second subscribed: the next post meets it closed and drops it

        bus.post(new Ping(1));
        bus.post(new Ping(2));

        assertEquals(List.of("late2"), seen);
    }

    @Test
    void hasListenersOnlyWhileOneIsOpen() {
        assertFalse(bus.hasListeners(Ping.class));

        EventBus.Registration r = bus.subscribe(Ping.class, p -> {});
        assertTrue(bus.hasListeners(Ping.class));
        assertFalse(bus.hasListeners(Other.class));

        r.close();
        assertFalse(bus.hasListeners(Ping.class));
    }
}
