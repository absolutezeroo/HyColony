package dev.hylens.plugin.watch;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hylens.core.hud.HudLine;
import java.util.List;
import javax.annotation.Nonnull;

/** The panel of what a watched citizen thinks, one template per line kind (spec 2026-09-30, § 6.2). */
final class WatchHud extends CustomUIHud {
    /** Its key among the player's custom HUDs; not "Spectating", which the spectator mode shows. */
    static final String KEY = "HyLensWatch";

    private static final String PANEL = "Hud/HyLens/WatchHud.ui";

    WatchHud(PlayerRef player) {
        super(player, KEY);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder ui) {
        ui.append(PANEL);
    }

    /** Replaces the panel's lines with {@code lines}: each its label, and a field its value beside it. */
    void show(List<HudLine> lines) {
        UICommandBuilder ui = new UICommandBuilder();
        ui.clear("#Lines");
        for (int i = 0; i < lines.size(); i++) {
            HudLine line = lines.get(i);
            String row = "#Lines[" + i + "]";
            ui.append("#Lines", template(line.kind()));
            ui.set(row + " #Label.TextSpans", ApiMessages.of(line.label()));
            line.value().ifPresent(v -> ui.set(row + " #Value.TextSpans", ApiMessages.of(v)));
        }
        update(false, ui);
    }

    /** The .ui appended for a line of {@code kind}. */
    private static String template(HudLine.Kind kind) {
        return switch (kind) {
            case TITLE -> "Hud/HyLens/WatchHudTitle.ui";
            case SECTION -> "Hud/HyLens/WatchHudSection.ui";
            case FIELD -> "Hud/HyLens/WatchHudField.ui";
            case ALERT -> "Hud/HyLens/WatchHudAlert.ui";
        };
    }
}
