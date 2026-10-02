package dev.hylens.plugin.command.book;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hylens.core.menu.MenuView;
import dev.hylens.plugin.watch.ApiMessages;
import java.util.List;
import java.util.Optional;

/**
 * Draws the HyLens menu's Citizens tab (spec 2026-10-02, § 3.2-3.3): the chosen colony's citizens, the chosen one and
 * its actions, "send here". While the operator watches the chosen citizen, Watch gives way to Free camera (or Follow
 * again, when the camera is free) and Stop watching.
 */
final class CitizensTab {
    static final String PAGE = "Pages/HyLens/Book/Citizens.ui";
    private static final String ROW = "Pages/HyLens/Book/CitizenRow.ui";

    private CitizensTab() {}

    /** Fills the tab appended just before with {@code v} and {@code watch}; binds every button. */
    static void render(UICommandBuilder ui, MenuBinds binds, MenuView v, MenuRender.Watch watch) {
        List<MenuView.CitizenRow> citizens = v.citizens();
        for (int i = 0; i < citizens.size(); i++) {
            row(ui, binds, i, citizens.get(i));
        }
        Optional<MenuView.CitizenRow> chosen =
                citizens.stream().filter(MenuView.CitizenRow::chosen).findFirst();
        ui.set(
                "#Chosen.TextSpans",
                chosen.map(c -> Message.translation("hylens.menu.chosen").param("p0", c.name()))
                        .orElse(Message.translation("hylens.menu.noneChosen")));
        chosen.ifPresent(c -> ui.set("#ChosenState.TextSpans", state(c)));
        boolean watched = chosen.map(MenuView.CitizenRow::watched).orElse(false);
        ui.set("#WatchButton.Visible", !watched);
        ui.set("#FreeButton.Visible", watched);
        ui.set("#UnwatchButton.Visible", watched);
        ui.set(
                "#FreeButton.Text",
                Message.translation(watch.cameraFree() ? "hylens.menu.follow" : "hylens.menu.freeCamera"));
        binds.on("#WatchButton", "watch", "");
        binds.on("#FreeButton", watch.cameraFree() ? "follow" : "free", "");
        binds.on("#UnwatchButton", "unwatch", "");
        binds.on("#LeisureButton", "leisure", "");
        binds.on("#TeleportButton", "teleport", "");
        binds.on("#RespawnButton", "respawn", "");
        ui.set("#SendX.Value", watch.x());
        ui.set("#SendY.Value", watch.y());
        ui.set("#SendZ.Value", watch.z());
        binds.on("#SendButton", "send", "");
        binds.on("#SendMapButton", "sendMap", "");
    }

    /** {@code c}'s job, AI state and job step. */
    private static Message state(MenuView.CitizenRow c) {
        return Message.translation("hylens.menu.rowState")
                .param("p0", ApiMessages.of(c.job()))
                .param("p1", c.ai())
                .param("p2", c.step());
    }

    /** Appends row {@code i}, citizen {@code c}: its name, state, alerts, and its choose button. */
    private static void row(UICommandBuilder ui, MenuBinds binds, int i, MenuView.CitizenRow c) {
        String row = "#Citizens[" + i + "]";
        ui.append("#Citizens", ROW);
        String name = c.watched() ? "hylens.menu.rowWatched" : c.chosen() ? "hylens.menu.rowChosen" : "hylens.menu.row";
        ui.set(row + " #Name.TextSpans", Message.translation(name).param("p0", c.name()));
        ui.set(row + " #State.TextSpans", state(c));
        if (c.alerts() > 0) {
            ui.set(
                    row + " #Alerts.TextSpans",
                    Message.translation("hylens.menu.rowAlerts").param("p0", String.valueOf(c.alerts())));
        }
        binds.on(row + " #Choose", "citizen", String.valueOf(c.ref().citizenId()));
    }
}
