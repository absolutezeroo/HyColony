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
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.FoundColonyPage;
import dev.hycolony.plugin.ui.TownHallPage;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** Renders core view models with Hytale custom pages. World thread only. */
public final class HytaleUiPort implements UiPort {
    private final Supplier<ColonyManager> manager;
    private final HytaleBlocks blocks;
    private final String townHallBlockId;
    private final String townHallItemId;
    /** Players whose page Hytale is closing right now: close() must not close it a second time. */
    private final Set<UUID> closing = new HashSet<>();

    public HytaleUiPort(Supplier<ColonyManager> manager, HytaleBlocks blocks, String townHallBlockId, String townHallItemId) {
        this.manager = manager;
        this.blocks = blocks;
        this.townHallBlockId = townHallBlockId;
        this.townHallItemId = townHallItemId;
    }

    private void removeTownHall(BlockPos pos) {
        blocks.removeWithDrop(pos, townHallBlockId, townHallItemId);
    }

    @Override
    public void showFoundColony(UUID player, FoundColonyView view) {
        open(player, pr -> new FoundColonyPage(pr, view, new FoundColonyPage.Handler() {
            @Override
            public boolean confirm(String name) {
                ColonyManager m = manager.get();
                Optional<BlockPos> pos = m.pendingPositionOf(player);
                boolean created = m.confirmFoundation(player, name).isPresent();
                if (!created && m.pendingPositionOf(player).isEmpty()) {
                    pos.ifPresent(HytaleUiPort.this::removeTownHall); // spot became invalid: foundation dropped
                }
                return created;
            }

            @Override
            public void cancel(boolean windowClosing) {
                if (windowClosing) {
                    closing.add(player);
                }
                try {
                    manager.get().cancelFoundation(player).ifPresent(HytaleUiPort.this::removeTownHall);
                } finally {
                    closing.remove(player);
                }
            }
        }));
    }

    @Override
    public void showTownHall(UUID player, TownHallView view) {
        open(player, pr -> new TownHallPage(pr, view, name -> manager.get().rename(player, view.colonyId(), name)));
    }

    @Override
    public void close(UUID player) {
        if (closing.contains(player)) {
            return;
        }
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        if (ref == null || !ref.isValid()) {
            return; // disconnected or not in a world
        }
        Store<EntityStore> store = ref.getStore();
        store.getComponent(ref, Player.getComponentType()).getPageManager().setPage(ref, store, Page.None);
    }

    private void open(UUID player, Function<PlayerRef, CustomUIPage> page) {
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        store.getComponent(ref, Player.getComponentType()).getPageManager().openCustomPage(ref, store, page.apply(pr));
    }
}
