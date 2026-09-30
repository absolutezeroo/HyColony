package dev.hylens.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hylens.plugin.send.MapSend;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/** /hylens menu: opens the HyLens menu (spec 2026-09-30, § 6.4). */
final class MenuCommand extends AbstractPlayerCommand {
    private final LensParts parts;
    private final CitizenWatch watch;
    private final MapSend map;

    MenuCommand(LensParts parts, CitizenWatch watch, MapSend map) {
        super("menu", "Open the HyLens menu: colonies, citizens, actions, layers, clock, checks (operators)");
        this.parts = parts;
        this.watch = watch;
        this.map = map;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {
        @Nullable Player component = store.getComponent(ref, Player.getComponentType());
        if (component != null) {
            component
                    .getPageManager()
                    .openCustomPage(
                            ref,
                            store,
                            new MenuPage(player, parts, watch, new MenuSend(map, parts.watches(), parts.menus())));
        }
    }
}
