package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.plugin.ui.ColonyPage;

/** One tab of the town hall's book (MC AbstractWindowTownHall page): fills its page appended at a root selector. */
interface TownHallTab {
    /** What the page does after a tab's event. */
    enum Outcome {
        /** Nothing: the event went to the core, which shows the window again if it must. */
        NONE,
        /** The tab's page state changed: draw the whole window again. */
        REDRAW,
        /** Update the tab in place ({@link #refresh}), keeping what a field holds. */
        REFRESH
    }

    /** Fills the tab's page appended at selector {@code root} and binds its buttons. */
    void render(UICommandBuilder ui, UIEventBuilder events, String root);

    /** The in-place update after {@link Outcome#REFRESH}; nothing by default. */
    default void refresh(UICommandBuilder ui, UIEventBuilder events, String root) {}

    /** Runs {@code act} if it is one of this tab's events. */
    default Outcome handle(ColonyPage.Act act) {
        return Outcome.NONE;
    }
}
