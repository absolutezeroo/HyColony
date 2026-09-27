package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.construction.shared.BuilderSettingsModule.Mode;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.Locale;
import java.util.UUID;

/** The builder hut's Settings tab (MC SettingsModuleWindow): the Auto/Manual mode button cycles the mode. */
final class BuilderSettingsTab implements HutTab {
    private final ColonyManager manager;
    private final UUID player;
    private final BlockPos hut;
    private final Mode mode;
    private final boolean canManage;

    BuilderSettingsTab(ColonyManager manager, UUID player, BlockPos hut, Mode mode, boolean canManage) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.mode = mode;
        this.canManage = canManage;
    }

    @Override
    public String document() {
        return "Pages/HyColony/BuilderSettingsTab.ui";
    }

    @Override
    public String labelKey() {
        return "hycolony.ui.building.tab.settings";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        String button = root + " #ModeButton";
        // A button's Text renders no nested message: one full key per mode.
        ui.set(
                button + ".Text",
                Message.translation("hycolony.ui.builder.mode." + mode.name().toLowerCase(Locale.ROOT)));
        if (canManage) {
            ColonyPage.bind(events, button, "mode");
        } else {
            ui.set(button + ".Disabled", true);
        }
    }

    /** The core checks MANAGE_HUTS and shows the window again. */
    @Override
    public void handle(ColonyPage.Act act) {
        if ("mode".equals(act.action())) {
            manager.huts().setBuilderMode(player, hut, mode == Mode.AUTO ? Mode.MANUAL : Mode.AUTO);
        }
    }
}
