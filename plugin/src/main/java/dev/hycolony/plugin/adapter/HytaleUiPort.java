package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildOptionsView;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.app.ui.FieldView;
import dev.hycolony.core.app.ui.FoundColonyView;
import dev.hycolony.core.app.ui.NeedsPlayerNotice;
import dev.hycolony.core.app.ui.RequestsView;
import dev.hycolony.core.app.ui.SuggestBuildToolView;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.app.ui.UiPort;
import dev.hycolony.core.app.ui.WandPacksView;
import dev.hycolony.core.app.ui.WandView;
import dev.hycolony.core.app.ui.WindowKey;
import dev.hycolony.core.app.wand.HutHandPlacement;
import dev.hycolony.core.app.wand.WandActions;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.BuildOptionsPage;
import dev.hycolony.plugin.ui.BuildingPage;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.FoundColonyHandler;
import dev.hycolony.plugin.ui.FoundColonyPage;
import dev.hycolony.plugin.ui.RequestsPage;
import dev.hycolony.plugin.ui.citizen.CitizenInventoryWindows;
import dev.hycolony.plugin.ui.citizen.CitizenPage;
import dev.hycolony.plugin.ui.field.FieldPage;
import dev.hycolony.plugin.ui.hut.HutWindow;
import dev.hycolony.plugin.ui.request.RequestTexts;
import dev.hycolony.plugin.ui.townhall.TownHallPage;
import dev.hycolony.plugin.ui.wand.SuggestBuildToolPage;
import dev.hycolony.plugin.ui.wand.WandPacksPage;
import dev.hycolony.plugin.ui.wand.WandPage;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Renders core view models with Hytale custom pages. World thread only: a player in another world, or without a
 * Player component, gets no page; a page that fails to open is logged (WARNING once, then FINE), never thrown (§ 4).
 */
public final class HytaleUiPort implements UiPort {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Supplier<ColonyManager> manager;
    private final Supplier<WandActions> wand;
    private final HytaleBlocks blocks;
    private final IdMap ids;
    private final CitizenInventoryWindows citizenInventories;
    private final HutPickUp pickUp;
    private final LiveWindows live = new LiveWindows();
    /** Players whose page Hytale is closing right now: close() must not close it a second time. */
    private final Set<UUID> closing = new HashSet<>();

    private boolean warned;

    /** {@code bodies}: a citizen body's loaded entity, for the camera of the citizen's inventory page. */
    public HytaleUiPort(
            Supplier<ColonyManager> manager,
            Supplier<WandActions> wand,
            HytaleBlocks blocks,
            IdMap ids,
            Function<BodyId, Optional<Ref<EntityStore>>> bodies) {
        this.manager = manager;
        this.citizenInventories = new CitizenInventoryWindows(manager, bodies);
        this.pickUp = new HutPickUp(manager, ids);
        this.wand = wand;
        this.blocks = blocks;
        this.ids = ids;
    }

    @Override
    public void showFoundColony(UUID player, FoundColonyView view) {
        open(
                player,
                pr -> new FoundColonyPage(pr, view, new FoundColonyHandler(manager, player, blocks, ids, closing)));
    }

    @Override
    public void showTownHall(UUID player, TownHallView view) {
        open(player, townHallPage(view));
    }

    @Override
    public void showBuilding(UUID player, BuildingView view) {
        open(player, buildingPage(view));
    }

    @Override
    public void showBuildOptions(UUID player, BuildOptionsView view) {
        open(
                player,
                (pr, previous) -> new BuildOptionsPage(
                                pr, view, manager.get(), () -> pickUp.run(player, view.building()))
                        .keepStateOf(previous));
    }

    @Override
    public boolean isShowing(UUID player, WindowKey window) {
        return live.isShowing(player, window);
    }

    @Override
    public boolean refreshBuilding(UUID player, BuildingView view) {
        return live.refresh(player, new WindowKey.Hut(view.pos()), buildingPage(view));
    }

    @Override
    public boolean refreshTownHall(UUID player, TownHallView view) {
        return live.refresh(player, new WindowKey.TownHall(view.colonyId()), townHallPage(view));
    }

    @Override
    public boolean refreshCitizen(UUID player, CitizenView view) {
        return live.refresh(player, new WindowKey.Citizen(view.colonyId(), view.citizenId()), citizenPage(view));
    }

    @Override
    public boolean refreshRequests(UUID player, RequestsView view) {
        return live.refresh(
                player,
                new WindowKey.Clipboard(view.colonyId()),
                (pr, previous) -> new RequestsPage(pr, view, manager.get(), ids));
    }

    private BiFunction<PlayerRef, CustomUIPage, ColonyPage> townHallPage(TownHallView view) {
        return (pr, previous) -> new TownHallPage(pr, view, manager.get()).keepTabOf(previous);
    }

    /**
     * The window of the player for this hut drawing {@code view}: the one open (main window or a window it opened, MC
     * keeps a sub-window open after its action), else the hut's main window.
     */
    private BiFunction<PlayerRef, CustomUIPage, ColonyPage> buildingPage(BuildingView view) {
        return (pr, previous) -> previous instanceof HutWindow w && w.hutPos().equals(view.pos())
                ? w.with(pr, view)
                : new BuildingPage(pr, view, manager.get());
    }

    private BiFunction<PlayerRef, CustomUIPage, ColonyPage> citizenPage(CitizenView view) {
        return (pr, previous) -> new CitizenPage(pr, view, manager.get(), ids).keepTabOf(previous);
    }

    @Override
    public void showRequests(UUID player, RequestsView view) {
        open(player, pr -> new RequestsPage(pr, view, manager.get(), ids));
    }

    @Override
    public void showCitizen(UUID player, CitizenView view) {
        open(player, citizenPage(view));
    }

    /** Opens the field block's window (MC WindowField). */
    @Override
    public void showField(UUID player, FieldView view) {
        open(player, pr -> new FieldPage(pr, view, manager.get(), ids));
    }

    @Override
    public boolean refreshField(UUID player, FieldView view) {
        return live.refresh(
                player, new WindowKey.Field(view.pos()), (pr, previous) -> new FieldPage(pr, view, manager.get(), ids));
    }

    @Override
    public void showWand(UUID player, WandView view) {
        open(player, pr -> new WandPage(pr, view, manager.get(), wand.get()));
    }

    @Override
    public void showWandPacks(UUID player, WandPacksView view) {
        open(player, pr -> new WandPacksPage(pr, view, manager.get(), wand.get()));
    }

    @Override
    public void showSuggestBuildTool(UUID player, SuggestBuildToolView view) {
        HutHandPlacement hand = new HutHandPlacement(manager.get(), wand.get(), new ItemKey(ids.itemId("build_tool")));
        open(player, pr -> new SuggestBuildToolPage(pr, view, hand));
    }

    @Override
    public void openCitizenInventory(UUID player, int colonyId, int citizenId) {
        citizenInventories.open(player, colonyId, citizenId);
    }

    @Override
    public void notifyNeedsPlayer(UUID player, NeedsPlayerNotice notice) {
        PlayerRef pr = Universe.get().getPlayer(player);
        if (pr != null) {
            pr.sendMessage(RequestTexts.needsPlayer(notice));
        }
    }

    @Override
    public void close(UUID player) {
        if (closing.contains(player)) {
            return;
        }
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        Player p = playerHere(ref);
        if (ref != null && p != null) {
            guarded("close", () -> p.getPageManager().setPage(ref, ref.getStore(), Page.None));
        }
    }

    private void open(UUID player, Function<PlayerRef, CustomUIPage> page) {
        open(player, (pr, previous) -> page.apply(pr));
    }

    /** {@code page} also gets the page the player has open now (null if none), to keep its local state. */
    private void open(UUID player, BiFunction<PlayerRef, CustomUIPage, ? extends CustomUIPage> page) {
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        Player p = playerHere(ref);
        if (pr == null || ref == null || p == null) {
            return;
        }
        PageManager pages = p.getPageManager();
        CustomUIPage current = ColonyPage.openOrClosed(pages.getCustomPage());
        guarded(
                "open",
                () -> pages.openCustomPage(
                        ref, ref.getStore(), page.apply(pr, current instanceof ColonyPage c ? c.live() : current)));
    }

    /**
     * The Player component behind {@code ref}; null when disconnected, not in a world, in another world (its store
     * asserts its own thread, as LiveWindows checks) or without one.
     */
    private static @Nullable Player playerHere(@Nullable Ref<EntityStore> ref) {
        if (ref == null
                || !ref.isValid()
                || !ref.getStore().getExternalData().getWorld().isInThread()) {
            return null;
        }
        return ref.getStore().getComponent(ref, Player.getComponentType());
    }

    private void guarded(String op, Runnable call) {
        try {
            call.run();
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony: window %s failed", op);
            warned = true;
        }
    }
}
