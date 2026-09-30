package dev.hylens.plugin.command;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hyblockui.api.Texts;
import java.util.List;

/** HyLens's chat lines to a player, from hylens.lang. */
final class Chat {
    private Chat() {}

    /** Sends the translation of {@code key} to {@code player}; a param written "%key" is translated too. */
    static void tell(PlayerRef player, String key, String... params) {
        player.sendMessage(Texts.translated(key, List.of(params)));
    }
}
