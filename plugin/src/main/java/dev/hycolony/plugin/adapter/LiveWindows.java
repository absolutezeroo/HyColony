package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ui.WindowKey;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.RequestsPage;
import dev.hycolony.plugin.ui.citizen.CitizenPage;
import dev.hycolony.plugin.ui.hut.HutWindow;
import dev.hycolony.plugin.ui.townhall.TownHallPage;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.logging.Level;
import javax.annotation.Nullable;

/**
 * The core's live refresh of an open colony window (MC's view sync): finds whether the player still has it open and
 * redraws it in place. Never throws; the first failure is logged, the next ones in FINE. World thread only.
 */
final class LiveWindows {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private boolean warned;

    /** False when the player is gone, in another world, or has closed that window or opened another page. */
    boolean isShowing(UUID player, WindowKey window) {
        try {
            ColonyPage open = openPage(player);
            return open != null && shows(open.live(), window);
        } catch (RuntimeException e) {
            fail(e);
            return false;
        }
    }

    /** Redraws {@code window} in place with {@code page} (given the page it replaces); false if it is not open. */
    boolean refresh(UUID player, WindowKey window, BiFunction<PlayerRef, CustomUIPage, ColonyPage> page) {
        try {
            ColonyPage open = openPage(player);
            if (open == null || !shows(open.live(), window)) {
                return false;
            }
            open.refreshWith(page.apply(Universe.get().getPlayer(player), open.live()));
            return true;
        } catch (RuntimeException e) {
            fail(e);
            return false;
        }
    }

    @Nullable
    private static ColonyPage openPage(UUID player) {
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        if (!store.getExternalData().getWorld().isInThread()) {
            return null; // the player moved to another world: its store asserts its own thread
        }
        Player p = store.getComponent(ref, Player.getComponentType());
        return p != null && p.getPageManager().getCustomPage() instanceof ColonyPage c ? c : null;
    }

    private static boolean shows(ColonyPage page, WindowKey window) {
        return switch (window) {
            case WindowKey.Hut h -> page instanceof HutWindow w && w.hutPos().equals(h.pos());
            case WindowKey.TownHall t ->
                page instanceof TownHallPage p && p.view().colonyId() == t.colonyId();
            case WindowKey.Citizen c ->
                page instanceof CitizenPage p
                        && p.view().colonyId() == c.colonyId()
                        && p.view().citizenId() == c.citizenId();
            case WindowKey.Clipboard c ->
                page instanceof RequestsPage p && p.view().colonyId() == c.colonyId();
        };
    }

    private void fail(RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony live window refresh failed");
        warned = true;
    }
}
