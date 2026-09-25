package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.Notifier;
import java.util.UUID;

public final class HytaleNotifier implements Notifier {
    /** Core Msg -> Hytale Message; params become p0, p1, ... (see .lang files). */
    public static Message toMessage(Msg msg) {
        Message m = Message.translation(msg.key());
        for (int i = 0; i < msg.params().size(); i++) {
            m = m.param("p" + i, msg.params().get(i));
        }
        return m;
    }

    @Override
    public void send(UUID player, Msg message) {
        PlayerRef pr = Universe.get().getPlayer(player);
        if (pr != null) {
            pr.sendMessage(toMessage(message));
        }
    }
}
