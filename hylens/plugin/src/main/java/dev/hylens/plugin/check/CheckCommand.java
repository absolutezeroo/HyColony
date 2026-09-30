package dev.hylens.plugin.check;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyblockui.api.Texts;
import dev.hylens.plugin.HyColonyAccess;
import java.util.List;
import javax.annotation.Nonnull;

/** /hylens check: tells the confirmed violations of the world's colonies in the chat (spec 2026-09-30, § 6.5). */
public final class CheckCommand extends AbstractPlayerCommand {
    public CheckCommand() {
        super("check", "Check the world's colonies for inconsistencies (operators)");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {
        HyColonyAccess.world(world)
                .ifPresentOrElse(
                        w -> ColonyChecks.tell(player, w),
                        () -> player.sendMessage(Texts.translated("hylens.notRunning", List.of())));
    }
}
