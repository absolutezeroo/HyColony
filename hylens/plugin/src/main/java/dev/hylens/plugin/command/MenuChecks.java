package dev.hylens.plugin.command;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.ColonyWorld;
import dev.hylens.core.check.AutoCheck;
import dev.hylens.core.check.NewAlerts;
import dev.hylens.core.menu.Menus;
import dev.hylens.plugin.check.ColonyChecks;
import java.util.Optional;

/** The menu's checks (spec 2026-09-30, § 6.5): check the world's colonies now, or turn the automatic check on/off. */
final class MenuChecks {
    private final Menus menus;
    private final NewAlerts alerts;

    MenuChecks(Menus menus, NewAlerts alerts) {
        this.menus = menus;
        this.alerts = alerts;
    }

    /**
     * Checks {@code colonies} now, the result told in {@code player}'s chat ("checkNow"), or turns their automatic
     * check on or off ("autoCheck"); the text to show under the buttons.
     */
    ApiText run(String action, PlayerRef player, Optional<ColonyWorld> colonies) {
        if ("checkNow".equals(action)) {
            colonies.ifPresent(w -> ColonyChecks.tell(player, w));
            return ApiText.of("hylens.check.inChat");
        }
        boolean on = AutoCheck.toggle(menus, alerts, player.getUuid());
        return ApiText.of(on ? "hylens.check.autoOn" : "hylens.check.autoOff");
    }
}
