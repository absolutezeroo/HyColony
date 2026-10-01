package dev.hycolony.core.testing;

import dev.hycolony.core.app.ui.BuildOptionsView;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.app.ui.FieldView;
import dev.hycolony.core.app.ui.FoundColonyView;
import dev.hycolony.core.app.ui.NeedsPlayerNotice;
import dev.hycolony.core.app.ui.RequestsView;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.app.ui.UiPort;
import dev.hycolony.core.app.ui.WandPacksView;
import dev.hycolony.core.app.ui.WandView;
import dev.hycolony.core.app.ui.WindowKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class FakeUi implements UiPort {
    public final Map<UUID, Object> shown = new LinkedHashMap<>();

    public record Notice(UUID player, NeedsPlayerNotice notice) {}

    public final List<Notice> notices = new ArrayList<>();

    public record OpenedInventory(UUID player, int colonyId, int citizenId) {}

    public final List<OpenedInventory> openedInventories = new ArrayList<>();
    /** Views drawn in place by a live refresh, in order; a refresh never touches {@link #shown}'s keys. */
    public final List<Object> redrawn = new ArrayList<>();
    /** isShowing calls so far. */
    public int showingChecks;
    /** Players whose isShowing throws, as a buggy adapter would. */
    public final Set<UUID> failing = new HashSet<>();
    /** Runs inside close(), like Hytale calling the page's onDismiss before it forgets the page. */
    public Consumer<UUID> onClose = p -> {};

    @Override
    public void showFoundColony(UUID player, FoundColonyView view) {
        shown.put(player, view);
    }

    @Override
    public void showTownHall(UUID player, TownHallView view) {
        shown.put(player, view);
    }

    @Override
    public void showBuilding(UUID player, BuildingView view) {
        shown.put(player, view);
    }

    @Override
    public void showBuildOptions(UUID player, BuildOptionsView view) {
        shown.put(player, view);
    }

    @Override
    public void showRequests(UUID player, RequestsView view) {
        shown.put(player, view);
    }

    @Override
    public void showCitizen(UUID player, CitizenView view) {
        shown.put(player, view);
    }

    @Override
    public void showField(UUID player, FieldView view) {
        shown.put(player, view);
    }

    @Override
    public void showWand(UUID player, WandView view) {
        shown.put(player, view);
    }

    @Override
    public void showWandPacks(UUID player, WandPacksView view) {
        shown.put(player, view);
    }

    @Override
    public void openCitizenInventory(UUID player, int colonyId, int citizenId) {
        openedInventories.add(new OpenedInventory(player, colonyId, citizenId));
    }

    @Override
    public void notifyNeedsPlayer(UUID player, NeedsPlayerNotice notice) {
        notices.add(new Notice(player, notice));
    }

    @Override
    public boolean refreshBuilding(UUID player, BuildingView view) {
        return redraw(
                player,
                view,
                shown.get(player) instanceof BuildingView b && b.pos().equals(view.pos()));
    }

    @Override
    public boolean refreshTownHall(UUID player, TownHallView view) {
        return redraw(player, view, shown.get(player) instanceof TownHallView v && v.colonyId() == view.colonyId());
    }

    @Override
    public boolean refreshCitizen(UUID player, CitizenView view) {
        return redraw(
                player,
                view,
                shown.get(player) instanceof CitizenView v
                        && v.colonyId() == view.colonyId()
                        && v.citizenId() == view.citizenId());
    }

    @Override
    public boolean refreshField(UUID player, FieldView view) {
        return redraw(
                player,
                view,
                shown.get(player) instanceof FieldView v && v.pos().equals(view.pos()));
    }

    @Override
    public boolean refreshRequests(UUID player, RequestsView view) {
        return redraw(player, view, shown.get(player) instanceof RequestsView v && v.colonyId() == view.colonyId());
    }

    @Override
    public boolean isShowing(UUID player, WindowKey window) {
        showingChecks++;
        if (failing.contains(player)) {
            throw new IllegalStateException("failing fake window");
        }
        return window.equals(keyOf(shown.get(player)));
    }

    private static WindowKey keyOf(Object view) {
        if (view instanceof BuildingView b) {
            return new WindowKey.Hut(b.pos());
        }
        if (view instanceof TownHallView t) {
            return new WindowKey.TownHall(t.colonyId());
        }
        if (view instanceof RequestsView r) {
            return new WindowKey.Clipboard(r.colonyId());
        }
        if (view instanceof FieldView f) {
            return new WindowKey.Field(f.pos());
        }
        return view instanceof CitizenView c ? new WindowKey.Citizen(c.colonyId(), c.citizenId()) : null;
    }

    /** Like Hytale: only the window still open is redrawn. */
    private boolean redraw(UUID player, Object view, boolean stillOpen) {
        if (stillOpen) {
            shown.put(player, view);
            redrawn.add(view);
        }
        return stillOpen;
    }

    @Override
    public void close(UUID player) {
        onClose.accept(player);
        shown.remove(player);
    }
}
