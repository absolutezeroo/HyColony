package dev.hycolony.plugin.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.List;

/**
 * The vanilla tab row (TriggerVolumeInspectorPage.buildTabs): one {@code TabButton.ui} per tab in {@code #TabButtons},
 * the open one disabled, and only the open tab's content group visible. A click sends action "tab" with its index.
 */
public final class TabBar {
    /** A tab: its content group selector and its full label key. */
    public interface Tab {
        String group();

        String labelKey();
    }

    private TabBar() {}

    /** Groups of tabs absent from {@code tabs} keep the {@code Visible: false} of their {@code .ui}. */
    public static void render(UICommandBuilder ui, UIEventBuilder events, List<? extends Tab> tabs, Tab open) {
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            String button = "#TabButtons[" + i + "]";
            ui.append("#TabButtons", "Pages/HyColony/TabButton.ui");
            ui.set(button + ".Text", Message.translation(t.labelKey()));
            ui.set(button + ".Disabled", t.equals(open));
            ColonyPage.bind(events, button, "tab", i);
            ui.set(t.group() + ".Visible", t.equals(open));
        }
    }
}
