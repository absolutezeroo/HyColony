package dev.hylens.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** /hylens unwatch: stops the watch and its history, and leaves the spectator mode ({@link CitizenWatch#stop}). */
final class UnwatchCommand extends AbstractPlayerCommand {
    private final CitizenWatch watch;

    UnwatchCommand(CitizenWatch watch) {
        super("unwatch", "Stop following a citizen (operators)");
        this.watch = watch;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {
        watch.stop(player, store, ref);
    }
}
