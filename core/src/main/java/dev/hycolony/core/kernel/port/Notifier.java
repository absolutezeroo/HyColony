package dev.hycolony.core.kernel.port;

import java.util.UUID;

public interface Notifier {
    void send(UUID player, Msg message);
}
