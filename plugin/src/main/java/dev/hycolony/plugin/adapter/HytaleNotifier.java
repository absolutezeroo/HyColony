package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.Notifier;
import java.util.UUID;

public final class HytaleNotifier implements Notifier {
    /**
     * Core Msg -> Hytale Message; params become p0, p1, ... (see .lang files). A "%key" param is a nested translation:
     * on a label, set such a message on .TextSpans, not .Text.
     */
    public static Message toMessage(Msg msg) {
        Message m = Message.translation(msg.key());
        for (int i = 0; i < msg.params().size(); i++) {
            String p = msg.params().get(i);
            m = p.startsWith("%") ? m.param("p" + i, Message.translation(p.substring(1))) : m.param("p" + i, p);
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
