package dev.hycolony.core.kernel.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventBusTest {
    record Ping(int n) {}

    record Other() {}

    @Test
    void deliversOnlyMatchingTypeInSubscriptionOrder() {
        EventBus bus = new EventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(Ping.class, p -> seen.add("a" + p.n()));
        bus.subscribe(Ping.class, p -> seen.add("b" + p.n()));
        bus.subscribe(Other.class, o -> seen.add("other"));
        bus.post(new Ping(1));
        assertEquals(List.of("a1", "b1"), seen);
    }

    @Test
    void failingListenerDoesNotStopOthers() {
        EventBus bus = new EventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(Ping.class, p -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(Ping.class, p -> seen.add("ok"));
        bus.post(new Ping(1));
        assertEquals(List.of("ok"), seen);
    }
}
