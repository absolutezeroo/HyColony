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
 * Housing, each a switch reading On or Off (MC BoolSetting.render) whose click asks the core for the other value.
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
        for (Toggle t : Toggle.values()) {
            String button = root + " #Switch" + t.ordinal();
            String state = settings.get(t) ? "on" : "off";
            ui.set(button + ".Text", Message.translation("hycolony.ui.townhall.setting." + state));
            // MC BoolSetting.trigger: the window turns the shown value over and sends the result.
            ColonyPage.bind(events, button, settings.get(t) ? "settingOff" : "settingOn", t.ordinal());
        }
    }

    /** MC TriggerSettingMessage: the core checks MANAGE_HUTS, sets the value, then shows the town hall again. */
    @Override
    public Outcome handle(ColonyPage.Act act) {
        boolean on = "settingOn".equals(act.action());
        if ((on || "settingOff".equals(act.action())) && act.index() >= 0 && act.index() < Toggle.values().length) {
            manager.administration().setSetting(player, colonyId, Toggle.values()[act.index()], on);
        }
        return Outcome.NONE;
    }
}
