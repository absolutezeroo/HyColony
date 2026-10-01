package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.plugin.ui.ColonyPage;

/** One tab of the town hall's book (MC AbstractWindowTownHall page): fills its page appended at a root selector. */
interface TownHallTab {
    /** Fills the tab's page appended at selector {@code root} and binds its buttons. */
    void render(UICommandBuilder ui, UIEventBuilder events, String root);

    /** Runs {@code act} if it is one of this tab's buttons; the core re-shows the window. */
    default void handle(ColonyPage.Act act) {}
}
