package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.ItemPickerPage;
import java.util.Optional;

/**
 * One module tab of a hut window (MC module window): its own {@code .ui}, appended into the window's
 * {@code #ModuleTabs}, then filled under that appended root.
 */
public interface HutTab {
    /** The tab's {@code .ui} document. */
    String document();

    /** The tab button's full label key. */
    String labelKey();

    /** Fills the tab appended at selector {@code root} and binds its buttons. */
    void render(UICommandBuilder ui, UIEventBuilder events, String root);

    /** Runs {@code act} if it is one of this tab's buttons; the core re-shows the window. */
    default void handle(ColonyPage.Act act) {}

    /**
     * True when {@code act} changed what this tab shows without going through the core (which re-shows the window
     * itself), so the page must redraw.
     */
    default boolean redraws(ColonyPage.Act act) {
        return false;
    }

    /** The item list {@code act} opens in place of the window (MC WindowSelectRes); empty for other buttons. */
    default Optional<ItemPickerPage.Picker> picker(ColonyPage.Act act) {
        return Optional.empty();
    }
}
