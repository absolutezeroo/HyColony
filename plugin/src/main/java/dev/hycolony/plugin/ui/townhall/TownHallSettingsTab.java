package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.colony.ColonySettings.Toggle;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.UUID;

/**
 * The town hall's Settings tab (MC WindowSettings): New citizens spawning, Auto Worker Hiring and Auto Citizen
 * Housing, each a switch reading On or Off (MC BoolSetting.render) that the core turns over.
 */
final class TownHallSettingsTab implements TownHallTab {
    private final ColonyManager manager;
    private final UUID player;
    private final int colonyId;
    private final TownHallView.Settings settings;

    TownHallSettingsTab(ColonyManager manager, UUID player, int colonyId, TownHallView.Settings settings) {
        this.manager = manager;
        this.player = player;
        this.colonyId = colonyId;
        this.settings = settings;
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        boolean[] values = {settings.moveIn(), settings.autoHiring(), settings.autoHousing()};
        for (Toggle t : Toggle.values()) {
            String button = root + " #Switch" + t.ordinal();
            String state = values[t.ordinal()] ? "on" : "off";
            ui.set(button + ".Text", Message.translation("hycolony.ui.townhall.setting." + state));
            ColonyPage.bind(events, button, "toggle", t.ordinal());
        }
    }

    /** MC TriggerSettingMessage: the core checks MANAGE_HUTS, then shows the town hall again. */
    @Override
    public Outcome handle(ColonyPage.Act act) {
        if ("toggle".equals(act.action()) && act.index() >= 0 && act.index() < Toggle.values().length) {
            manager.administration().toggle(player, colonyId, Toggle.values()[act.index()]);
        }
        return Outcome.NONE;
    }
}
