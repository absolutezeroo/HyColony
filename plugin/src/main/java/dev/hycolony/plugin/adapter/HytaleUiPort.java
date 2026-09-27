package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.colony.ui.CitizenView;
import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.NeedsPlayerNotice;
import dev.hycolony.core.colony.ui.RequestsView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.colony.ui.UiPort;
import dev.hycolony.core.colony.ui.WandView;
import dev.hycolony.core.colony.ui.WindowKey;
import dev.hycolony.core.construction.wand.WandActions;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.BuildingPage;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.FoundColonyPage;
import dev.hycolony.plugin.ui.RequestsPage;
import dev.hycolony.plugin.ui.citizen.CitizenInventoryWindows;
import dev.hycolony.plugin.ui.citizen.CitizenPage;
import dev.hycolony.plugin.ui.townhall.TownHallPage;
import dev.hycolony.plugin.ui.wand.WandPage;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** Renders core view models with Hytale custom pages. World thread only. */
public final class HytaleUiPort implements UiPort {
    private final Supplier<ColonyManager> manager;
    private final Supplier<WandActions> wand;
    private final HytaleBlocks blocks;
    private final IdMap ids;
    private final String townHallBlockId;
    private final String townHallItemId;
    private final CitizenInventoryWindows citizenInventories;
    private final HutPickUp pickUp;
    private final LiveWindows live = new LiveWindows();
    /** Players whose page Hytale is closing right now: close() must not close it a second time. */
    private final Set<UUID> closing = new HashSet<>();

    public HytaleUiPort(Supplier<ColonyManager> manager, Supplier<WandActions> wand, HytaleBlocks blocks, IdMap ids) {
        this.manager = manager;
        this.citizenInventories = new CitizenInventoryWindows(manager);
        this.pickUp = new HutPickUp(manager, ids);
        this.wand = wand;
        this.blocks = blocks;
        this.ids = ids;
        this.townHallBlockId = ids.blockId("hut.townhall");
        this.townHallItemId = ids.itemId("hut.townhall");
    }

    private void removeTownHall(BlockPos pos) {
        blocks.removeWithDrop(pos, townHallBlockId, townHallItemId);
    }

    @Override
    public void showFoundColony(UUID player, FoundColonyView view) {
        open(
                player,
                pr -> new FoundColonyPage(pr, view, new FoundColonyPage.Handler() {
                    @Override
                    public boolean confirm(String name) {
                        ColonyManager m = manager.get();
                        Optional<BlockPos> pos = m.foundation().pendingPositionOf(player);
                        boolean created = m.foundation().confirm(player, name).isPresent();
                        if (!created && m.foundation().pendingPositionOf(player).isEmpty()) {
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
                            manager.get().foundation().cancel(player).ifPresent(HytaleUiPort.this::removeTownHall);
                        } finally {
                            closing.remove(player);
                        }
                    }
                }));
    }

    @Override
    public void showTownHall(UUID player, TownHallView view) {
        open(player, townHallPage(view));
    }

    @Override
    public void showBuilding(UUID player, BuildingView view) {
        open(player, buildingPage(player, view));
    }

    @Override
    public boolean isShowing(UUID player, WindowKey window) {
        return live.isShowing(player, window);
    }

    @Override
    public boolean refreshBuilding(UUID player, BuildingView view) {
        return live.refresh(
                player,
                new WindowKey.Hut(view.pos()),
                (pr, previous) -> new BuildingPage(pr, view, manager.get(), () -> pickUp.run(player, view))
                        .keepStateOf(previous));
    }

    @Override
    public boolean refreshTownHall(UUID player, TownHallView view) {
        return live.refresh(player, new WindowKey.TownHall(view.colonyId()), townHallPage(view));
    }

    @Override
    public boolean refreshCitizen(UUID player, CitizenView view) {
        return live.refresh(player, new WindowKey.Citizen(view.colonyId(), view.citizenId()), citizenPage(view));
    }

    private BiFunction<PlayerRef, CustomUIPage, ColonyPage> townHallPage(TownHallView view) {
        return (pr, previous) -> new TownHallPage(pr, view, manager.get()).keepTabOf(previous);
    }

    private BiFunction<PlayerRef, CustomUIPage, ColonyPage> buildingPage(UUID player, BuildingView view) {
        return (pr, previous) ->
                new BuildingPage(pr, view, manager.get(), () -> pickUp.run(player, view)).keepTabOf(previous);
    }

    private BiFunction<PlayerRef, CustomUIPage, ColonyPage> citizenPage(CitizenView view) {
        return (pr, previous) -> new CitizenPage(pr, view, manager.get(), ids).keepTabOf(previous);
    }

    @Override
    public void showRequests(UUID player, RequestsView view) {
        open(player, pr -> new RequestsPage(pr, view, manager.get()));
    }

    @Override
    public void showCitizen(UUID player, CitizenView view) {
        open(player, citizenPage(view));
    }

    @Override
    public void showWand(UUID player, WandView view) {
        open(player, pr -> new WandPage(pr, view, manager.get(), wand.get(), ids));
    }

    @Override
    public void openCitizenInventory(UUID player, int colonyId, int citizenId) {
        citizenInventories.open(player, colonyId, citizenId);
    }

    @Override
    public void notifyNeedsPlayer(UUID player, NeedsPlayerNotice notice) {
        PlayerRef pr = Universe.get().getPlayer(player);
        if (pr != null) {
            pr.sendMessage(RequestsPage.needsPlayer(notice));
        }
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
        open(player, (pr, previous) -> page.apply(pr));
    }

    /** {@code page} also gets the page the player has open now (null if none), to keep its local state. */
    private void open(UUID player, BiFunction<PlayerRef, CustomUIPage, ? extends CustomUIPage> page) {
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PageManager pages = store.getComponent(ref, Player.getComponentType()).getPageManager();
        CustomUIPage current = pages.getCustomPage();
        pages.openCustomPage(ref, store, page.apply(pr, current instanceof ColonyPage c ? c.live() : current));
    }
}
