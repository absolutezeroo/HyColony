package dev.hylens.plugin.watch;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.gamemode.GameModeTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyblockui.api.Texts;
import dev.hylens.core.watch.WatchLoss;
import dev.hylens.core.watch.Watches;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A watch whose citizen HyColony no longer knows ({@link WatchLoss}, its colony deleted): stopped, and the operator
 * told, rather than kept silently on an empty panel.
 */
final class LostWatch {
    private LostWatch() {}

    /**
     * On the world's thread, from a simple TickingSystem where the mode may change: stops {@code player}'s watch, takes
     * them out of the spectator mode, and tells them.
     */
    static void drop(PlayerRef player, Store<EntityStore> store, Watches watches) {
        watches.stop(player.getUuid());
        @Nullable Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid() && Spectating.isSpectating(ref, store)) {
            GameModeTypes.exit(ref, store);
        }
        player.sendMessage(Texts.translated("hylens.watch.lost", List.of()));
    }
}
