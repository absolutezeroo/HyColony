package dev.hylens.plugin.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hycolony.api.ApiText;
import java.util.List;
import javax.annotation.Nonnull;

/** The panel of what a watched citizen thinks, one label per line (spec 2026-09-30, § 6.2). */
final class WatchHud extends CustomUIHud {
    /** Its key among the player's custom HUDs; not "Spectating", which the spectator mode shows. */
    static final String KEY = "HyLensWatch";

    private static final String PANEL = "Hud/HyLens/WatchHud.ui";
    private static final String LINE = "Hud/HyLens/WatchHudLine.ui";

    WatchHud(PlayerRef player) {
        super(player, KEY);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder ui) {
        ui.append(PANEL);
    }

    /** Replaces the panel's lines with {@code lines}. */
    void show(List<ApiText> lines) {
        UICommandBuilder ui = new UICommandBuilder();
        ui.clear("#Lines");
        for (int i = 0; i < lines.size(); i++) {
            ui.append("#Lines", LINE);
            ui.set("#Lines[" + i + "] #Text.TextSpans", ApiMessages.of(lines.get(i)));
        }
        update(false, ui);
    }
}
