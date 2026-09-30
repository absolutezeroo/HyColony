package dev.hylens.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hylens.plugin.send.MapSend;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/** /hylens menu: opens the HyLens menu (spec 2026-09-30, § 6.4), and closes those open as HyLens stops. */
public final class MenuCommand extends AbstractPlayerCommand {
    private final LensParts parts;
    private final CitizenWatch watch;
    private final MapSend map;

    MenuCommand(LensParts parts, CitizenWatch watch, MapSend map) {
        super("menu", "Open the HyLens menu: colonies, citizens, actions, layers, clock, checks (operators)");
        this.parts = parts;
        this.watch = watch;
        this.map = map;
    }

    /**
     * On {@code world}'s thread: closes every HyLens menu open there, as HyLens stops; a click on one left open would
     * run HyLens's code once its classes are gone.
     */
    public static void closeOpen(World world) {
        for (PlayerRef player : world.getPlayerRefs()) {
            @Nullable Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            @Nullable Player component = store.getComponent(ref, Player.getComponentType());
            if (component != null && component.getPageManager().getCustomPage() instanceof MenuPage) {
                component.getPageManager().setPage(ref, store, Page.None);
            }
        }
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
