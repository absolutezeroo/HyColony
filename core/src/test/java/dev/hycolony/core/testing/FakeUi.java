package dev.hycolony.core.testing;

import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.colony.ui.UiPort;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class FakeUi implements UiPort {
    public final Map<UUID, Object> shown = new LinkedHashMap<>();

    @Override public void showFoundColony(UUID player, FoundColonyView view) { shown.put(player, view); }
    @Override public void showTownHall(UUID player, TownHallView view) { shown.put(player, view); }
    @Override public void close(UUID player) { shown.remove(player); }
}
