package dev.hylens.plugin.command.book;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.api.ApiText;
import dev.hylens.core.menu.MenuTab;
import dev.hylens.core.menu.MenuView;
import dev.hylens.plugin.watch.ApiMessages;
import java.util.Optional;

/**
 * Draws the HyLens menu as MC's town hall book (spec 2026-10-02, § 3.1): the open tab's page, its long ribbon, the
 * other tabs' short ribbons and wax seals (MC AbstractWindowTownHall), and the last action's result. Each tab draws
 * its own page ({@link ColoniesTab}, {@link CitizensTab}, {@link ViewTab}). An id in a button's "Index" still names
 * the same row once the list changed, unless HyColony gave a dead citizen's id to a newcomer in between (colony ids
 * are never reused).
 */
public final class MenuRender {
    public static final String PAGE = "Pages/HyLens/Menu.ui";

    /**
     * What the Citizens tab shows beside the view: the "send here" fields as typed, and whether the operator's camera
     * is free of the body watched.
     */
    public record Watch(String x, String y, String z, boolean cameraFree) {}

    private MenuRender() {}

    /**
     * Appends {@code v}'s open tab into the book appended just before, fills it with {@code v} and {@code watch}, shows
     * the last {@code result}, and binds every button and seal.
     */
    public static void render(
            UICommandBuilder ui, UIEventBuilder events, MenuView v, Optional<ApiText> result, Watch watch) {
        MenuBinds binds = new MenuBinds(events, v.tab() == MenuTab.CITIZENS);
        switch (v.tab()) {
            case COLONIES -> {
                ui.append("#Page", ColoniesTab.PAGE);
                ColoniesTab.render(ui, binds, v);
            }
            case CITIZENS -> {
                ui.append("#Page", CitizensTab.PAGE);
                CitizensTab.render(ui, binds, v, watch);
            }
            case VIEW -> {
                ui.append("#Page", ViewTab.PAGE);
                ViewTab.render(ui, binds, v);
            }
        }
        bookmarks(ui, binds, v.tab());
        result.ifPresent(r -> ui.set("#Result.TextSpans", ApiMessages.of(r)));
    }

    /** Shows {@code text} alone, on a book that has nothing else to show. */
    public static void only(UICommandBuilder ui, ApiText text) {
        ui.set("#Result.TextSpans", ApiMessages.of(text));
    }

    /**
     * Shows the open tab's long ribbon, and the others' short ribbon and wax seal, in bookmark slot order (MenuTab's);
     * a seal click opens its tab.
     */
    private static void bookmarks(UICommandBuilder ui, MenuBinds binds, MenuTab open) {
        MenuTab[] tabs = MenuTab.values();
        for (int slot = 0; slot < tabs.length; slot++) {
            MenuTab t = tabs[slot];
            boolean shown = t == open;
            ui.set("#Ribbon" + slot + ".Visible", shown);
            ui.set("#Mark" + slot + ".Visible", !shown);
            ui.set("#Seal" + slot + ".Visible", !shown);
            ui.set("#RibbonText" + slot + ".TextSpans", Message.translation(name(t)));
            ui.set("#Seal" + slot + ".TooltipText", Message.translation(name(t)));
            if (!shown) {
                binds.on("#Seal" + slot, "tab", t.name());
            }
        }
    }

    /** The translation key of {@code t}'s name. */
    private static String name(MenuTab t) {
        return switch (t) {
            case COLONIES -> "hylens.menu.colonies";
            case CITIZENS -> "hylens.menu.citizens";
            case VIEW -> "hylens.menu.view";
        };
    }
}
