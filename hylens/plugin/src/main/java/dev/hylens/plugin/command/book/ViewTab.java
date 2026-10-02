package dev.hylens.plugin.command.book;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hylens.core.draw.Layers;
import dev.hylens.core.menu.MenuView;
import java.util.Locale;

/** Draws the HyLens menu's View tab (spec 2026-10-02, § 3.2): the layers, the automatic check, the colony clock. */
final class ViewTab {
    static final String PAGE = "Pages/HyLens/Book/View.ui";
    private static final String LAYER_BUTTON = "Pages/HyLens/Book/LayerButton.ui";

    private ViewTab() {}

    /** Fills the tab appended just before with {@code v}; binds every button. */
    static void render(UICommandBuilder ui, MenuBinds binds, MenuView v) {
        layers(ui, binds, v.layers());
        ui.set(
                "#AutoCheckButton.Text",
                Message.translation(v.autoCheck() ? "hylens.menu.autoCheck.on" : "hylens.menu.autoCheck.off"));
        binds.on("#AutoCheckButton", "autoCheck", "");
        ui.set(
                "#ClockState.TextSpans",
                Message.translation(v.paused() ? "hylens.menu.clockPaused" : "hylens.menu.clockRunning"));
        ui.set(
                "#StepCount.TextSpans",
                Message.translation("hylens.menu.stepCount").param("p0", String.valueOf(v.step())));
        binds.on("#PauseButton", "pause", "");
        binds.on("#StepLessButton", "stepLess", "");
        binds.on("#StepMoreButton", "stepMore", "");
        binds.on("#StepButton", "step", "");
        binds.on("#ResumeButton", "resume", "");
    }

    /** One button per layer, its text saying whether the layer shows: one complete key per state (CLAUDE.md § 7). */
    private static void layers(UICommandBuilder ui, MenuBinds binds, Layers layers) {
        Layers.Layer[] all = Layers.Layer.values();
        for (int i = 0; i < all.length; i++) {
            Layers.Layer layer = all[i];
            String button = "#Layers[" + i + "]";
            ui.append("#Layers", LAYER_BUTTON);
            String key = "hylens.menu.layer." + layer.name().toLowerCase(Locale.ROOT)
                    + (layers.shows(layer) ? ".on" : ".off");
            ui.set(button + ".Text", Message.translation(key));
            binds.on(button, "layer", layer.name());
        }
    }
}
