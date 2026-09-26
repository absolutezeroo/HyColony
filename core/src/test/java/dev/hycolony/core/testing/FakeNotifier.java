package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.Notifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FakeNotifier implements Notifier {
    public record Sent(UUID player, Msg msg) {}

    public final List<Sent> sent = new ArrayList<>();

    @Override
    public void send(UUID player, Msg message) {
        sent.add(new Sent(player, message));
    }
}
