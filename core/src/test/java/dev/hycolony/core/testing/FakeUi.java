package dev.hycolony.core.testing;

import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.colony.ui.CitizenView;
import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.NeedsPlayerNotice;
import dev.hycolony.core.colony.ui.RequestsView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.colony.ui.UiPort;
import dev.hycolony.core.colony.ui.WorkOrdersView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class FakeUi implements UiPort {
    public final Map<UUID, Object> shown = new LinkedHashMap<>();

    public record Notice(UUID player, NeedsPlayerNotice notice) {}

    public final List<Notice> notices = new ArrayList<>();
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
    public void showRequests(UUID player, RequestsView view) {
        shown.put(player, view);
    }

    @Override
    public void showWorkOrders(UUID player, WorkOrdersView view) {
        shown.put(player, view);
    }

    @Override
    public void showCitizen(UUID player, CitizenView view) {
        shown.put(player, view);
    }

    @Override
    public void notifyNeedsPlayer(UUID player, NeedsPlayerNotice notice) {
        notices.add(new Notice(player, notice));
    }

    @Override
    public void close(UUID player) {
        onClose.accept(player);
        shown.remove(player);
    }
}
