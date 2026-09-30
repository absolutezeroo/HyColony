package dev.hylens.plugin.command;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.api.ApiText;
import dev.hylens.core.draw.Layers;
import dev.hylens.core.menu.MenuView;
import dev.hylens.plugin.watch.ApiMessages;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Draws the HyLens menu from its view (spec 2026-09-30, § 6.4): the colonies, the chosen colony's citizens, the chosen
 * citizen's actions, the last action's result and the layers. Each button sends an "Action", and in "Index" the id
 * of its colony or citizen, or its layer's name: an id still names the same row once the list changed, unless
 * HyColony gave a dead citizen's id to a newcomer in between (colony ids are never reused).
 */
final class MenuRender {
    static final String PAGE = "Pages/HyLens/Menu.ui";
    private static final String COLONY_ROW = "Pages/HyLens/MenuColonyRow.ui";
    private static final String CITIZEN_ROW = "Pages/HyLens/MenuCitizenRow.ui";
    private static final String LAYER_BUTTON = "Pages/HyLens/MenuLayerButton.ui";

    private MenuRender() {}

    /** Fills the page appended just before with {@code v} and the last {@code result}; binds every button. */
    static void render(UICommandBuilder ui, UIEventBuilder events, MenuView v, Optional<ApiText> result) {
        List<MenuView.ColonyRow> colonies = v.colonies();
        for (int i = 0; i < colonies.size(); i++) {
            MenuView.ColonyRow c = colonies.get(i);
            String row = "#Colonies[" + i + "]";
            ui.append("#Colonies", COLONY_ROW);
            String key = c.chosen() ? "hylens.menu.colonyChosen" : "hylens.menu.colony";
            ui.set(
                    row + ".Text",
                    Message.translation(key).param("p0", c.name()).param("p1", String.valueOf(c.citizens())));
            bind(events, row, "colony", String.valueOf(c.ref().colonyId()));
        }
        List<MenuView.CitizenRow> citizens = v.citizens();
        for (int i = 0; i < citizens.size(); i++) {
            citizen(ui, events, i, citizens.get(i));
        }
        ui.set(
                "#Chosen.TextSpans",
                v.citizens().stream()
                        .filter(MenuView.CitizenRow::chosen)
                        .findFirst()
                        .map(c -> Message.translation("hylens.menu.chosen").param("p0", c.name()))
                        .orElse(Message.translation("hylens.menu.noneChosen")));
        bind(events, "#WatchButton", "watch", "");
        bind(events, "#LeisureButton", "leisure", "");
        bind(events, "#TeleportButton", "teleport", "");
        bind(events, "#RespawnButton", "respawn", "");
        result.ifPresent(r -> ui.set("#Result.TextSpans", ApiMessages.of(r)));
        layers(ui, events, v.layers());
    }

    /** Shows {@code text} alone, on a page that has nothing else to show. */
    static void only(UICommandBuilder ui, ApiText text) {
        ui.set("#Result.TextSpans", ApiMessages.of(text));
    }

    private static void citizen(UICommandBuilder ui, UIEventBuilder events, int i, MenuView.CitizenRow c) {
        String row = "#Citizens[" + i + "]";
        ui.append("#Citizens", CITIZEN_ROW);
        String name = c.watched() ? "hylens.menu.rowWatched" : c.chosen() ? "hylens.menu.rowChosen" : "hylens.menu.row";
        ui.set(row + " #Name.TextSpans", Message.translation(name).param("p0", c.name()));
        ui.set(
                row + " #State.TextSpans",
                Message.translation("hylens.menu.rowState")
                        .param("p0", ApiMessages.of(c.job()))
                        .param("p1", c.ai())
                        .param("p2", c.step()));
        if (c.alerts() > 0) {
            ui.set(
                    row + " #Alerts.TextSpans",
                    Message.translation("hylens.menu.rowAlerts").param("p0", String.valueOf(c.alerts())));
        }
        bind(events, row + " #Choose", "citizen", String.valueOf(c.ref().citizenId()));
    }

    /** One button per layer, its text saying whether the layer shows: one complete key per state (CLAUDE.md § 7). */
    private static void layers(UICommandBuilder ui, UIEventBuilder events, Layers layers) {
        Layers.Layer[] all = Layers.Layer.values();
        for (int i = 0; i < all.length; i++) {
            Layers.Layer layer = all[i];
            String button = "#Layers[" + i + "]";
            ui.append("#Layers", LAYER_BUTTON);
            String key = "hylens.menu.layer." + layer.name().toLowerCase(Locale.ROOT)
                    + (layers.shows(layer) ? ".on" : ".off");
            ui.set(button + ".Text", Message.translation(key));
            bind(events, button, "layer", layer.name());
        }
    }

    private static void bind(UIEventBuilder events, String selector, String action, String index) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of("Action", action).append("Index", index),
                false);
    }
}
