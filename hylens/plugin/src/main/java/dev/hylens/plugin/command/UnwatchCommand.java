package dev.hylens.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.gamemode.GameModeTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hylens.core.watch.Watches;
import javax.annotation.Nonnull;

/**
 * /hylens unwatch: stops the watch and its history, and leaves the spectator mode. It leaves it even with no watch
 * held, since the mode is saved with the player and outlives a server restart.
 */
final class UnwatchCommand extends AbstractPlayerCommand {
    private final Watches watches;

    UnwatchCommand(Watches watches) {
        super("unwatch", "Stop following a citizen (operators)");
        this.watches = watches;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {
        boolean watched = watches.stop(player.getUuid()).isPresent();
        boolean left = Spectating.isSpectating(ref, store) && GameModeTypes.exit(ref, store);
        Chat.tell(player, watched || left ? "hylens.watch.stopped" : "hylens.watch.notWatching");
    }
}
