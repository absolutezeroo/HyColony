package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.hyblockui.api.Texts;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.Notifier;
import java.util.UUID;

public final class HytaleNotifier implements Notifier {
    /** Core Msg -> Hytale Message, through HyBlockUI's {@link Texts#translated}. */
    public static Message toMessage(Msg msg) {
        return Texts.translated(msg.key(), msg.params());
    }

    @Override
    public void send(UUID player, Msg message) {
        PlayerRef pr = Universe.get().getPlayer(player);
        if (pr != null) {
            pr.sendMessage(toMessage(message));
        }
    }
}
