package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.colony.ui.UiPort;
import dev.hycolony.plugin.ui.FoundColonyPage;
import dev.hycolony.plugin.ui.TownHallPage;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** Renders core view models with Hytale custom pages. World thread only. */
public final class HytaleUiPort implements UiPort {
    private final Supplier<ColonyManager> manager;
    private final HytaleBlocks blocks;
    private final String townHallItemId;

    public HytaleUiPort(Supplier<ColonyManager> manager, HytaleBlocks blocks, String townHallItemId) {
        this.manager = manager;
        this.blocks = blocks;
        this.townHallItemId = townHallItemId;
    }

    @Override
    public void showFoundColony(UUID player, FoundColonyView view) {
        open(player, pr -> new FoundColonyPage(pr, view, new FoundColonyPage.Handler() {
            @Override
            public void confirm(String name) {
                manager.get().confirmFoundation(player, name);
            }

            @Override
            public void cancel() {
                manager.get().cancelFoundation(player).ifPresent(pos -> blocks.removeWithDrop(pos, townHallItemId));
            }
        }));
    }

    @Override
    public void showTownHall(UUID player, TownHallView view) {
        open(player, pr -> new TownHallPage(pr, view, name -> manager.get().rename(player, view.colonyId(), name)));
    }

    @Override
    public void close(UUID player) {
        PlayerRef pr = Universe.get().getPlayer(player);
        if (pr == null) {
            return;
        }
        Ref<EntityStore> ref = pr.getReference();
        Store<EntityStore> store = ref.getStore();
        store.getComponent(ref, Player.getComponentType()).getPageManager().setPage(ref, store, Page.None);
    }

    private void open(UUID player, Function<PlayerRef, CustomUIPage> page) {
        PlayerRef pr = Universe.get().getPlayer(player);
        if (pr == null) {
            return;
        }
        Ref<EntityStore> ref = pr.getReference();
        Store<EntityStore> store = ref.getStore();
        store.getComponent(ref, Player.getComponentType()).getPageManager().openCustomPage(ref, store, page.apply(pr));
    }
}
