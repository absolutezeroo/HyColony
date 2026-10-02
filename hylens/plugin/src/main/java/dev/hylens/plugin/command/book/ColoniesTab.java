package dev.hylens.plugin.command.book;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hylens.core.menu.MenuView;
import java.util.List;

/** Draws the HyLens menu's Colonies tab (spec 2026-10-02, § 3.2): the world's colonies, the chosen one, the check. */
final class ColoniesTab {
    static final String PAGE = "Pages/HyLens/Book/Colonies.ui";
    private static final String ROW = "Pages/HyLens/Book/ColonyRow.ui";

    private ColoniesTab() {}

    /** Fills the tab appended just before with {@code v}; binds every button. */
    static void render(UICommandBuilder ui, MenuBinds binds, MenuView v) {
        List<MenuView.ColonyRow> colonies = v.colonies();
        for (int i = 0; i < colonies.size(); i++) {
            MenuView.ColonyRow c = colonies.get(i);
            String row = "#Colonies[" + i + "]";
            ui.append("#Colonies", ROW);
            String key =
                    (c.chosen() ? "hylens.menu.colonyChosen" : "hylens.menu.colony") + (c.alerts() > 0 ? "Alerts" : "");
            ui.set(
                    row + ".Text",
                    Message.translation(key)
                            .param("p0", c.name())
                            .param("p1", String.valueOf(c.citizens()))
                            .param("p2", String.valueOf(c.alerts())));
            binds.on(row, "colony", String.valueOf(c.ref().colonyId()));
        }
        colonies.stream()
                .filter(MenuView.ColonyRow::chosen)
                .findFirst()
                .ifPresentOrElse(
                        c -> {
                            ui.set("#ColonyName.Text", c.name());
                            ui.set(
                                    "#ColonyDetail.TextSpans",
                                    Message.translation("hylens.menu.colonyDetail")
                                            .param("p0", String.valueOf(c.citizens()))
                                            .param("p1", String.valueOf(c.alerts())));
                        },
                        () -> ui.set("#ColonyDetail.TextSpans", Message.translation("hylens.menu.colonyNone")));
        binds.on("#CheckNowButton", "checkNow", "");
    }
}
