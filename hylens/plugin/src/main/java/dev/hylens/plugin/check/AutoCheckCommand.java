package dev.hylens.plugin.check;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyblockui.api.Texts;
import dev.hylens.core.check.AutoCheck;
import dev.hylens.core.check.NewAlerts;
import dev.hylens.core.menu.Menus;
import java.util.List;
import javax.annotation.Nonnull;

/** /hylens autocheck: turns the automatic check on or off for the operator (spec 2026-09-30, § 6.5). */
public final class AutoCheckCommand extends AbstractPlayerCommand {
    private final Menus menus;
    private final NewAlerts alerts;

    public AutoCheckCommand(Menus menus, NewAlerts alerts) {
        super("autocheck", "Turn the automatic check of the colonies on or off (operators)");
        this.menus = menus;
        this.alerts = alerts;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {
        boolean on = AutoCheck.toggle(menus, alerts, player.getUuid());
        player.sendMessage(Texts.translated(on ? "hylens.check.autoOn" : "hylens.check.autoOff", List.of()));
    }
}
