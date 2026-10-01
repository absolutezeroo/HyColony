package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.ColonyPage;

/**
 * A window drawing one hut's view: its main window or a window it opens that shows the same view (hire workers,
 * assign residents, the inventory summary). The core re-shows a hut's view after each action and live: the window the
 * player has open for that hut then draws it again instead of going back to the hut's main window, as MC keeps its
 * sub-window open.
 */
public interface HutWindow {
    /** The hut this window shows. */
    BlockPos hutPos();

    /** The same window drawing {@code view}, keeping this one's local state. */
    ColonyPage with(PlayerRef playerRef, BuildingView view);
}
