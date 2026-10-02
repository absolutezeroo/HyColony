package dev.hylens.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hylens.core.menu.MenuView;
import dev.hylens.core.watch.Watches;
import java.util.Optional;

/**
 * The HyLens menu's watch clicks on the chosen citizen (spec 2026-10-02, § 3.3): watch it, free or follow the camera,
 * stop. Watching or moving the camera closes the menu so the operator sees the world; stopping keeps it open.
 */
final class MenuWatchClicks {
    private final CitizenWatch watch;
    private final Watches watches;

    MenuWatchClicks(CitizenWatch watch, Watches watches) {
        this.watch = watch;
        this.watches = watches;
    }

    /**
     * On the world's thread: runs the watch click {@code action} of {@code player}, at {@code ref}, on {@code chosen};
     * true when the menu should close, the camera following or free. Refusals are told in the chat. Freeing or
     * following a citizen the operator no longer watches (a book drawn before the watch changed) does nothing, so the
     * camera never follows another citizen than the HUD's.
     */
    boolean run(
            String action,
            PlayerRef player,
            MenuView.CitizenRow chosen,
            Ref<EntityStore> ref,
            Store<EntityStore> store) {
        return switch (action) {
            case "free" -> chosen.watched() && watch.free(player, store, ref);
            case "follow" -> chosen.watched() && watch.follow(player, store, ref, chosen.ref(), chosen.name());
            case "unwatch" -> {
                watch.stop(player, store, ref);
                yield false;
            }
            default -> {
                watch.start(player, store, ref, chosen.ref(), chosen.name());
                // start tells no result: the watch held afterwards says whether it started.
                yield watches.watched(player.getUuid()).equals(Optional.of(chosen.ref()));
            }
        };
    }
}
